package dev.upscaler.rt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * P5.1b: dynamic entities as ray-traced bounding-box instances. A single unit-cube BLAS is built once
 * and instanced per entity into the per-frame TLAS ({@link RtComposite}) with a scale+translate
 * transform derived from the entity's interpolated AABB — so there are no per-frame BLAS builds, only
 * TLAS instances (which P5.1a already rebuilds every frame). Each instance carries the {@link
 * #ENTITY_BIT} custom-index flag so {@code world.rchit} flat-shades it as a coarse box instead of
 * reading the terrain section table.
 *
 * <p>This is the first entity milestone: it proves the dynamic-instance plumbing (collection, the
 * entity/terrain hit split) with the simplest geometry source. Real {@code ModelPart} model capture
 * (actual mob shapes + entity textures) is P5.1b-2; per-object motion vectors are P5.1c — until then
 * moving entities ghost under DLSS-RR (they reuse the camera-reprojection MV, which is wrong for
 * objects that move relative to the world).
 *
 * <p>Coordinates share terrain's rebased space: the box transform translates by {@code AABB.min −
 * rebaseOrigin} (rebaseOrigin = {@link RtTerrain}'s player-block rebase), so f32 stays exact near the
 * player. The box is axis-aligned (the hitbox), so it does not rotate with the entity — coarse but
 * unmistakably a dynamic, lit, shadow-casting object in the path-traced scene.
 */
public final class RtEntities {
    public static final RtEntities INSTANCE = new RtEntities();
    public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("upscaler.rt.entities", "true"));
    /** Custom-index flag bit (bit 23 of the 24-bit instanceCustomIndex) marking an entity-box instance. */
    public static final int ENTITY_BIT = 0x800000;
    private static final int MAX_ENTITIES = Integer.getInteger("upscaler.rt.maxEntities", 1024);
    private static final int TABLE_ENTRY_BYTES = 16; // vec4: world displacement xyz (+ pad) per entity
    // Ring of fixed-size displacement tables: each frame fills the next slot, so the GPU read of the
    // frame's trace never races a later frame's host write. > frames-in-flight (mirrors RtPipeline RING).
    private static final int TABLE_RING = 6;

    private RtAccel cubeBlas;
    private RtBuffer cubePositions; // kept only so shutdown() can free them; the BLAS is self-contained post-build
    private RtBuffer cubeIndices;
    private long cubeBlasAddr;

    // P5.1c per-object motion vectors: a per-frame table of each entity's world-space displacement
    // since the previous frame (cur − prev interpolated position), read by world.rchit and subtracted
    // in the raygen MV reprojection so moving entities get correct (non-camera) motion vectors.
    private RtBuffer[] tableRing;
    private int tableSlot;
    private long entityTableAddr;
    // Previous frame's absolute interpolated positions, keyed by entity id; rebuilt each frame (prunes
    // entities that left). New/unseen entities get zero displacement (no ghost-correction artifact).
    private Map<Integer, float[]> prevPositions = new HashMap<>();

    private RtEntities() {
    }

    /** Device address of this frame's entity displacement table (valid whenever entity instances exist). */
    public long entityTableAddress() {
        return entityTableAddr;
    }

    /**
     * Append this frame's entity-box instances to {@code base} (the terrain static instances) and
     * return the combined list, leaving {@code base} untouched (it is owned by {@link RtTerrain}).
     * Returns {@code base} unchanged when disabled, when there is no level, or when no entity qualifies.
     * The shared cube BLAS is built lazily on first use.
     */
    public List<RtAccel.Instance> withEntities(RtContext ctx, List<RtAccel.Instance> base, int rebaseX, int rebaseY, int rebaseZ) {
        if (!ENABLED) {
            return base;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return base;
        }
        Entity cameraEntity = mc.getCameraEntity();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        List<RtAccel.Instance> out = null;
        long tableBase = 0L; // mapped pointer of this frame's displacement table slot (set on first entity)
        Map<Integer, float[]> curPositions = new HashMap<>();
        int idx = 0;
        for (Entity entity : level.entitiesForRendering()) {
            if (idx >= MAX_ENTITIES) {
                break;
            }
            // Skip the camera entity (would box the viewer in first person) and invisible entities.
            if (entity == cameraEntity || entity.isInvisible()) {
                continue;
            }
            AABB box = entity.getBoundingBox();
            float sx = (float) box.getXsize();
            float sy = (float) box.getYsize();
            float sz = (float) box.getZsize();
            if (sx <= 0f || sy <= 0f || sz <= 0f) {
                continue; // degenerate AABB (e.g. markers) — nothing to trace
            }
            // Absolute interpolated position at the rendered sub-tick (for smooth motion + MVs).
            float ix = (float) Mth.lerp(partial, entity.xo, entity.getX());
            float iy = (float) Mth.lerp(partial, entity.yo, entity.getY());
            float iz = (float) Mth.lerp(partial, entity.zo, entity.getZ());
            // Shift the ticked AABB (at the current position) to the interpolated position.
            double shiftX = ix - entity.getX();
            double shiftY = iy - entity.getY();
            double shiftZ = iz - entity.getZ();
            // Row-major 3x4: unit cube [0,1]^3 scaled by the AABB size and translated to its rebased min.
            float tx = (float) (box.minX + shiftX - rebaseX);
            float ty = (float) (box.minY + shiftY - rebaseY);
            float tz = (float) (box.minZ + shiftZ - rebaseZ);
            float[] xform = {sx, 0, 0, tx, 0, sy, 0, ty, 0, 0, sz, tz};
            if (out == null) {
                out = new ArrayList<>(base);
                ensureResources(ctx);
                tableSlot = (tableSlot + 1) % TABLE_RING;
                tableBase = tableRing[tableSlot].mapped;
                entityTableAddr = tableRing[tableSlot].deviceAddress;
            }
            // P5.1c: world-space displacement since last frame (zero for new entities), written into the
            // table at this instance's index so world.rchit/raygen can de-camera the motion vector.
            int id = entity.getId();
            float[] prev = prevPositions.get(id);
            float dispX = prev == null ? 0f : ix - prev[0];
            float dispY = prev == null ? 0f : iy - prev[1];
            float dispZ = prev == null ? 0f : iz - prev[2];
            long entry = tableBase + (long) idx * TABLE_ENTRY_BYTES;
            MemoryUtil.memPutFloat(entry, dispX);
            MemoryUtil.memPutFloat(entry + 4, dispY);
            MemoryUtil.memPutFloat(entry + 8, dispZ);
            MemoryUtil.memPutFloat(entry + 12, 0f);
            curPositions.put(id, new float[]{ix, iy, iz});

            out.add(new RtAccel.Instance(xform, cubeBlasAddr, ENTITY_BIT | (idx & 0x7FFFFF)));
            idx++;
        }
        prevPositions = curPositions; // advance (and prune entities that left view)
        return out == null ? base : out;
    }

    /** Build the shared unit-cube BLAS + the displacement-table ring once (one-shot, on first entity). */
    private void ensureResources(RtContext ctx) {
        if (cubeBlas != null) {
            return;
        }
        int storage = org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
        tableRing = new RtBuffer[TABLE_RING];
        for (int i = 0; i < TABLE_RING; i++) {
            tableRing[i] = ctx.createBuffer((long) MAX_ENTITIES * TABLE_ENTRY_BYTES, storage, true);
        }
        // Unit cube [0,1]^3: 8 corners, 12 triangles (2 per face). The face order MUST match world.rchit's
        // CUBE_N normals indexed by gl_PrimitiveID>>1: -Z, +Z, -Y, +Y, -X, +X.
        float[] verts = {
                0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0,   // 0..3 (z=0)
                0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1,   // 4..7 (z=1)
        };
        int[] idxs = {
                0, 1, 2, 0, 2, 3,   // face 0: -Z
                4, 5, 6, 4, 6, 7,   // face 1: +Z
                0, 1, 5, 0, 5, 4,   // face 2: -Y
                3, 2, 6, 3, 6, 7,   // face 3: +Y
                0, 3, 7, 0, 7, 4,   // face 4: -X
                1, 2, 6, 1, 6, 5,   // face 5: +X
        };
        int asInput = org.lwjgl.vulkan.KHRAccelerationStructure.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR;
        cubePositions = ctx.createBuffer((long) verts.length * Float.BYTES, asInput, true);
        cubeIndices = ctx.createBuffer((long) idxs.length * Integer.BYTES, asInput, true);
        MemoryUtil.memFloatBuffer(cubePositions.mapped, verts.length).put(verts);
        MemoryUtil.memIntBuffer(cubeIndices.mapped, idxs.length).put(idxs);
        // Opaque (entity boxes never run the cutout any-hit). Built synchronously — this runs once.
        RtAccel.PreparedBlas prepared = RtAccel.prepareTrianglesBlas(ctx, cubePositions, verts.length / 3, cubeIndices, idxs.length, true);
        List<RtAccel.PreparedBlas> one = List.of(prepared);
        ctx.submitSync(cmd -> RtAccel.recordBlasBuilds(cmd, one));
        RtAccel.freeBlasScratch(one);
        cubeBlas = prepared.accel;
        cubeBlasAddr = cubeBlas.deviceAddress;
    }

    /** Free the shared cube BLAS + its source buffers + the displacement-table ring (teardown; GPU idle). */
    public void shutdown() {
        if (cubeBlas != null) {
            cubeBlas.destroy();
            cubeBlas = null;
        }
        if (cubePositions != null) {
            cubePositions.destroy();
            cubePositions = null;
        }
        if (cubeIndices != null) {
            cubeIndices.destroy();
            cubeIndices = null;
        }
        if (tableRing != null) {
            for (RtBuffer b : tableRing) {
                b.destroy();
            }
            tableRing = null;
        }
        cubeBlasAddr = 0L;
        entityTableAddr = 0L;
        prevPositions.clear();
    }
}
