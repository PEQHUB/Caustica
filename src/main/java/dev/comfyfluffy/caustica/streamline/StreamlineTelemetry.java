package dev.comfyfluffy.caustica.streamline;

import java.util.concurrent.atomic.AtomicLong;

/** Allocation-free latest-value telemetry publication. */
public final class StreamlineTelemetry {
    private final AtomicLong sequence = new AtomicLong();
    private volatile long timestampNs;
    private volatile long frameToken;
    private volatile long sourcePeriodNs;
    private volatile long applicationPresentPeriodNs;
    private volatile long completionAgeNs;
    private volatile long completionValue;
    private volatile int runtimeState;
    private volatile int reason;
    private volatile int pacingOwner;
    private volatile int requestedGeneratedFrames;
    private volatile int actualGeneratedFrames;
    private volatile int freeSlots;
    private volatile int pendingSlots;
    private volatile int quarantinedSlots;
    private volatile int apiError;

    public void publish(long nowNs, long token, long sourcePeriod, long presentPeriod,
            long completionAge, long completion, int state, int transitionReason, int owner,
            int requested, int actual, int free, int pending, int quarantined, int error) {
        sequence.incrementAndGet();
        timestampNs = nowNs;
        frameToken = token;
        sourcePeriodNs = sourcePeriod;
        applicationPresentPeriodNs = presentPeriod;
        completionAgeNs = completionAge;
        completionValue = completion;
        runtimeState = state;
        reason = transitionReason;
        pacingOwner = owner;
        requestedGeneratedFrames = requested;
        actualGeneratedFrames = actual;
        freeSlots = free;
        pendingSlots = pending;
        quarantinedSlots = quarantined;
        apiError = error;
        sequence.incrementAndGet();
    }

    public boolean copyInto(MutableSnapshot out) {
        for (int attempt = 0; attempt < 3; attempt++) {
            long before = sequence.get();
            if ((before & 1L) != 0L) continue;
            out.timestampNs = timestampNs;
            out.frameToken = frameToken;
            out.sourcePeriodNs = sourcePeriodNs;
            out.applicationPresentPeriodNs = applicationPresentPeriodNs;
            out.completionAgeNs = completionAgeNs;
            out.completionValue = completionValue;
            out.runtimeState = runtimeState;
            out.reason = reason;
            out.pacingOwner = pacingOwner;
            out.requestedGeneratedFrames = requestedGeneratedFrames;
            out.actualGeneratedFrames = actualGeneratedFrames;
            out.freeSlots = freeSlots;
            out.pendingSlots = pendingSlots;
            out.quarantinedSlots = quarantinedSlots;
            out.apiError = apiError;
            long after = sequence.get();
            if (before == after && (after & 1L) == 0L) return true;
        }
        return false;
    }

    public static final class MutableSnapshot {
        public long timestampNs, frameToken, sourcePeriodNs, applicationPresentPeriodNs;
        public long completionAgeNs, completionValue;
        public int runtimeState, reason, pacingOwner;
        public int requestedGeneratedFrames, actualGeneratedFrames;
        public int freeSlots, pendingSlots, quarantinedSlots, apiError;
    }
}
