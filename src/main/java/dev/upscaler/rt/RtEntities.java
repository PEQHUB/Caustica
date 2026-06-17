package dev.upscaler.rt;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.upscaler.UpscalerMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * P5.1b-2: dynamic entities as real ray-traced {@code ModelPart} geometry. Each frame, every model
 * entity is re-posed and captured ({@link RtEntityCollector} + {@link RtEntityCapture}) into a mesh in
 * terrain's vertex layout, uploaded, and given a per-entity BLAS built inline in the composite's frame
 * command buffer; one TLAS instance per entity (identity transform — geometry is captured directly in
 * terrain's rebased space) carries the {@link #ENTITY_BIT} custom-index flag so {@code world.rchit}
 * takes the entity path. A per-frame entity geometry table ({@code {primAddr, idxAddr, uvAddr, disp}})
 * gives the hit shader each entity's per-triangle normals/tint and its per-object motion-vector
 * displacement (P5.1c). Non-model entities (items/arrows — geometry via submitItem/submitBlockModel,
 * which the collector ignores) are skipped.
 *
 * <p>Shading is flat vertex-colour (white → grey-lit) until entity textures land (P5.1b-2b): entities
 * use per-type texture files, not the block atlas, so the captured UVs are stored but not yet sampled.
 *
 * <p>Per-frame cost is real (per-entity capture + buffer uploads + a BLAS build); capped by {@code
 * -Dupscaler.rt.maxEntities}. A reusable mesh/BLAS pool is a deferred perf item.
 */
public final class RtEntities {
    public static final RtEntities INSTANCE = new RtEntities();
    public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("upscaler.rt.entities", "true"));
    /** Custom-index flag bit (bit 23 of the 24-bit instanceCustomIndex) marking an entity instance. */
    public static final int ENTITY_BIT = 0x800000;
    private static final int MAX_ENTITIES = Integer.getInteger("upscaler.rt.maxEntities", 1024);
    // Chunk radius around the player to scan for block entities (chests/signs/…) each frame.
    private static final int BE_VIEW_CHUNKS = Integer.getInteger("upscaler.rt.beViewChunks", 8);
    // Entity geometry table entry: {u64 primAddr, u64 idxAddr, u64 uvAddr, pad8, vec4 disp} = 48 bytes
    // (std430 vec4 forces the struct to 16-align/48-size; disp.xyz = per-object MV world displacement).
    private static final int TABLE_ENTRY_BYTES = 48;
    // Ring of fixed-size geometry tables: each frame fills the next slot so the GPU read of this frame's
    // trace never races a later frame's host write. > frames-in-flight (mirrors RtPipeline RING).
    private static final int TABLE_RING = 6;
    // Frames a per-frame entity resource (mesh buffers + BLAS + scratch) must outlive before it's freed.
    private static final int KEEP_FRAMES = 4;
    // Identity 3x4 row-major: entity geometry is captured directly in rebased space, so no per-instance
    // transform is needed (unlike terrain sections, which carry sectionOrigin − rebase).
    private static final float[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};

    // Reusable capture pipeline (single-threaded on the render thread).
    private final RtEntityCollector collector = new RtEntityCollector();
    private final RtEntityCapture capture = new RtEntityCapture();
    private CameraRenderState cameraState;

    private RtBuffer[] tableRing;
    private int tableSlot;

    // Previous frame's absolute interpolated positions, keyed by entity id (rebuilt each frame → prunes
    // entities that left view); drives the per-object motion-vector displacement.
    private Map<Integer, float[]> prevPositions = new HashMap<>();

    // Per-frame entity GPU resources awaiting a frames-in-flight-safe free.
    private final List<Deferred> deferred = new ArrayList<>();

    // P5.1b-2 step-1 capture-pipeline probe (kept; gated + throttled).
    public static final boolean ENABLED_PROBE = Boolean.parseBoolean(System.getProperty("upscaler.rt.entityProbe", "false"));
    private static final int PROBE_INTERVAL = 120;
    private long probeCounter;

    private RtEntities() {
    }

    /** This frame's entity contribution: the full instance list (terrain + entities), the entity BLAS to
     *  build inline this frame, and the geometry-table device address the hit shader reads. */
    public record FrameEntities(List<RtAccel.Instance> instances, List<RtAccel.PreparedBlas> blas, long geomTableAddr) {
    }

    private record Deferred(long freeFrame, Runnable free) {
    }

    /** Mutable per-frame build state shared by the entity + block-entity capture passes. */
    private final class FrameBuild {
        final List<RtAccel.Instance> base;
        List<RtAccel.Instance> instances;
        List<RtAccel.PreparedBlas> blas;
        List<RtBuffer> buffers;
        long tableBase;
        long geomTableAddr;
        int count;

        FrameBuild(List<RtAccel.Instance> base) {
            this.base = base;
        }

        boolean full() {
            return count >= MAX_ENTITIES;
        }
    }

    /**
     * Capture this frame's model entities + block entities into per-object meshes/BLAS and merge them
     * with the terrain static instances. The caller (RtComposite) records the returned BLAS builds
     * before the TLAS build and pushes the geometry-table address. Returns terrain-only (no BLAS, addr 0)
     * when disabled or nothing captured. Coordinates are captured rebase-relative → identity instance.
     */
    public FrameEntities beginFrame(RtContext ctx, List<RtAccel.Instance> base, int rbx, int rby, int rbz,
                                    double camX, double camY, double camZ, Matrix4f projection, Matrix4f viewRotation) {
        processDeferred();
        if (!ENABLED) {
            return new FrameEntities(base, List.of(), 0L);
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return new FrameEntities(base, List.of(), 0L);
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        setCamera(camX, camY, camZ, projection, viewRotation);

        FrameBuild build = new FrameBuild(base);
        captureEntities(ctx, build, mc, level, partial, rbx, rby, rbz);
        captureBlockEntities(ctx, build, mc, level, partial, rbx, rby, rbz);

        if (build.instances == null) {
            return new FrameEntities(base, List.of(), 0L);
        }
        // Retire this frame's meshes + BLAS once it is no longer in flight (their build + the trace that
        // reads them must complete first).
        long freeAt = RtComposite.frameCounter() + KEEP_FRAMES;
        List<RtAccel.PreparedBlas> blasForFree = build.blas;
        List<RtBuffer> buffersForFree = build.buffers;
        deferred.add(new Deferred(freeAt, () -> {
            RtAccel.freeBlasScratch(blasForFree);
            for (RtAccel.PreparedBlas b : blasForFree) {
                b.accel.destroy();
            }
            for (RtBuffer buf : buffersForFree) {
                buf.destroy();
            }
        }));
        return new FrameEntities(build.instances, build.blas, build.geomTableAddr);
    }

    /** Capture animated entities (mobs, items, falling blocks) with per-object motion-vector displacement. */
    private void captureEntities(RtContext ctx, FrameBuild build, Minecraft mc, ClientLevel level, float partial, int rbx, int rby, int rbz) {
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        Entity cameraEntity = mc.getCameraEntity();
        Map<Integer, float[]> curPositions = new HashMap<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (build.full()) {
                break;
            }
            if (entity == cameraEntity || entity.isInvisible()) {
                continue;
            }
            float ix = (float) Mth.lerp(partial, entity.xo, entity.getX());
            float iy = (float) Mth.lerp(partial, entity.yo, entity.getY());
            float iz = (float) Mth.lerp(partial, entity.zo, entity.getZ());
            capture.reset();
            try {
                EntityRenderState state = dispatcher.extractEntity(entity, partial);
                collector.begin(capture);
                // Capture directly in rebased space so the TLAS instance transform is identity.
                dispatcher.submit(state, cameraState, ix - rbx, iy - rby, iz - rbz, new PoseStack(), collector);
            } catch (Throwable t) {
                continue; // non-fatal: skip an entity whose extract/submit throws
            } finally {
                collector.begin(null);
            }
            if (capture.isEmpty()) {
                continue; // non-model entity (arrow/etc.) — no body geometry captured
            }
            int id = entity.getId();
            float[] prev = prevPositions.get(id);
            float dx = prev == null ? 0f : ix - prev[0];
            float dy = prev == null ? 0f : iy - prev[1];
            float dz = prev == null ? 0f : iz - prev[2];
            curPositions.put(id, new float[]{ix, iy, iz});
            appendCapture(ctx, build, dx, dy, dz);
        }
        prevPositions = curPositions;
    }

    /** Capture block entities (chests, signs, …) — static, so motion-vector displacement is zero. */
    private void captureBlockEntities(RtContext ctx, FrameBuild build, Minecraft mc, ClientLevel level, float partial, int rbx, int rby, int rbz) {
        BlockEntityRenderDispatcher beDispatcher = mc.getBlockEntityRenderDispatcher();
        beDispatcher.prepare(cameraState.pos); // sets the camera for shouldRender / extract
        int pcx = rbx >> 4, pcz = rbz >> 4;
        for (int cx = pcx - BE_VIEW_CHUNKS; cx <= pcx + BE_VIEW_CHUNKS; cx++) {
            for (int cz = pcz - BE_VIEW_CHUNKS; cz <= pcz + BE_VIEW_CHUNKS; cz++) {
                if (build.full()) {
                    return;
                }
                if (!level.getChunkSource().hasChunk(cx, cz) || !(level.getChunk(cx, cz) instanceof LevelChunk chunk)) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (build.full()) {
                        return;
                    }
                    capture.reset();
                    try {
                        BlockEntityRenderState state = beDispatcher.tryExtractRenderState(be, partial, null, false);
                        if (state == null) {
                            continue; // off-screen-only (beacon/end-gateway), distance-culled, or no renderer
                        }
                        BlockPos p = be.getBlockPos();
                        PoseStack pose = new PoseStack();
                        pose.translate(p.getX() - rbx, p.getY() - rby, p.getZ() - rbz);
                        collector.begin(capture);
                        beDispatcher.submit(state, pose, collector, cameraState);
                    } catch (Throwable t) {
                        continue;
                    } finally {
                        collector.begin(null);
                    }
                    if (capture.isEmpty()) {
                        continue;
                    }
                    appendCapture(ctx, build, 0f, 0f, 0f); // static → zero MV displacement
                }
            }
        }
    }

    /** Upload the current {@link #capture} as a per-object mesh + BLAS, add its instance + geom-table entry. */
    private void appendCapture(RtContext ctx, FrameBuild build, float dispX, float dispY, float dispZ) {
        if (build.instances == null) {
            build.instances = new ArrayList<>(build.base);
            build.blas = new ArrayList<>();
            build.buffers = new ArrayList<>();
            ensureResources(ctx);
            tableSlot = (tableSlot + 1) % TABLE_RING;
            build.tableBase = tableRing[tableSlot].mapped;
            build.geomTableAddr = tableRing[tableSlot].deviceAddress;
        }
        int asInput = org.lwjgl.vulkan.KHRAccelerationStructure.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR;
        int storage = org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
        int vertCount = capture.verts.size() / 3;
        int idxCount = capture.idx.size();
        RtBuffer positions = ctx.createBuffer((long) capture.verts.size() * Float.BYTES, asInput, true);
        RtBuffer indices = ctx.createBuffer((long) capture.idx.size() * Integer.BYTES, asInput | storage, true);
        RtBuffer uvs = ctx.createBuffer((long) capture.uvList.size() * Float.BYTES, storage, true);
        RtBuffer prim = ctx.createBuffer((long) capture.prim.size() * Float.BYTES, storage, true);
        MemoryUtil.memFloatBuffer(positions.mapped, capture.verts.size()).put(capture.verts.elements(), 0, capture.verts.size());
        MemoryUtil.memIntBuffer(indices.mapped, capture.idx.size()).put(capture.idx.elements(), 0, capture.idx.size());
        MemoryUtil.memFloatBuffer(uvs.mapped, capture.uvList.size()).put(capture.uvList.elements(), 0, capture.uvList.size());
        MemoryUtil.memFloatBuffer(prim.mapped, capture.prim.size()).put(capture.prim.elements(), 0, capture.prim.size());

        // Non-opaque so world.rahit alpha-tests the texture (cutout). Opaque texels pass to the chit.
        RtAccel.PreparedBlas blas = RtAccel.prepareTrianglesBlas(ctx, positions, vertCount, indices, idxCount, false);

        long entry = build.tableBase + (long) build.count * TABLE_ENTRY_BYTES;
        MemoryUtil.memPutLong(entry, prim.deviceAddress);
        MemoryUtil.memPutLong(entry + 8, indices.deviceAddress);
        MemoryUtil.memPutLong(entry + 16, uvs.deviceAddress);
        MemoryUtil.memPutFloat(entry + 32, dispX);
        MemoryUtil.memPutFloat(entry + 36, dispY);
        MemoryUtil.memPutFloat(entry + 40, dispZ);
        MemoryUtil.memPutFloat(entry + 44, 0f);

        build.instances.add(new RtAccel.Instance(IDENTITY, blas.accel.deviceAddress, ENTITY_BIT | (build.count & 0x7FFFFF)));
        build.blas.add(blas);
        build.buffers.add(positions);
        build.buffers.add(indices);
        build.buffers.add(uvs);
        build.buffers.add(prim);
        build.count++;
    }

    private void setCamera(double camX, double camY, double camZ, Matrix4f projection, Matrix4f viewRotation) {
        if (cameraState == null) {
            cameraState = new CameraRenderState();
        }
        cameraState.pos = new Vec3(camX, camY, camZ);
        cameraState.projectionMatrix.set(projection);
        cameraState.viewRotationMatrix.set(viewRotation);
        cameraState.orientation.setFromUnnormalized(viewRotation);
        cameraState.initialized = true;
    }

    private void ensureResources(RtContext ctx) {
        if (tableRing != null) {
            return;
        }
        int storage = org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
        tableRing = new RtBuffer[TABLE_RING];
        for (int i = 0; i < TABLE_RING; i++) {
            tableRing[i] = ctx.createBuffer((long) MAX_ENTITIES * TABLE_ENTRY_BYTES, storage, true);
        }
    }

    private void processDeferred() {
        if (deferred.isEmpty()) {
            return;
        }
        long now = RtComposite.frameCounter();
        java.util.Iterator<Deferred> it = deferred.iterator();
        while (it.hasNext()) {
            Deferred d = it.next();
            if (d.freeFrame() <= now) {
                d.free().run();
                it.remove();
            }
        }
    }

    /** Free the geometry-table ring + any outstanding per-frame entity resources (teardown; GPU idle). */
    public void shutdown() {
        for (Deferred d : deferred) {
            d.free().run();
        }
        deferred.clear();
        if (tableRing != null) {
            for (RtBuffer b : tableRing) {
                b.destroy();
            }
            tableRing = null;
        }
        prevPositions.clear();
    }

    /**
     * P5.1b-2 step-1 verification probe: extract + submit each entity through the capturing collector
     * and log the captured geometry stats. Pure diagnostic (no GPU). Gated by {@code
     * -Dupscaler.rt.entityProbe}; throttled.
     */
    public void probe(double camX, double camY, double camZ, Matrix4f projection, Matrix4f viewRotation) {
        if (!ENABLED_PROBE || (probeCounter++ % PROBE_INTERVAL) != 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Entity cameraEntity = mc.getCameraEntity();
        setCamera(camX, camY, camZ, projection, viewRotation);

        int total = 0, captured = 0, totalTris = 0, logged = 0, failed = 0;
        StringBuilder sample = new StringBuilder();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == cameraEntity || entity.isInvisible()) {
                continue;
            }
            total++;
            try {
                EntityRenderState state = dispatcher.extractEntity(entity, partial);
                PoseStack pose = new PoseStack();
                capture.reset();
                collector.begin(capture);
                double x = Mth.lerp(partial, entity.xo, entity.getX()) - camX;
                double y = Mth.lerp(partial, entity.yo, entity.getY()) - camY;
                double z = Mth.lerp(partial, entity.zo, entity.getZ()) - camZ;
                dispatcher.submit(state, cameraState, x, y, z, pose, collector);
                if (!capture.isEmpty()) {
                    captured++;
                    totalTris += capture.triangleCount();
                    if (logged < 5) {
                        long texView = RtEntityTextures.INSTANCE.resolveView(collector.firstRenderType());
                        sample.append(String.format("  %s: %d verts, %d tris, tex=0x%x%n",
                                entity.getType(), capture.vertexCount(), capture.triangleCount(), texView));
                        logged++;
                    }
                }
            } catch (Throwable t) {
                failed++;
                if (failed <= 2) {
                    UpscalerMod.LOGGER.warn("RT entity capture probe failed for {}", entity.getType(), t);
                }
            } finally {
                collector.begin(null);
            }
        }
        UpscalerMod.LOGGER.info("RT entity capture probe: {} entities, {} captured, {} tris total, {} failed{}{}",
                total, captured, totalTris, failed, sample.length() > 0 ? "\n" : "", sample);
    }
}
