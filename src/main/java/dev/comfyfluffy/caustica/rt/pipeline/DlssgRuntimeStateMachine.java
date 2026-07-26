package dev.comfyfluffy.caustica.rt.pipeline;

/** Recoverable DLSS-G state machine; transient failures cannot latch session-fatal. */
public final class DlssgRuntimeStateMachine {
    public static final int REQUIRED_STABLE_FRAMES = 4;
    private static final int REPEATED_VENDOR_ERROR_LIMIT = 8;
    private static final int ERROR_WINDOW_FRAMES = 120;
    private DlssgRuntimeState state = DlssgRuntimeState.UNINITIALIZED;
    private DlssgTransitionReason reason = DlssgTransitionReason.NONE;
    private long transitionSequence;
    private int stableFrames;
    private int lastError;
    private int sameErrorCount;
    private long errorWindowStartFrame;

    public boolean transition(DlssgRuntimeState next, DlssgTransitionReason why, long frame, long nowNs) {
        if (!legal(state, next)) return false;
        if (state == next && reason == why) return false;
        state = next;
        reason = why;
        transitionSequence++;
        stableFrames = 0;
        return true;
    }

    public void onValidSourceFrame(long frame, long nowNs) {
        if (state == DlssgRuntimeState.WARMUP || state == DlssgRuntimeState.SUSPENDED_HITCH
                || state == DlssgRuntimeState.SUSPENDED_INPUT_STARVATION
                || state == DlssgRuntimeState.SUSPENDED_OUT_OF_BAND) {
            if (++stableFrames >= REQUIRED_STABLE_FRAMES) {
                transition(DlssgRuntimeState.RECOVERING, DlssgTransitionReason.NONE, frame, nowNs);
            }
        } else {
            stableFrames = 0;
        }
    }

    public void onGeneratedPresentationConfirmed(long frame, long nowNs) {
        if (state == DlssgRuntimeState.RECOVERING) {
            transition(DlssgRuntimeState.ACTIVE, DlssgTransitionReason.NONE, frame, nowNs);
        }
    }

    public boolean recordVendorError(int code, long frame, long nowNs) {
        if (code != lastError || frame - errorWindowStartFrame > ERROR_WINDOW_FRAMES) {
            lastError = code;
            sameErrorCount = 1;
            errorWindowStartFrame = frame;
            return false;
        }
        if (++sameErrorCount >= REPEATED_VENDOR_ERROR_LIMIT) {
            transition(DlssgRuntimeState.FATAL, DlssgTransitionReason.API_ERROR, frame, nowNs);
            return true;
        }
        return false;
    }

    private static boolean legal(DlssgRuntimeState from, DlssgRuntimeState to) {
        if (to == DlssgRuntimeState.FATAL) return true;
        if (to == DlssgRuntimeState.IDLE) return from != DlssgRuntimeState.FATAL;
        return switch (from) {
            case UNINITIALIZED -> to == DlssgRuntimeState.UNAVAILABLE || to == DlssgRuntimeState.IDLE;
            case UNAVAILABLE -> to == DlssgRuntimeState.IDLE;
            case IDLE -> to == DlssgRuntimeState.WARMUP || to == DlssgRuntimeState.RECONFIGURING;
            case WARMUP -> suspended(to) || to == DlssgRuntimeState.RECOVERING || to == DlssgRuntimeState.RECONFIGURING;
            case ACTIVE -> suspended(to) || to == DlssgRuntimeState.RECONFIGURING;
            case SUSPENDED_MENU, SUSPENDED_HITCH, SUSPENDED_INPUT_STARVATION, SUSPENDED_OUT_OF_BAND ->
                    to == DlssgRuntimeState.RECOVERING || to == DlssgRuntimeState.RECONFIGURING || suspended(to);
            case RECONFIGURING -> to == DlssgRuntimeState.WARMUP || to == DlssgRuntimeState.UNAVAILABLE;
            case RECOVERING -> to == DlssgRuntimeState.ACTIVE || suspended(to) || to == DlssgRuntimeState.RECONFIGURING;
            case FATAL -> false;
        };
    }

    private static boolean suspended(DlssgRuntimeState value) {
        return value == DlssgRuntimeState.SUSPENDED_MENU || value == DlssgRuntimeState.SUSPENDED_HITCH
                || value == DlssgRuntimeState.SUSPENDED_INPUT_STARVATION
                || value == DlssgRuntimeState.SUSPENDED_OUT_OF_BAND;
    }

    public DlssgRuntimeState state() { return state; }
    public DlssgTransitionReason reason() { return reason; }
    public long transitionSequence() { return transitionSequence; }
    public int stableFrames() { return stableFrames; }
}
