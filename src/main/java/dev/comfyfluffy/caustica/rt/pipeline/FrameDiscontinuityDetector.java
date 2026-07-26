package dev.comfyfluffy.caustica.rt.pipeline;

/** Fixed-storage source-frame period detector using median and MAD. */
public final class FrameDiscontinuityDetector {
    private static final int WINDOW = 31;
    private static final int WARMUP = 12;
    private static final long ABSOLUTE_MIN_NS = 35_000_000L;
    private final long[] samples = new long[WINDOW];
    private final long[] scratch = new long[WINDOW];
    private final long[] deviations = new long[WINDOW];
    private int count;
    private int cursor;
    private long lastStartNs;
    private long medianNs;
    private long madNs;
    private long thresholdNs = ABSOLUTE_MIN_NS;

    public boolean onSourceFrameStart(long nowNs, boolean sampleEligible) {
        if (lastStartNs == 0L) { lastStartNs = nowNs; return false; }
        long period = nowNs - lastStartNs;
        lastStartNs = nowNs;
        boolean discontinuity = count >= WARMUP && period > thresholdNs;
        if (sampleEligible && !discontinuity && period > 0L) {
            samples[cursor] = period;
            cursor = (cursor + 1) % WINDOW;
            if (count < WINDOW) count++;
            recompute();
        }
        return discontinuity;
    }

    public boolean onExplicitDiscontinuity(FrameDiscontinuityReason reason) {
        return reason != null && reason != FrameDiscontinuityReason.NONE;
    }

    public void resetTiming(long nowNs) { lastStartNs = nowNs; }
    private void recompute() {
        System.arraycopy(samples, 0, scratch, 0, count);
        sort(scratch, count);
        medianNs = scratch[count / 2];
        for (int i = 0; i < count; i++) deviations[i] = Math.abs(samples[i] - medianNs);
        sort(deviations, count);
        madNs = deviations[count / 2];
        long robust = safeAdd(safeMultiply(medianNs, 9L) / 4L, safeMultiply(madNs, 3L));
        thresholdNs = Math.max(ABSOLUTE_MIN_NS, robust);
    }
    private static void sort(long[] values, int length) {
        for (int i = 1; i < length; i++) {
            long value = values[i]; int j = i - 1;
            while (j >= 0 && values[j] > value) values[j + 1] = values[j--];
            values[j + 1] = value;
        }
    }
    private static long safeMultiply(long a, long b) {
        if (a <= 0 || b <= 0) return 0L;
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }
    private static long safeAdd(long a, long b) { return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b; }
    public int sampleCount() { return count; }
    public long medianNs() { return medianNs; }
    public long madNs() { return madNs; }
    public long thresholdNs() { return thresholdNs; }
}
