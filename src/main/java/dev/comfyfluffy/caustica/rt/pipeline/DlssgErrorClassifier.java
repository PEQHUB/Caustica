package dev.comfyfluffy.caustica.rt.pipeline;

public final class DlssgErrorClassifier {
    private DlssgErrorClassifier() { }

    public static DlssgTransitionReason classifyPresent(int vkResult) {
        return vkResult == 0 ? DlssgTransitionReason.NONE
                : vkResult == 1 || vkResult == 1000001003 || vkResult == -1000001004
                ? DlssgTransitionReason.PRESENT_OUT_OF_DATE
                : DlssgTransitionReason.API_ERROR;
    }

    public static boolean recoverable(DlssgTransitionReason reason) {
        return reason != DlssgTransitionReason.DEVICE_LOST
                && reason != DlssgTransitionReason.ABI_MISMATCH
                && reason != DlssgTransitionReason.UNSUPPORTED;
    }
}
