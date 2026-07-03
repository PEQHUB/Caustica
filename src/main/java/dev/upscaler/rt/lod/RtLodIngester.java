package dev.upscaler.rt.lod;

import dev.upscaler.UpscalerConfig;
import dev.upscaler.rt.terrain.RtWorkerPool;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * LOD ingest for the annulus between the fine {@code RtTerrain} window and the loaded horizon
 * (docs/LOD_PLAN.md M2). Once the fine window shrinks to the near boundary, sections beyond it never
 * get a tessellation job — so the piggyback path can't feed them. This class watches loaded chunk
 * columns in that annulus and dispatches a much lighter snapshot: a {@link PalettedContainer} copy of
 * the section's block states (no 27-section render region, no neighbour gating — voxelization reads
 * only the section's own cells) voxelized on {@link RtWorkerPool}.
 *
 * <p>Ordering rides the same shared token source as the piggyback ({@link RtLodWorld#nextIngestToken}),
 * fetched at dispatch on the main thread, so a section crossing the near boundary between sources can
 * never regress. "Already ingested" is {@code RtLodWorld}'s appliedToken map — which both sources
 * populate — so nothing is voxelized twice when a column crosses from the fine window into the annulus.
 *
 * <p>Main-thread confined except the worker voxelize jobs (bounded by {@code lodIngestMaxInflight}).
 */
final class RtLodIngester {
    private final LongOpenHashSet trackedColumns = new LongOpenHashSet();
    private final LongArrayList pending = new LongArrayList();
    private final LongOpenHashSet pendingSet = new LongOpenHashSet();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final Object dirtyLock = new Object();
    private LongArrayList dirtySections = new LongArrayList();
    private LongArrayList dirtyDrain = new LongArrayList();
    private long dispatched;
    private long dirtied;

    /** Thread-safe block-dirty intake. No ±1 expand: voxelization has no neighbour dependencies. */
    void markBlocksDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        synchronized (dirtyLock) {
            for (int scx = minX >> 4; scx <= maxX >> 4; scx++) {
                for (int scy = minY >> 4; scy <= maxY >> 4; scy++) {
                    for (int scz = minZ >> 4; scz <= maxZ >> 4; scz++) {
                        dirtySections.add(RtLodWorld.key(scx, scy, scz));
                    }
                }
            }
        }
    }

    void clear() {
        trackedColumns.clear();
        pending.clear();
        pendingSet.clear();
        synchronized (dirtyLock) {
            dirtySections.clear();
        }
        // In-flight worker jobs drain into the (epoch-fenced) world queue and are dropped there.
    }

    void tick(RtLodWorld world, ClientLevel level, int pcx, int pcz, int nearRadius, int outerRadius) {
        ClientChunkCache chunks = level.getChunkSource();
        int minSecY = level.getMinY() >> 4;
        int maxSecY = (level.getMinY() + level.getHeight() - 1) >> 4;
        scanColumns(world, chunks, pcx, pcz, nearRadius, outerRadius, minSecY, maxSecY);
        drainDirty(world, chunks, pcx, pcz, nearRadius, outerRadius);
        dispatch(world, chunks, minSecY, maxSecY);
    }

    /**
     * Track loaded columns in the annulus; a newly loaded (or newly-in-annulus) column enqueues its
     * not-yet-applied sections. A column that unloads or leaves the outer radius drops its tracking AND
     * its appliedTokens, so a later reload re-ingests fresh data (server-side edits while unloaded).
     */
    private void scanColumns(RtLodWorld world, ClientChunkCache chunks, int pcx, int pcz,
                             int nearRadius, int outerRadius, int minSecY, int maxSecY) {
        // Sweep stale tracking first (unloaded / out of range / swallowed by the fine window).
        LongIterator it = trackedColumns.iterator();
        while (it.hasNext()) {
            long col = it.nextLong();
            int cx = (int) (col >> 32);
            int cz = (int) col;
            boolean inAnnulus = Math.max(Math.abs(cx - pcx), Math.abs(cz - pcz)) > nearRadius
                    && Math.abs(cx - pcx) <= outerRadius && Math.abs(cz - pcz) <= outerRadius;
            if (inAnnulus && chunks.hasChunk(cx, cz)) {
                continue;
            }
            it.remove();
            // Unloaded or out of range: forget applied tokens so a later reload re-ingests fresh data
            // (server-side edits while unloaded). Columns that crossed INTO the fine window keep theirs —
            // the terrain piggyback owns them now and its dirty path keeps them fresh.
            boolean intoFineWindow = Math.max(Math.abs(cx - pcx), Math.abs(cz - pcz)) <= nearRadius;
            if (!intoFineWindow) {
                for (int scy = minSecY; scy <= maxSecY; scy++) {
                    world.forgetApplied(RtLodWorld.key(cx, scy, cz));
                }
            }
        }
        for (int cx = pcx - outerRadius; cx <= pcx + outerRadius; cx++) {
            for (int cz = pcz - outerRadius; cz <= pcz + outerRadius; cz++) {
                if (Math.max(Math.abs(cx - pcx), Math.abs(cz - pcz)) <= nearRadius) {
                    continue; // fine window: the terrain tessellation piggyback covers it
                }
                long col = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
                if (trackedColumns.contains(col) || !chunks.hasChunk(cx, cz)) {
                    continue;
                }
                trackedColumns.add(col);
                for (int scy = minSecY; scy <= maxSecY; scy++) {
                    long key = RtLodWorld.key(cx, scy, cz);
                    if (!world.hasApplied(key) && pendingSet.add(key)) {
                        pending.add(key);
                    }
                }
            }
        }
    }

    private void drainDirty(RtLodWorld world, ClientChunkCache chunks, int pcx, int pcz,
                            int nearRadius, int outerRadius) {
        LongArrayList drain;
        synchronized (dirtyLock) {
            if (dirtySections.isEmpty()) {
                return;
            }
            drain = dirtySections;
            dirtySections = dirtyDrain;
            dirtyDrain = drain;
            dirtySections.clear();
        }
        for (LongIterator it = drain.iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            int cx = RtLodWorld.keyX(key);
            int cz = RtLodWorld.keyZ(key);
            int cheb = Math.max(Math.abs(cx - pcx), Math.abs(cz - pcz));
            if (cheb <= nearRadius || cheb > outerRadius || !chunks.hasChunk(cx, cz)) {
                continue; // in the fine window (piggyback re-extract covers it) or not ours
            }
            // No appliedToken check: an edit must re-ingest. The shared token keeps ordering correct.
            if (pendingSet.add(key)) {
                pending.add(key);
                dirtied++;
            }
        }
    }

    /** Snapshot + dispatch up to the per-tick/in-flight budget (LIFO — newest-loaded columns first). */
    private void dispatch(RtLodWorld world, ClientChunkCache chunks, int minSecY, int maxSecY) {
        if (pending.isEmpty()) {
            return;
        }
        int budget = Math.min(UpscalerConfig.Rt.Lod.INGEST_PER_TICK.value(),
                UpscalerConfig.Rt.Lod.INGEST_MAX_INFLIGHT.value() - inFlight.get());
        while (budget > 0 && !pending.isEmpty()) {
            long key = pending.removeLong(pending.size() - 1);
            pendingSet.remove(key);
            int scx = RtLodWorld.keyX(key);
            int scy = RtLodWorld.keyY(key);
            int scz = RtLodWorld.keyZ(key);
            if (scy < minSecY || scy > maxSecY) {
                continue;
            }
            ChunkAccess chunk = chunks.getChunk(scx, scz, ChunkStatus.FULL, false);
            if (chunk == null) {
                // Unloaded between scan and dispatch; drop tracking so a reload rescans the column.
                trackedColumns.remove(((long) scx << 32) ^ (scz & 0xFFFFFFFFL));
                continue;
            }
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(scy));
            long token = RtLodWorld.nextIngestToken();
            if (section.hasOnlyAir()) {
                world.enqueueAllAir(scx, scy, scz, token);
            } else {
                PalettedContainer<BlockState> copy = section.getStates().copy();
                inFlight.incrementAndGet();
                RtWorkerPool.INSTANCE.submit(() -> {
                    try {
                        world.voxelizeContainer(copy, scx, scy, scz, token);
                    } finally {
                        inFlight.decrementAndGet();
                    }
                    return null;
                });
            }
            dispatched++;
            budget--;
        }
    }

    void appendDebug(StringBuilder sb) {
        sb.append(", ingester pend ").append(pending.size())
                .append(" inflight ").append(inFlight.get())
                .append(" disp ").append(dispatched)
                .append(" dirty ").append(dirtied);
    }
}
