package dev.upscaler.rt.lod;

import dev.upscaler.UpscalerConfig;
import dev.upscaler.UpscalerMod;
import dev.upscaler.rt.RtContext;
import dev.upscaler.rt.accel.RtAccel;
import dev.upscaler.rt.accel.RtBuffer;
import dev.upscaler.rt.accel.RtBufferPool;
import dev.upscaler.rt.terrain.RtWorkerPool;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * GPU residency for far-field LOD proxies (docs/LOD_PLAN.md M2): keeps the selector's chosen nodes
 * meshed (worker), uploaded, and BLAS-built (one batched async GPU build at a time, like RtTerrain),
 * and publishes a per-node table + TLAS instances that {@code RtComposite} merges into the per-frame
 * TLAS. Each instance carries {@code LOD_BIT | slot} in its custom index; the slot indexes a flat BDA
 * table of per-node Prim-array addresses that {@code world.rchit}'s LOD branch reads. Proxies are one
 * opaque geometry per node — the driver never runs any-hit on them.
 *
 * <p>A node renders only while it is in the CURRENT selected set (immediate exclusion — no stale
 * overlap with fine terrain or with a parent/child node); its GPU resources outlive deselection by
 * {@code lodRetireGraceTicks} so re-selection at a band boundary is free instead of a rebuild.
 * Replacement on content-version change keeps the old node visible until the new BLAS lands (no edit
 * flicker). Rebase discipline matches near terrain: node-local vertices, {@code origin − rebase}
 * instance translation recomputed against the terrain rebase each time it moves.
 *
 * <p>Render-thread confined except the mesh jobs; first failure latches LOD rendering off.
 */
public final class RtLodTerrain {
    public static final RtLodTerrain INSTANCE = new RtLodTerrain();

    /** Custom-index flag routing hits to the LOD table (bit 21; entities/particles use 23/22). */
    public static final int LOD_BIT = 0x200000;
    private static final int KEEP_FRAMES = 4; // frames-in-flight horizon, matches RtTerrain/RtComposite
    private static final int TABLE_ENTRY_BYTES = 8; // u64 primAddr per slot
    private static final int TABLE_INITIAL_SLOTS = 1024;
    private static final long NO_TOKEN = 0L;
    private static final int RETIRE_SWEEP_INTERVAL_FRAMES = 32;

    private boolean failed;

    private final RtBufferPool pool = new RtBufferPool();
    private final Long2ObjectOpenHashMap<NodeGeom>[] resident;
    private final LongOpenHashSet[] selectedNow; // mirror of the selector's current sets, per level
    private final Long2LongOpenHashMap[] inFlight; // (level, key) -> mesh-job token
    private final ConcurrentLinkedQueue<MeshResult> completed = new ConcurrentLinkedQueue<>();
    private final ArrayList<PreparedNode> prepared = new ArrayList<>();
    private Pending pending;
    private final ArrayList<Deferred> deferred = new ArrayList<>();

    private RtBuffer table;
    private int tableCapacity;
    private int nextSlot;
    private final IntArrayList freeSlots = new IntArrayList();
    private final ArrayList<NodeGeom> slots = new ArrayList<>();

    private final ArrayList<RtAccel.Instance> cachedInstances = new ArrayList<>();
    private boolean instancesDirty = true;
    private int cachedRbx = Integer.MIN_VALUE;
    private int cachedRby;
    private int cachedRbz;

    private long seenSelectionGen = -1;
    private long seenMutation = -1;
    private int seenEpoch = Integer.MIN_VALUE;
    private long frameCount;
    private long meshTokens;

    // Debug counters
    private long meshJobs;
    private long meshDrops;
    private long published;
    private long retired;
    private long residentTris;

