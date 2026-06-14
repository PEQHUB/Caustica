package dev.upscaler.rt;

import dev.upscaler.UpscalerMod;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.lwjgl.system.MemoryUtil;

import java.util.List;

/**
 * P1 step 1: a one-shot full-cube extraction of the blocks around the player into an RT-native
 * position/index buffer pair, a BLAS, and a single-instance TLAS. Crude on purpose — every
 * non-air block is a unit cube, faces are emitted only where the neighbor is air (basic cull),
 * no model shapes / fluids / tint / lighting yet. The goal here is to prove world iteration +
 * extraction + AS build from real geometry; correct meshing (via Sodium) and shading come next.
 *
 * <p>Geometry is rebased to the player's block position ({@code (blockX,blockY,blockZ)}) so
 * coordinates stay small (camera-relative precision); the trace will offset rays by the
 * camera's position relative to that origin.
 */
public final class RtTerrain {
    public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("upscaler.rt.terrain", "true"));
    private static final int RADIUS = Integer.getInteger("upscaler.rt.terrainRadius", 16);

    private static RtTerrain instance;

    private final RtBuffer positions;
    private final RtBuffer indices;
    private final RtAccel blas;
    private final RtAccel tlas;
    /** World block coordinates of the rebase origin (geometry is stored relative to this). */
    public final int blockX;
    public final int blockY;
    public final int blockZ;

    private RtTerrain(RtBuffer positions, RtBuffer indices, RtAccel blas, RtAccel tlas, int bx, int by, int bz) {
        this.positions = positions;
        this.indices = indices;
        this.blas = blas;
        this.tlas = tlas;
        this.blockX = bx;
        this.blockY = by;
        this.blockZ = bz;
    }

    public static RtTerrain currentOrNull() {
        return instance;
    }

    public long tlas() {
        return tlas.handle;
    }

    /** Extract once the player is in a world. Returns true on success (then it's a no-op until rebuilt). */
    public static boolean extractAroundPlayer(RtContext ctx) {
        if (!ENABLED) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return false; // not in a world yet
        }
        BlockPos center = mc.player.blockPosition();
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        FloatArrayList verts = new FloatArrayList();
        IntArrayList idx = new IntArrayList();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int faces = 0;

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    m.set(cx + dx, cy + dy, cz + dz);
                    if (level.getBlockState(m).isAir()) {
                        continue;
                    }
                    for (int axis = 0; axis < 3; axis++) {
                        for (int val = 0; val < 2; val++) {
                            int nx = cx + dx;
                            int ny = cy + dy;
                            int nz = cz + dz;
                            int step = val == 1 ? 1 : -1;
                            if (axis == 0) {
                                nx += step;
                            } else if (axis == 1) {
                                ny += step;
                            } else {
                                nz += step;
                            }
                            m.set(nx, ny, nz);
                            if (level.getBlockState(m).isAir()) {
                                emitFace(verts, idx, dx, dy, dz, axis, val);
                                faces++;
                            }
                        }
                    }
                }
            }
        }

        if (idx.isEmpty()) {
            UpscalerMod.LOGGER.info("RT terrain: no solid geometry around ({},{},{}) — skipping", cx, cy, cz);
            return false;
        }

        int vertCount = verts.size() / 3;
        int idxCount = idx.size();
        int asInput = org.lwjgl.vulkan.KHRAccelerationStructure.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR;
        RtBuffer positions = ctx.createBuffer((long) verts.size() * Float.BYTES, asInput, true);
        RtBuffer indices = ctx.createBuffer((long) idx.size() * Integer.BYTES, asInput, true);
        MemoryUtil.memFloatBuffer(positions.mapped, verts.size()).put(verts.elements(), 0, verts.size());
        MemoryUtil.memIntBuffer(indices.mapped, idx.size()).put(idx.elements(), 0, idx.size());

        RtAccel blas = RtAccel.buildTrianglesBlas(ctx, positions, vertCount, indices, idxCount);
        float[] identity = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};
        RtAccel tlas = RtAccel.buildTlas(ctx, List.of(new RtAccel.Instance(identity, blas.deviceAddress)));

        if (instance != null) {
            instance.destroy();
        }
        instance = new RtTerrain(positions, indices, blas, tlas, cx, cy, cz);
        UpscalerMod.LOGGER.info("RT terrain: extracted {} faces ({} verts / {} indices) from {}^3 blocks around ({},{},{}); BLAS+TLAS built",
                faces, vertCount, idxCount, 2 * RADIUS + 1, cx, cy, cz);
        return true;
    }

    /** Emit one unit-cube face at block-local origin (bx,by,bz) on the given axis (0=x,1=y,2=z) at val (0 or 1). */
    private static void emitFace(FloatArrayList verts, IntArrayList idx, int bx, int by, int bz, int axis, int val) {
        int base = verts.size() / 3;
        int a = (axis + 1) % 3;
        int b = (axis + 2) % 3;
        for (int i = 0; i < 4; i++) {
            int va = (i == 1 || i == 2) ? 1 : 0;
            int vb = (i >= 2) ? 1 : 0;
            float[] c = new float[3];
            c[axis] = val;
            c[a] = va;
            c[b] = vb;
            verts.add(bx + c[0]);
            verts.add(by + c[1]);
            verts.add(bz + c[2]);
        }
        idx.add(base);
        idx.add(base + 1);
        idx.add(base + 2);
        idx.add(base);
        idx.add(base + 2);
        idx.add(base + 3);
    }

    public void destroy() {
        tlas.destroy();
        blas.destroy();
        indices.destroy();
        positions.destroy();
        if (instance == this) {
            instance = null;
        }
    }
}
