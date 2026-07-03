package dev.upscaler.rt.lod;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * Greedy cuboid proxy mesher for one LOD node (milestone M2 of docs/LOD_PLAN.md). Every non-air cell
 * is treated as an opaque unit cube (water/leaves deliberately flattened — the plan's accepted M2
 * simplification); faces hidden by any non-air neighbour are dropped, the rest are greedy-merged per
 * (face direction, layer, palette id) into maximal rectangles. No UVs — shading is the flat palette
 * albedo carried in per-triangle {@code Prim} records with the exact terrain layout (12 floats:
 * normal.xyz + emission.w, tint.rgb + 0, rough/metal + 4 spare lanes), so {@code world.rchit}'s LOD
 * branch reuses the terrain Prim struct unchanged.
 *
 * <p>Pure CPU over a cell snapshot + palette reads — runs on {@link dev.upscaler.rt.terrain.RtWorkerPool}.
 * Vertices are node-local (0..32·cellSize per axis, float-exact); the TLAS instance supplies
 * {@code nodeOrigin − rebaseOrigin}.
 */
final class RtLodMeshBuilder {
    private static final int N = RtLodSection.SIZE;
    private static final int PRIM_FLOATS = 12; // {nx,ny,nz,emission, r,g,b,materialKind, rough,metal, hasS,hasN}

    private RtLodMeshBuilder() {
    }

    /** One node's proxy mesh, upload-ready. {@code prim} is 2 identical records per quad (1 per tri). */
    record LodMesh(float[] positions, int[] indices, float[] prim, int triCount) {
    }

    /**
     * Mesh one node from a 32³ snapshot of global palette ids in {@code (y*32 + z)*32 + x} order.
     * {@code cellSize} = 2^level blocks. Returns null when nothing is visible.
     */
    static LodMesh build(RtLodPalette palette, int[] cells, float cellSize) {
        FloatArrayList verts = new FloatArrayList(4096);
        IntArrayList indices = new IntArrayList(4096);
        FloatArrayList prim = new FloatArrayList(4096);
        int[] mask = new int[N * N];
        for (int axis = 0; axis < 3; axis++) {
            for (int dir = -1; dir <= 1; dir += 2) {
                for (int a = 0; a < N; a++) {
                    if (buildMask(cells, axis, dir, a, mask)) {
                        emitLayer(palette, mask, axis, dir, a, cellSize, verts, indices, prim);
                    }
                }
            }
        }
        if (indices.isEmpty()) {
            return null;
        }
        return new LodMesh(verts.toFloatArray(), indices.toIntArray(), prim.toFloatArray(), indices.size() / 3);
    }

    /**
     * Fill the 32×32 face mask for one (axis, dir, layer): the cell's palette id where the cell is
     * non-air and its neighbour toward {@code dir} is air, else 0. Out-of-node neighbours count as air
     * (border faces are emitted; the extra hidden faces where a neighbour node is solid are occluded
     * geometry the tracer never shades). Returns whether any face is visible.
     */
    private static boolean buildMask(int[] cells, int axis, int dir, int a, int[] mask) {
        boolean any = false;
        int na = a + dir;
        boolean edge = na < 0 || na >= N;
        for (int v = 0; v < N; v++) {
            for (int u = 0; u < N; u++) {
                int id = cells[cellIndex(axis, a, u, v)];
                if (id != RtLodPalette.AIR_ID && (edge || cells[cellIndex(axis, na, u, v)] == RtLodPalette.AIR_ID)) {
                    mask[v * N + u] = id;
                    any = true;
                } else {
                    mask[v * N + u] = 0;
                }
            }
        }
        return any;
    }

    /** (axis, layer, u, v) -> flat cell index. Layout matches RtLodSection: ((y*32)+z)*32+x. */
    private static int cellIndex(int axis, int a, int u, int v) {
        // axis 0 (x faces): x=a, z=u, y=v; axis 1 (y faces): y=a, x=u, z=v; axis 2 (z faces): z=a, x=u, y=v
        int x, y, z;
        switch (axis) {
            case 0 -> { x = a; z = u; y = v; }
            case 1 -> { y = a; x = u; z = v; }
            default -> { z = a; x = u; y = v; }
        }
        return ((y * N) + z) * N + x;
    }