    @SuppressWarnings("unchecked")
    private RtLodTerrain() {
        resident = new Long2ObjectOpenHashMap[RtLodWorld.MAX_LEVELS];
        selectedNow = new LongOpenHashSet[RtLodWorld.MAX_LEVELS];
        inFlight = new Long2LongOpenHashMap[RtLodWorld.MAX_LEVELS];
        for (int i = 0; i < RtLodWorld.MAX_LEVELS; i++) {
            resident[i] = new Long2ObjectOpenHashMap<>();
            selectedNow[i] = new LongOpenHashSet();
            inFlight[i] = new Long2LongOpenHashMap();
        }
    }

    /** Per-render-frame residency pass, driven from RtComposite next to RtTerrain.frame. Never throws. */
    public static void frame(RtContext ctx) {
        if (INSTANCE.failed) {
            return;
        }
        try {
            INSTANCE.frameInner(ctx);
        } catch (Throwable t) {
            INSTANCE.failed = true;
            UpscalerMod.LOGGER.error("RT LOD: proxy residency failed; LOD rendering disabled for this session", t);
        }
    }

    /** Device address of the LOD node table ({@code u64 primAddr} per slot), 0 when nothing published. */
    public long tableAddress() {
        return table != null ? table.deviceAddress : 0L;
    }

    /**
     * This frame's LOD proxy instances against the given terrain rebase origin. Cached; rebuilt only
     * when residency/selection changed or the rebase moved. Empty when LOD rendering is inactive.
     */
    public List<RtAccel.Instance> instances(int rbx, int rby, int rbz) {
        if (failed || !RtLodWorld.renderingActive()) {
            return List.of();
        }
        if (instancesDirty || rbx != cachedRbx || rby != cachedRby || rbz != cachedRbz) {
            cachedInstances.clear();
            for (int level = 0; level < RtLodWorld.MAX_LEVELS; level++) {
                for (NodeGeom g : resident[level].values()) {
                    if (g.empty || !selectedNow[level].contains(g.key)) {
                        continue; // deselected nodes are excluded immediately (grace keeps only the GPU data)
                    }
                    int shift = 5 + level;
                    float[] xform = {
                            1, 0, 0, (RtLodWorld.keyX(g.key) << shift) - rbx,
                            0, 1, 0, (RtLodWorld.keyY(g.key) << shift) - rby,
                            0, 0, 1, (RtLodWorld.keyZ(g.key) << shift) - rbz};
                    cachedInstances.add(new RtAccel.Instance(xform, g.blas.deviceAddress, LOD_BIT | g.slot, 0xFF, 0));
                }
            }
            instancesDirty = false;
            cachedRbx = rbx;
            cachedRby = rby;
            cachedRbz = rbz;
        }
        return cachedInstances;
    }

    /** Teardown under an idle device (UpscalerClient.shutdownRt after waitIdle). */
    public static void shutdown(RtContext ctx) {
        INSTANCE.destroyAll(ctx);
    }

    private void frameInner(RtContext ctx) {
        frameCount++;
        RtLodWorld world = RtLodWorld.instance();
        if (!RtLodWorld.renderingActive()) {
            if (hasResidency()) {
                clearResidency(); // runtime toggle off — release via the deferred horizon
            }
            processDeferredFrees();
            return;
        }
        if (world.worldEpoch() != seenEpoch) {
            clearResidency(); // dimension/world change: old-world proxies retire, selection re-mirrors
            seenEpoch = world.worldEpoch();
        }
        processDeferredFrees();

        if (pending != null && ctx.isAsyncDone(pending.op())) {
            finalizePending(ctx, world);
        }

        long deadline = System.nanoTime()
                + (long) (UpscalerConfig.Rt.Lod.BUILD_BUDGET_MS.value() * 1_000_000f);
        drainCompleted(ctx, world, deadline);

        if (world.selectionGeneration() != seenSelectionGen || world.mutation() != seenMutation) {
            seenSelectionGen = world.selectionGeneration();
            seenMutation = world.mutation();
            diffSelection(world);
        }
        if (frameCount % RETIRE_SWEEP_INTERVAL_FRAMES == 0) {
            retireSweep(world);
        }

        if (!prepared.isEmpty() && pending == null) {
            List<RtAccel.PreparedBlas> blasBuilds = new ArrayList<>(prepared.size());
            for (PreparedNode pn : prepared) {
                blasBuilds.add(pn.blas());
            }
            RtContext.AsyncSubmit op = ctx.submitAsync(cmd -> RtAccel.recordBlasBuilds(ctx, cmd, blasBuilds));
            pending = new Pending(op, blasBuilds, new ArrayList<>(prepared));
            prepared.clear();
        }
    }

