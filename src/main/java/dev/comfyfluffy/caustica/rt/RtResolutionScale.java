package dev.comfyfluffy.caustica.rt;

import dev.comfyfluffy.caustica.CausticaConfig;

/** Shared preset and percentage rules for realtime reconstruction sizing. */
public final class RtResolutionScale {
    public static final int CUSTOM_QUALITY = -1;

    private RtResolutionScale() {
    }

    /** Display pixels per input pixel for each DLSS quality shortcut. */
    public static double presetUpscaleRatio(int quality) {
        return switch (quality) {
            case 3 -> 3.0;
            case 1 -> 1.7;
            case 2 -> 1.5;
            case 5 -> 1.0;
            default -> 2.0;
        };
    }

    public static int presetPercent(int quality) {
        return (int)Math.round(100.0 / presetUpscaleRatio(quality));
    }

    public static int displayedQuality() {
        return displayedQuality(CausticaConfig.Rt.DlssRr.INPUT_SCALE_PERCENT.configuredValue());
    }

    public static int displayedQuality(int inputScalePercent) {
        int quality = CausticaConfig.Rt.DlssRr.QUALITY.configuredValue();
        int shortcutPercent = shortcutInputPercent(
                CausticaConfig.Rt.OutputScale.PERCENT.configuredValue(), quality);
        return inputScalePercent == shortcutPercent
                ? quality : CUSTOM_QUALITY;
    }

    public static void selectQuality(int quality) {
        CausticaConfig.Rt.DlssRr.QUALITY.set(quality);
        CausticaConfig.Rt.DlssRr.INPUT_SCALE_PERCENT.set(shortcutInputPercent(
                CausticaConfig.Rt.OutputScale.PERCENT.value(), quality));
    }

    public static int shortcutInputPercent(int outputPercent, int quality) {
        return Math.clamp((int)Math.round(outputPercent / presetUpscaleRatio(quality)), 10, 200);
    }

    /** Exact preset dimension; avoids turning a 3.00x preset into 3.03x via integer-percent rounding. */
    public static int presetInputDimension(int outputDimension, int quality) {
        return Math.max(1, (int)Math.round(outputDimension / presetUpscaleRatio(quality)));
    }
}