    /** Standard greedy rectangle sweep over one mask layer; each rectangle becomes one quad (2 tris). */
    private static void emitLayer(RtLodPalette palette, int[] mask, int axis, int dir, int a, float cellSize,
                                  FloatArrayList verts, IntArrayList indices, FloatArrayList prim) {
        for (int v = 0; v < N; v++) {
            for (int u = 0; u < N; ) {
                int id = mask[v * N + u];
                if (id == 0) {
                    u++;
                    continue;
                }
                int w = 1;
                while (u + w < N && mask[v * N + u + w] == id) {
                    w++;
                }
                int h = 1;
                outer:
                while (v + h < N) {
                    for (int k = 0; k < w; k++) {
                        if (mask[(v + h) * N + u + k] != id) {
                            break outer;
                        }
                    }
                    h++;
                }
                for (int dv = 0; dv < h; dv++) {
                    for (int k = 0; k < w; k++) {
                        mask[(v + dv) * N + u + k] = 0;
                    }
                }
                emitQuad(palette, id, axis, dir, a, u, v, w, h, cellSize, verts, indices, prim);
                u += w;
            }
        }
    }

    private static void emitQuad(RtLodPalette palette, int id, int axis, int dir, int a, int u, int v, int w, int h,
                                 float cellSize, FloatArrayList verts, IntArrayList indices, FloatArrayList prim) {
        float plane = (a + (dir > 0 ? 1 : 0)) * cellSize;
        float u0 = u * cellSize, u1 = (u + w) * cellSize;
        float v0 = v * cellSize, v1 = (v + h) * cellSize;
        int base = verts.size() / 3;
        putCorner(verts, axis, plane, u0, v0);
        putCorner(verts, axis, plane, u1, v0);
        putCorner(verts, axis, plane, u1, v1);
        putCorner(verts, axis, plane, u0, v1);
        indices.add(base);
        indices.add(base + 1);
        indices.add(base + 2);
        indices.add(base);
        indices.add(base + 2);
        indices.add(base + 3);

        RtLodPalette.Entry e = palette.entry(id);
        float nx = axis == 0 ? dir : 0f;
        float ny = axis == 1 ? dir : 0f;
        float nz = axis == 2 ? dir : 0f;
        float emission = (e.emission() & 0xFF) / 15f;
        // Flat albedo: MapColor is sRGB; the terrain path gets linearization from the SRGB atlas view, so
        // convert here to match. Water proxies stay opaque at M2 but keep a low roughness for the far glint.
        int argb = e.argb();
        float r = srgbToLinear(((argb >> 16) & 0xFF) / 255f);
        float g = srgbToLinear(((argb >> 8) & 0xFF) / 255f);
        float b = srgbToLinear((argb & 0xFF) / 255f);
        float rough = e.kind() == RtLodPalette.KIND_WATER ? 0.08f : 0.9f;
        for (int t = 0; t < 2; t++) {
            prim.add(nx);
            prim.add(ny);
            prim.add(nz);
            prim.add(emission);
            prim.add(r);
            prim.add(g);
            prim.add(b);
            prim.add(0f); // tint.w = 0: opaque (never water/glass material routing)
            prim.add(rough);
            prim.add(0f); // metalness
            prim.add(0f); // hasS
            prim.add(0f); // hasN
        }
    }

    private static void putCorner(FloatArrayList verts, int axis, float plane, float cu, float cv) {
        // Inverse of cellIndex's (u,v) mapping per axis.
        switch (axis) {
            case 0 -> { verts.add(plane); verts.add(cv); verts.add(cu); }    // x plane: (x, y=v, z=u)
            case 1 -> { verts.add(cu); verts.add(plane); verts.add(cv); }    // y plane: (x=u, y, z=v)
            default -> { verts.add(cu); verts.add(cv); verts.add(plane); }   // z plane: (x=u, y=v, z)
        }
    }

    private static float srgbToLinear(float c) {
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