    /**
     * Mirror the selector's sets and dispatch mesh jobs for selected nodes that are missing, stale
     * (content version moved), or not already in flight. Also refreshes the selected nodes' grace clock.
     */
    private void diffSelection(RtLodWorld world) {
        instancesDirty = true; // membership may have changed even if residency didn't
        long tick = world.currentTick();
        for (int level = 0; level < RtLodWorld.MAX_LEVELS; level++) {
            LongOpenHashSet sel = selectedNow[level];
            sel.clear();
            LongArrayList keys = world.selectedKeys(level);
            for (int i = 0, n = keys.size(); i < n; i++) {
                long key = keys.getLong(i);
                sel.add(key);
                RtLodSection s = world.sectionAt(level, key);
                if (s == null) {
                    continue; // selector fallback nodes always exist; belt-and-suspenders
                }
                NodeGeom g = resident[level].get(key);
                if (g != null) {
                    g.lastSelectedTick = tick;
                    if (g.version == s.contentVersion()) {
                        continue;
                    }
                }
                if (inFlight[level].get(key) != NO_TOKEN) {
                    continue; // outstanding job; completion revalidates the version
                }
                dispatchMesh(world, level, key, s);
            }
        }
    }

    private void dispatchMesh(RtLodWorld world, int level, long key, RtLodSection section) {
        long token = ++meshTokens;
        inFlight[level].put(key, token);
        int[] cells = section.snapshotCells();
        int version = section.contentVersion();
        float cellSize = 1 << level;
        RtLodPalette palette = world.palette();
        MeshJob job = new MeshJob(level, key, token, version);
        meshJobs++;
        RtWorkerPool.INSTANCE.submit(() -> {
            try {
                completed.add(new MeshResult(job, RtLodMeshBuilder.build(palette, cells, cellSize), null));
            } catch (Throwable t) {
                completed.add(new MeshResult(job, null, t));
                throw t;
            }
            return null;
        });
    }

    /** Upload finished meshes and prepare their BLAS, bounded by the budget; stale results drop. */
    private void drainCompleted(RtContext ctx, RtLodWorld world, long deadline) {
        int budget = UpscalerConfig.Rt.Lod.BUILDS_PER_FRAME.value();
        MeshResult r;
        while (budget > 0 && System.nanoTime() < deadline && (r = completed.poll()) != null) {
            MeshJob job = r.job();
            if (inFlight[job.level()].get(job.key()) != job.token()) {
                meshDrops++; // superseded or residency cleared since dispatch
                continue;
            }
            inFlight[job.level()].remove(job.key());
            if (r.failure() != null) {
                throw new IllegalStateException("LOD mesh job failed", r.failure());
            }
            RtLodSection s = world.sectionAt(job.level(), job.key());
            if (s == null || s.contentVersion() != job.version() || !selectedNow[job.level()].contains(job.key())) {
                meshDrops++; // stale content or deselected; the next diff re-dispatches if still wanted
                continue;
            }
            if (r.mesh() == null) {
                publishEmpty(world, job.level(), job.key(), job.version());
                continue;
            }
            prepared.add(uploadNode(ctx, job, r.mesh()));
            budget--;
        }
    }

