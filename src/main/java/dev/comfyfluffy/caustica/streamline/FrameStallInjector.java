package dev.comfyfluffy.caustica.streamline;

import java.util.concurrent.locks.LockSupport;

/** Development-only deterministic CPU stall injector; disabled in production by default. */
public final class FrameStallInjector {
    private static final boolean ENABLED = Boolean.getBoolean("caustica.streamline.stall.cpu");
    private static final long STALL_NS = Math.max(0L, Long.getLong("caustica.streamline.stall.cpu.ms", 0L))
            * 1_000_000L;
    private static final int EVERY = Math.max(1, Integer.getInteger("caustica.streamline.stall.every", 1));

    private FrameStallInjector() {
    }

    public static boolean enabled() {
        return ENABLED && STALL_NS > 0L;
    }

    public static void maybeInject(StreamlineFrameTrace trace, long token, int frameIndex) {
        if (!enabled() || frameIndex % EVERY != 0) {
            return;
        }
        trace.record(StreamlineFrameTraceEvent.CPU_STALL_BEGIN, token, STALL_NS, 0L, frameIndex,
                StreamlineFrameTraceEvent.PRODUCER_CLIENT);
        LockSupport.parkNanos(STALL_NS);
        trace.record(StreamlineFrameTraceEvent.CPU_STALL_END, token, STALL_NS, 0L, frameIndex,
                StreamlineFrameTraceEvent.PRODUCER_CLIENT);
    }
}
