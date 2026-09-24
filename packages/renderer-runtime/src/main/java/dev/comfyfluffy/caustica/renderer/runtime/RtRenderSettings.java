package dev.comfyfluffy.caustica.renderer.runtime;

import dev.comfyfluffy.caustica.settings.OptionValues;
import dev.comfyfluffy.caustica.renderer.presentation.RtExposure;

/** Renderer settings captured together at the host frame or resource-configuration boundary. */
public record RtRenderSettings(int debugView, int maxBounces, float jitterSignX, float jitterSignY,
                               int peakNits, boolean hdr, RtExposure.Settings exposure, float rrResponsivity) {
    public static RtRenderSettings capture(OptionValues options, boolean pqActive) {
        return new RtRenderSettings(options.get(RendererOptions.Rt.Composite.DEBUG_VIEW),
                options.get(RendererOptions.Rt.Composite.MAX_BOUNCES),
                options.get(RendererOptions.Rt.Composite.JITTER_SIGN_X),
                options.get(RendererOptions.Rt.Composite.JITTER_SIGN_Y),
                options.get(RendererOptions.Rt.Hdr.PEAK_NITS),
                pqActive && options.get(RendererOptions.Rt.Hdr.ENABLED), exposure(options),
                options.get(RendererOptions.Rt.DlssRr.RESPONSIVITY));
    }

    private static RtExposure.Settings exposure(OptionValues options) {
        return new RtExposure.Settings(
                options.get(RendererOptions.Rt.Exposure.MODE),
                options.get(RendererOptions.Rt.Exposure.MANUAL_EV),
                options.get(RendererOptions.Rt.Exposure.KEY),
                options.get(RendererOptions.Rt.Exposure.ADAPT_DARKEN),
                options.get(RendererOptions.Rt.Exposure.ADAPT_BRIGHTEN),
                options.get(RendererOptions.Rt.Exposure.LOW_PERCENTILE),
                options.get(RendererOptions.Rt.Exposure.HIGH_PERCENTILE),
                options.get(RendererOptions.Rt.Exposure.STRIDE),
                options.get(RendererOptions.Rt.Exposure.CENTER_WEIGHT_SIGMA),
                options.get(RendererOptions.Rt.Exposure.CENTER_WEIGHT_FLOOR),
                options.get(RendererOptions.Rt.Exposure.ENVIRONMENT_WEIGHT_CAP),
                options.get(RendererOptions.Rt.Exposure.EMISSIVE_WEIGHT_CAP),
                options.get(RendererOptions.Rt.Exposure.PRE_EXPOSURE),
                options.get(RendererOptions.Rt.Tonemap.GAMMA));
    }
}
