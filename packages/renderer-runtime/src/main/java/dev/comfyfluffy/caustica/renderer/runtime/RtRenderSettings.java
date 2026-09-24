package dev.comfyfluffy.caustica.renderer.runtime;

import dev.comfyfluffy.caustica.settings.Option;
import dev.comfyfluffy.caustica.settings.OptionValues;
import dev.comfyfluffy.caustica.renderer.presentation.RtExposure;
import dev.comfyfluffy.caustica.renderer.presentation.RtToneMapping;

import java.util.List;

/** Renderer settings captured together at the host frame or resource-configuration boundary. */
public record RtRenderSettings(int debugView, int maxBounces, float jitterSignX, float jitterSignY,
                               int peakNits, boolean hdr, RtExposure.Settings exposure,
                               RtToneMapping.Settings toneMapping) {
    public static RtRenderSettings capture(OptionValues options, boolean pqActive) {
        return new RtRenderSettings(options.get(RendererOptions.Rt.Composite.DEBUG_VIEW),
                options.get(RendererOptions.Rt.Composite.MAX_BOUNCES),
                options.get(RendererOptions.Rt.Composite.JITTER_SIGN_X),
                options.get(RendererOptions.Rt.Composite.JITTER_SIGN_Y),
                options.get(RendererOptions.Rt.Hdr.PEAK_NITS),
                pqActive && options.get(RendererOptions.Rt.Hdr.ENABLED), exposure(options),
                toneMapping(options));
    }

    private static RtToneMapping.Settings toneMapping(OptionValues options) {
        RtToneMapping.SdrMode sdrMode = RtToneMapping.SdrMode.of(options.get(RendererOptions.Rt.Tonemap.SDR_MAPPER));
        RtToneMapping.HdrMode hdrMode = RtToneMapping.HdrMode.of(options.get(RendererOptions.Rt.Tonemap.HDR_MAPPER));
        float paperWhiteNits = options.get(RendererOptions.Rt.Tonemap.PAPER_WHITE_NITS);
        return new RtToneMapping.Settings(sdrMode, hdrMode, paperWhiteNits,
                options.get(RendererOptions.Rt.Hdr.PEAK_NITS) / paperWhiteNits,
                parameters(options, RendererOptions.Rt.Tonemap.SDR_CONTROLS.getOrDefault(sdrMode, List.of())),
                parameters(options, RendererOptions.Rt.Tonemap.HDR_CONTROLS.getOrDefault(hdrMode, List.of())));
    }

    private static RtToneMapping.Parameters parameters(OptionValues options, List<Option<Float>> controls) {
        float[] values = new float[controls.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = options.get(controls.get(i));
        }
        return RtToneMapping.Parameters.of(values);
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
