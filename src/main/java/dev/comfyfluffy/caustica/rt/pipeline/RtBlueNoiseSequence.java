package dev.comfyfluffy.caustica.rt.pipeline;

import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.accel.RtBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;

/** Precomputed 64x64, 8D Owen-scrambled Sobol sequence used by the live path tracer. */
public final class RtBlueNoiseSequence {
    static final int SIDE = 64;
    static final int SAMPLE_COUNT = SIDE * SIDE;
    static final int DIMENSIONS = 8;
    // Pair dimensions by measured 16x16 projection quality, not numerical adjacency.
    private static final int[] DIMENSION_ORDER = {0, 3, 1, 2, 4, 6, 5, 7};
    private static final long BYTE_SIZE = (long) SAMPLE_COUNT * DIMENSIONS * Integer.BYTES;

    private final RtBuffer buffer;

    private RtBlueNoiseSequence(RtBuffer buffer) {
        this.buffer = buffer;
    }

    public static RtBlueNoiseSequence create(RtContext ctx) {
        RtBuffer buffer = ctx.createBuffer(BYTE_SIZE, VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, true,
                "64x64 8D Owen-Sobol live path samples");
        if (buffer.mapped == 0L) {
            buffer.destroy();
            throw new IllegalStateException("Live Owen-Sobol path samples are not host mapped");
        }
        for (int sample = 0; sample < SAMPLE_COUNT; sample++) {
            long offset = buffer.mapped + (long) sample * DIMENSIONS * Integer.BYTES;
            for (int dimension = 0; dimension < DIMENSIONS; dimension++) {
                MemoryUtil.memPutInt(offset + (long) dimension * Integer.BYTES,
                        sampleBits(sample, dimension));
            }
        }
        return new RtBlueNoiseSequence(buffer);
    }

    public long deviceAddress() {
        return buffer.deviceAddress;
    }

    public void destroy() {
        buffer.destroy();
    }

    static int sampleBits(int sample, int dimension) {
        int sobolDimension = DIMENSION_ORDER[dimension];
        int seed = sampleHash((sobolDimension + 1) * 0x9e3779b9);
        return owenScramble(RtPathSampleSequence.sobolBits(sample, sobolDimension), seed);
    }

    private static int owenScramble(int value, int seed) {
        int x = Integer.reverse(value);
        x ^= x * 0x3d20adea;
        x += seed;
        x *= (seed >>> 16) | 1;
        x ^= x * 0x05526c56;
        x ^= x * 0x53a22864;
        return Integer.reverse(x);
    }

    private static int sampleHash(int value) {
        int x = value;
        x ^= x >>> 16;
        x *= 0x21f0aaad;
        x ^= x >>> 15;
        x *= 0xf35a2d97;
        x ^= x >>> 15;
        return x;
    }
}
