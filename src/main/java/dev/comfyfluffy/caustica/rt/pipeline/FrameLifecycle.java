package dev.comfyfluffy.caustica.rt.pipeline;

/** Allocation-free legality gate for normal and out-of-band Streamline tokens. */
public final class FrameLifecycle {
    public static final int NONE = 0;
    public static final int NORMAL = 1;
    public static final int OUT_OF_BAND = 2;

    private long token;
    private int frameIndex = -1;
    private int kind;
    private int markers;
    private int applicationSubmitCount;
    private int presentResult;
    private long beginNs;
    private long firstSubmitNs;
    private long lastSubmitNs;
    private long presentNs;
    private long violations;
    private boolean realFrameOnly;
    private FrameLifecycleState state = FrameLifecycleState.EMPTY;

    public boolean beginNormal(int frameIndex, long token, long nowNs) {
        if (state != FrameLifecycleState.EMPTY && state != FrameLifecycleState.COMPLETE
                && state != FrameLifecycleState.ABANDONED) return reject();
        reset(frameIndex, token, NORMAL, nowNs);
        return true;
    }

    public boolean beginOutOfBand(int frameIndex, long token, long nowNs) {
        if (state != FrameLifecycleState.EMPTY && state != FrameLifecycleState.COMPLETE
                && state != FrameLifecycleState.ABANDONED) return reject();
        reset(frameIndex, token, OUT_OF_BAND, nowNs);
        realFrameOnly = true;
        return true;
    }

    private void reset(int frameIndex, long token, int kind, long nowNs) {
        this.frameIndex = frameIndex;
        this.token = token;
        this.kind = kind;
        this.markers = 0;
        this.applicationSubmitCount = 0;
        this.presentResult = 0;
        this.beginNs = nowNs;
        this.firstSubmitNs = 0L;
        this.lastSubmitNs = 0L;
        this.presentNs = 0L;
        this.realFrameOnly = false;
        this.state = FrameLifecycleState.TOKEN_ACQUIRED;
    }

    public boolean markSleep() {
        return require(kind == NORMAL && state == FrameLifecycleState.TOKEN_ACQUIRED
                && (markers & 1) == 0, FrameLifecycleState.SLEEP_COMPLETE, 1);
    }

    public boolean markSimulationStart() {
        return require(kind == NORMAL && (state == FrameLifecycleState.SLEEP_COMPLETE
                || (state == FrameLifecycleState.TOKEN_ACQUIRED && (markers & 1) == 0))
                && (markers & 2) == 0, FrameLifecycleState.SIMULATION_ACTIVE, 2);
    }

    public boolean markSimulationEnd() {
        return require(kind == NORMAL && state == FrameLifecycleState.SIMULATION_ACTIVE
                && (markers & 4) == 0, FrameLifecycleState.SIMULATION_COMPLETE, 4);
    }

    public boolean markRenderSubmitStart() {
        return require(kind == NORMAL && state == FrameLifecycleState.SIMULATION_COMPLETE
                && (markers & 8) == 0, FrameLifecycleState.RENDER_SUBMIT_ACTIVE, 8);
    }

    public boolean markApplicationSubmit() {
        if (kind != NORMAL || state != FrameLifecycleState.RENDER_SUBMIT_ACTIVE) return reject();
        applicationSubmitCount++;
        long now = System.nanoTime();
        if (firstSubmitNs == 0L) firstSubmitNs = now;
        lastSubmitNs = now;
        return true;
    }

    public boolean markRenderSubmitEnd() {
        return require(kind == NORMAL && state == FrameLifecycleState.RENDER_SUBMIT_ACTIVE
                && (markers & 16) == 0, FrameLifecycleState.RENDER_SUBMIT_COMPLETE, 16);
    }

    public boolean markPresentStart() {
        boolean valid = (kind == NORMAL && state == FrameLifecycleState.RENDER_SUBMIT_COMPLETE)
                || (kind == OUT_OF_BAND && state == FrameLifecycleState.TOKEN_ACQUIRED);
        if (!valid || (markers & 32) != 0) return reject();
        markers |= 32;
        state = FrameLifecycleState.PRESENT_ACTIVE;
        return true;
    }

    public boolean markPresentEnd(int vkResult) {
        if (state != FrameLifecycleState.PRESENT_ACTIVE || (markers & 64) != 0) return reject();
        presentResult = vkResult;
        presentNs = System.nanoTime();
        markers |= 64;
        state = FrameLifecycleState.COMPLETE;
        return true;
    }

    public boolean markLatencyPing() {
        if (kind != NORMAL || (markers & 128) != 0) return reject();
        markers |= 128;
        return true;
    }

    public void forceRealFrameOnly() {
        realFrameOnly = true;
    }

    public void abandon(DlssgTransitionReason reason) {
        realFrameOnly = true;
        state = FrameLifecycleState.ABANDONED;
    }

    private boolean require(boolean valid, FrameLifecycleState next, int bit) {
        if (!valid) return reject();
        markers |= bit;
        state = next;
        return true;
    }

    private boolean reject() {
        violations++;
        return false;
    }

    public void resetAfterPresent() {
        state = FrameLifecycleState.EMPTY;
        kind = NONE;
        token = 0L;
        frameIndex = -1;
        markers = 0;
        applicationSubmitCount = 0;
        realFrameOnly = false;
    }

    public long token() { return token; }
    public int frameIndex() { return frameIndex; }
    public int kind() { return kind; }
    public FrameLifecycleState state() { return state; }
    public boolean normal() { return kind == NORMAL; }
    public boolean outOfBand() { return kind == OUT_OF_BAND; }
    public boolean sleepIssued() { return (markers & 1) != 0; }
    public boolean simulationStarted() { return (markers & 2) != 0; }
    public boolean simulationEnded() { return (markers & 4) != 0; }
    public boolean renderSubmitStarted() { return (markers & 8) != 0; }
    public boolean renderSubmitEnded() { return (markers & 16) != 0; }
    public boolean presentStarted() { return (markers & 32) != 0; }
    public boolean presentEnded() { return (markers & 64) != 0; }
    public boolean latencyPingIssued() { return (markers & 128) != 0; }
    public boolean realFrameOnly() { return realFrameOnly; }
    public int applicationSubmitCount() { return applicationSubmitCount; }
    public int presentResult() { return presentResult; }
    public long violations() { return violations; }
    public long beginNs() { return beginNs; }
    public long firstSubmitNs() { return firstSubmitNs; }
    public long lastSubmitNs() { return lastSubmitNs; }
    public long presentNs() { return presentNs; }
}
