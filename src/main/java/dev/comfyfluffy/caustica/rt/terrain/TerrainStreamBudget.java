package dev.comfyfluffy.caustica.rt.terrain;

/** Pure scheduling policy for the render-thread terrain streaming deadline. */
final class TerrainStreamBudget {
    private static final int FULL_QUEUE_PRESSURE = 256;

    private TerrainStreamBudget() {
    }

    static long adaptiveBudgetNanos(double baseMs, double maxMs, int queued, int outstanding, int outstandingCap) {
        double low = Math.max(0.0, baseMs);
        double high = Math.max(low, maxMs);
        double queuePressure = Math.min(1.0, Math.max(0, queued) / (double) FULL_QUEUE_PRESSURE);
        double flightPressure = outstandingCap <= 0 ? 0.0
                : Math.min(1.0, Math.max(0, outstanding) / (double) outstandingCap);
        double pressure = Math.max(queuePressure, flightPressure);
        return millisToNanos(low + (high - low) * pressure);
    }

    static long fixedBudgetNanos(double budgetMs) {
        return millisToNanos(Math.max(0.0, budgetMs));
    }

    static long deadline(long startNanos, long budgetNanos) {
        if (budgetNanos >= Long.MAX_VALUE - startNanos) {
            return Long.MAX_VALUE;
        }
        return startNanos + budgetNanos;
    }

    private static long millisToNanos(double millis) {
        double nanos = millis * 1_000_000.0;
        return nanos >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) nanos;
    }
}
