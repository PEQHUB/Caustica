package dev.comfyfluffy.caustica.rt.pipeline;

/** One-frame temporal reset signal shared by explicit renderer events. */
public final class RtTemporalDiscontinuity {
    private boolean pending;
    private FrameDiscontinuityReason reason = FrameDiscontinuityReason.NONE;

    public void request(FrameDiscontinuityReason reason) {
        pending = true;
        this.reason = reason == null ? FrameDiscontinuityReason.NONE : reason;
    }

    public boolean consume() {
        boolean result = pending;
        pending = false;
        reason = FrameDiscontinuityReason.NONE;
        return result;
    }

    public boolean pending() { return pending; }
    public FrameDiscontinuityReason reason() { return reason; }
}
