package dev.comfyfluffy.caustica.rt.pipeline;

public enum FrameDiscontinuityReason {
    NONE, PERIOD_OUTLIER, CAMERA_CUT, TELEPORT, DIMENSION_CHANGE, RESOURCE_RELOAD, SWAPCHAIN_CHANGE,
    PRESENT_OUT_OF_DATE, MISSING_INPUT
}
