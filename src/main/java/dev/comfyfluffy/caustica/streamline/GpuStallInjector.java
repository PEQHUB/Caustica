package dev.comfyfluffy.caustica.streamline;

/** Development-only GPU-stall contract. The Vulkan submission seam records this request. */
public final class GpuStallInjector {
    private static final boolean ENABLED = Boolean.getBoolean("caustica.streamline.stall.gpu");
    private static final long STALL_NS = Math.max(0L, Long.getLong("caustica.streamline.stall.gpu.ms", 0L))
            * 1_000_000L;
    private static final int EVERY = Math.max(1, Integer.getInteger("caustica.streamline.stall.every", 1));

    private GpuStallInjector() {
    }

    public static boolean enabled() {
        return ENABLED && STALL_NS > 0L;
    }

    public static boolean shouldInject(int frameIndex) {
        return enabled() && frameIndex % EVERY == 0;
    }

    public static long durationNs() {
        return STALL_NS;
    }
}