    private PreparedNode uploadNode(RtContext ctx, MeshJob job, RtLodMeshBuilder.LodMesh mesh) {
        int asInput = org.lwjgl.vulkan.KHRAccelerationStructure.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR;
        int storage = org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
        String label = "LOD node L" + job.level() + " " + RtLodWorld.keyX(job.key()) + ","
                + RtLodWorld.keyY(job.key()) + "," + RtLodWorld.keyZ(job.key());
        int vertCount = mesh.positions().length / 3;
        RtBuffer positions = pool.acquire(ctx, (long) mesh.positions().length * Float.BYTES, asInput, true,
                label + " positions");
        RtBuffer indices = pool.acquire(ctx, (long) mesh.indices().length * Integer.BYTES, asInput, true,
                label + " indices");
        RtBuffer prim = pool.acquire(ctx, (long) mesh.prim().length * Float.BYTES, storage, true,
                label + " prim");
        MemoryUtil.memFloatBuffer(positions.mapped, mesh.positions().length).put(mesh.positions());
        MemoryUtil.memIntBuffer(indices.mapped, mesh.indices().length).put(mesh.indices());
        MemoryUtil.memFloatBuffer(prim.mapped, mesh.prim().length).put(mesh.prim());
        RtAccel.PreparedBlas blas = RtAccel.prepareTrianglesBlasPooled(ctx, pool, positions, vertCount,
                indices, mesh.indices().length, true, label + " BLAS");
        return new PreparedNode(job.level(), job.key(), job.version(), positions, indices, prim, blas,
                mesh.triCount());
    }

    /** An all-hidden node (fully enclosed at this coarseness): resident marker only, nothing on GPU. */
    private void publishEmpty(RtLodWorld world, int level, long key, int version) {
        NodeGeom g = new NodeGeom(level, key);
        g.empty = true;
        g.version = version;
        g.lastSelectedTick = world.currentTick();
        NodeGeom prev = resident[level].put(key, g);
        if (prev != null) {
            retireNode(prev);
        }
        instancesDirty = true;
    }

    /** Swap a completed batched build in: publish nodes still wanted, retire replaced/abandoned ones. */
    private void finalizePending(RtContext ctx, RtLodWorld world) {
        Pending p = pending;
        pending = null;
        ctx.freeAsync(p.op());
        RtAccel.freeBlasScratch(pool, p.blas());
        long freeAt = dev.upscaler.rt.RtComposite.frameCounter() + KEEP_FRAMES;
        for (PreparedNode pn : p.nodes()) {
            NodeGeom g = new NodeGeom(pn.level(), pn.key());
            g.positions = pn.positions();
            g.indices = pn.indices();
            g.prim = pn.prim();
            g.blas = pn.blas().accel;
            g.blasBacking = pn.blas().pooledBacking();
            g.triCount = pn.triCount();
            g.version = pn.version();
            g.lastSelectedTick = world.currentTick();
            if (!selectedNow[pn.level()].contains(pn.key())) {
                // Deselected (or the world cleared) while the batched build was in flight. Never
                // published — retire the fresh, unreferenced geometry at the horizon.
                deferred.add(new Deferred(freeAt, () -> destroyNodeResources(g)));
                continue;
            }
            NodeGeom prev = resident[pn.level()].put(pn.key(), g);
            if (prev != null && !prev.empty && prev.slot >= 0) {
                g.slot = prev.slot; // in-place swap: table slot keeps its index, entry is rewritten
                slots.set(g.slot, g);
                retireBuffersOnly(prev, freeAt);
            } else {
                if (prev != null) {
                    retireNode(prev); // empty marker being replaced by real geometry
                }
                g.slot = allocateSlot();
                slots.set(g.slot, g);
            }
            ensureTableCapacity(ctx, nextSlot, freeAt);
            writeTableEntry(g);
            residentTris += g.triCount;
            published++;
            instancesDirty = true;
        }
    }

