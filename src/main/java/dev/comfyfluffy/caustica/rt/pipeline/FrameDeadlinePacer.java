package dev.comfyfluffy.caustica.rt.pipeline;

import java.util.concurrent.locks.LockSupport;

/** Absolute-deadline CPU pacer used when Vulkan Reflex does not enforce its configured frame limit. */
final class FrameDeadlinePacer {
    private static final long NANOS_PER_MICROSECOND = 1_000L;
    private static final long SPIN_THRESHOLD_NANOS = 200_000L;

    private long intervalNanos;
    private long nextDeadlineNanos;

    void pace(int intervalUs) {
        long requestedInterval = Math.max(0L, intervalUs) * NANOS_PER_MICROSECOND;
        if (requestedInterval == 0L) {
            reset();
            return;
        }

        long now = System.nanoTime();
        if (requestedInterval != intervalNanos || nextDeadlineNanos == 0L
                || now - nextDeadlineNanos > requestedInterval) {
            intervalNanos = requestedInterval;
            nextDeadlineNanos = now + requestedInterval;
            return;
        }

        waitUntil(nextDeadlineNanos);
        now = System.nanoTime();
        nextDeadlineNanos = advanceDeadline(nextDeadlineNanos, now, requestedInterval);
    }

    void reset() {
        intervalNanos = 0L;
        nextDeadlineNanos = 0L;
    }

    static long advanceDeadline(long previousDeadline, long now, long interval) {
        long scheduled = previousDeadline + interval;
        return now - scheduled > interval ? now + interval : scheduled;
    }

    private static void waitUntil(long deadlineNanos) {
        while (true) {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0L) {
                return;
            }
            if (remaining > SPIN_THRESHOLD_NANOS) {
                LockSupport.parkNanos(remaining - SPIN_THRESHOLD_NANOS);
            } else {
                Thread.onSpinWait();
            }
        }
    }
}
