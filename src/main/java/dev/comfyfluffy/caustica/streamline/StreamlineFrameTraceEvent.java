package dev.comfyfluffy.caustica.streamline;

/** Integer event identifiers for the allocation-free Streamline frame trace. */
public final class StreamlineFrameTraceEvent {
    private StreamlineFrameTraceEvent() {
    }

    public static final int FRAME_BEGIN = 1;
    public static final int REFLEX_OPTIONS = 2;
    public static final int REFLEX_SLEEP_ENTER = 3;
    public static final int REFLEX_SLEEP_EXIT = 4;
    public static final int PCL_PING = 5;
    public static final int PCL_SIM_START = 6;
    public static final int PCL_SIM_END = 7;
    public static final int EVENT_POLL_BEGIN = 8;
    public static final int EVENT_POLL_END = 9;
    public static final int CURSOR_CALLBACK = 10;
    public static final int INPUT_ACCUMULATOR_DRAIN = 11;
    public static final int CAMERA_INPUT_CONSUME = 12;
    public static final int RENDER_RECORD_BEGIN = 13;
    public static final int QUEUE_SUBMIT_ENTER = 14;
    public static final int QUEUE_SUBMIT_EXIT = 15;
    public static final int PCL_RENDER_SUBMIT_START = 16;
    public static final int PCL_RENDER_SUBMIT_END = 17;
    public static final int FG_SLOT_ACQUIRE = 18;
    public static final int FG_SLOT_WAIT_ENTER = 19;
    public static final int FG_SLOT_WAIT_EXIT = 20;
    public static final int FG_TAGS_SUBMITTED = 21;
    public static final int DLSSG_OPTIONS_ENTER = 22;
    public static final int DLSSG_OPTIONS_EXIT = 23;
    public static final int PCL_PRESENT_START = 24;
    public static final int PROXY_PRESENT_ENTER = 25;
    public static final int PROXY_PRESENT_EXIT = 26;
    public static final int PCL_PRESENT_END = 27;
    public static final int DLSSG_STATE_ENTER = 28;
    public static final int DLSSG_STATE_EXIT = 29;
    public static final int REFLEX_STATE_ENTER = 30;
    public static final int REFLEX_STATE_EXIT = 31;
    public static final int ACCEPTANCE_REPORT_ENTER = 32;
    public static final int ACCEPTANCE_REPORT_EXIT = 33;
    public static final int MINECRAFT_LIMITER_ENTER = 34;
    public static final int MINECRAFT_LIMITER_EXIT = 35;
    public static final int FRAME_END = 36;
    public static final int FRAME_DISCONTINUITY = 37;
    public static final int STATE_TRANSITION = 38;
    public static final int CPU_STALL_BEGIN = 39;
    public static final int CPU_STALL_END = 40;
    public static final int GPU_STALL_SUBMITTED = 41;

    public static final int PRODUCER_CLIENT = 1;
    public static final int PRODUCER_RENDER = 2;
    public static final int PRODUCER_PRESENT = 3;
    public static final int PRODUCER_NATIVE = 4;
}