    /** Free deselected nodes once their grace expired; selected nodes always survive. */
    private void retireSweep(RtLodWorld world) {
        long tick = world.currentTick();
        long grace = UpscalerConfig.Rt.Lod.RETIRE_GRACE_TICKS.value();
        for (int level = 0; level < RtLodWorld.MAX_LEVELS; level++) {
            ObjectIterator<NodeGeom> it = resident[level].values().iterator();
            while (it.hasNext()) {
                NodeGeom g = it.next();
                if (selectedNow[level].contains(g.key)) {
                    g.lastSelectedTick = tick;
                    continue;
                }
                if (tick - g.lastSelectedTick > grace) {
                    it.remove();
                    retireNode(g);
                }
            }
        }
    }

    private void retireNode(NodeGeom g) {
        if (g.slot >= 0) {
            slots.set(g.slot, null);
            freeSlots.add(g.slot);
            g.slot = -1;
        }
        if (!g.empty) {
            residentTris -= g.triCount;
            retired++;
            long freeAt = dev.upscaler.rt.RtComposite.frameCounter() + KEEP_FRAMES;
            deferred.add(new Deferred(freeAt, () -> destroyNodeResources(g)));
        }
        instancesDirty = true;
    }

    /** Retire a replaced node's GPU resources but leave its (reused) slot alone. */
    private void retireBuffersOnly(NodeGeom prev, long freeAt) {
        residentTris -= prev.triCount;
        retired++;
        deferred.add(new Deferred(freeAt, () -> destroyNodeResources(prev)));
        prev.slot = -1;
    }

    private void destroyNodeResources(NodeGeom g) {
        if (g.blas != null) {
            RtAccel.destroyPooledAccel(pool, g.blas, g.blasBacking);
        }
        if (g.prim != null) {
            pool.release(g.prim);
        }
        if (g.indices != null) {
            pool.release(g.indices);
        }
        if (g.positions != null) {
            pool.release(g.positions);
        }
    }

    private int allocateSlot() {
        int slot = freeSlots.isEmpty() ? nextSlot++ : freeSlots.removeInt(freeSlots.size() - 1);
        if (slot >= LOD_BIT) {
            throw new IllegalStateException("LOD node slots exhausted: " + slot);
        }
        while (slots.size() <= slot) {
            slots.add(null);
        }
        return slot;
    }

    private void ensureTableCapacity(RtContext ctx, int minCapacity, long freeAt) {
        if (table != null && tableCapacity >= minCapacity) {
            return;
        }
        int capacity = Math.max(TABLE_INITIAL_SLOTS, tableCapacity);
        while (capacity < minCapacity) {
            capacity <<= 1;
        }
        int storage = org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
        RtBuffer newTable = pool.acquire(ctx, (long) capacity * TABLE_ENTRY_BYTES, storage, true,
                "LOD node table " + capacity + " slots");
        if (table != null) {
            MemoryUtil.memCopy(table.mapped, newTable.mapped, (long) tableCapacity * TABLE_ENTRY_BYTES);
            RtBuffer old = table;
            deferred.add(new Deferred(freeAt, () -> pool.release(old)));
        }
        table = newTable;
        tableCapacity = capacity;
    }

    private void writeTableEntry(NodeGeom g) {
        MemoryUtil.memPutLong(table.mapped + (long) g.slot * TABLE_ENTRY_BYTES, g.prim.deviceAddress);
    }

    private boolean hasResidency() {
        if (table != null || !prepared.isEmpty() || pending != null) {
            return true;
        }
        for (Long2ObjectOpenHashMap<NodeGeom> m : resident) {
            if (!m.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Retire everything through the deferred horizon (world clear / rendering toggled off). */
    private void clearResidency() {
        long freeAt = dev.upscaler.rt.RtComposite.frameCounter() + KEEP_FRAMES;
        for (int level = 0; level < RtLodWorld.MAX_LEVELS; level++) {
            for (NodeGeom g : resident[level].values()) {
                if (!g.empty) {
                    residentTris -= g.triCount;
                    NodeGeom node = g;
                    deferred.add(new Deferred(freeAt, () -> destroyNodeResources(node)));
                }
            }
            resident[level].clear();
            selectedNow[level].clear();
            inFlight[level].clear(); // orphans in-flight jobs; their completions fail the token check
        }
        for (PreparedNode pn : prepared) {
            RtAccel.freeBlasScratch(pool, List.of(pn.blas()));
            NodeGeom g = new NodeGeom(pn.level(), pn.key());
            g.positions = pn.positions();
            g.indices = pn.indices();
            g.prim = pn.prim();
            g.blas = pn.blas().accel;
            g.blasBacking = pn.blas().pooledBacking();
            deferred.add(new Deferred(freeAt, () -> destroyNodeResources(g)));
        }
        prepared.clear();
        // A pending GPU build keeps its resources until finalizePending, whose selectedNow check (now
        // empty) retires every node it produces.
        if (table != null) {
            RtBuffer old = table;
            deferred.add(new Deferred(freeAt, () -> pool.release(old)));
            table = null;
        }
        tableCapacity = 0;
        nextSlot = 0;
        freeSlots.clear();
        slots.clear();
        cachedInstances.clear();
        instancesDirty = true;
        seenSelectionGen = -1;
        seenMutation = -1;
    }

    private void processDeferredFrees() {
        if (deferred.isEmpty()) {
            return;
        }
        long now = dev.upscaler.rt.RtComposite.frameCounter();
        Iterator<Deferred> it = deferred.iterator();
        while (it.hasNext()) {
            Deferred d = it.next();
            if (d.freeFrame() <= now) {
                d.free().run();
                it.remove();
            }
        }
    }

    private void destroyAll(RtContext ctx) {
        if (pending != null) {
            ctx.freeAsync(pending.op());
            RtAccel.freeBlasScratch(pool, pending.blas());
            for (PreparedNode pn : pending.nodes()) {
                NodeGeom g = new NodeGeom(pn.level(), pn.key());
                g.positions = pn.positions();
                g.indices = pn.indices();
                g.prim = pn.prim();
                g.blas = pn.blas().accel;
                g.blasBacking = pn.blas().pooledBacking();
                destroyNodeResources(g);
            }
            pending = null;
        }
        clearResidency();
        // Device is idle (caller waited): run every deferred free now, then drop the pooled backing.
        for (Deferred d : deferred) {
            d.free().run();
        }
        deferred.clear();
        pool.destroyAll();
        completed.clear();
    }

    void appendDebug(StringBuilder sb) {
        int nodes = 0;
        for (Long2ObjectOpenHashMap<NodeGeom> m : resident) {
            nodes += m.size();
        }
        sb.append(", proxies ").append(nodes)
                .append(" tris ").append(residentTris)
                .append(" published ").append(published)
                .append(" retired ").append(retired)
                .append(" jobs ").append(meshJobs)
                .append(" drops ").append(meshDrops)
                .append(" prepQ ").append(prepared.size())
                .append(pending != null ? " building" : "");
    }

    private record MeshJob(int level, long key, long token, int version) {
    }

    private record MeshResult(MeshJob job, RtLodMeshBuilder.LodMesh mesh, Throwable failure) {
    }

    private record PreparedNode(int level, long key, int version, RtBuffer positions, RtBuffer indices,
                                RtBuffer prim, RtAccel.PreparedBlas blas, int triCount) {
    }

    private record Pending(RtContext.AsyncSubmit op, List<RtAccel.PreparedBlas> blas, List<PreparedNode> nodes) {
    }

    private record Deferred(long freeFrame, Runnable free) {
    }

    /** One resident proxy node: GPU buffers + BLAS + its table slot. {@code empty} = nothing visible. */
    private static final class NodeGeom {
        final int level;
        final long key;
        RtBuffer positions;
        RtBuffer indices;
        RtBuffer prim;
        RtAccel blas;
        RtBuffer blasBacking;
        int slot = -1;
        int triCount;
        int version;
        long lastSelectedTick;
        boolean empty;

        NodeGeom(int level, long key) {
            this.level = level;
            this.key = key;
        }
    }
}
