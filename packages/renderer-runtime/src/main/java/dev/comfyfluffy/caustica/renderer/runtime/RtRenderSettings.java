package dev.comfyfluffy.caustica.renderer.runtime;

import dev.comfyfluffy.caustica.settings.OptionValues;
import dev.comfyfluffy.caustica.renderer.presentation.RtExposure;

/** Renderer settings captured together at the host frame or resource-configuration boundary. */
public record RtRenderSettings(int debugView, int maxBounces, float jitterSignX, float jitterSignY,
                               int peakNits, boolean hdr, RtExposure.Settings exposure, Sharc sharc) {
    /** SHaRC cache controls; see {@link RendererOptions.Rt.Sharc}. */
    public record Sharc(boolean enabled, int cacheExponent, int updateTileSize, int accumulationFrames,
                        int staleFrames, float sceneScale, float gridLogarithmBase, float gridLevelBias,
                        float radianceScale, float roughnessThreshold, boolean antiFirefly,
                        boolean primarySurfaceDebug) { }

    public static RtRenderSettings capture(OptionValues options, boolean pqActive) {
        return new RtRenderSettings(options.get(RendererOptions.Rt.Composite.DEBUG_VIEW),
                options.get(RendererOptions.Rt.Composite.MAX_BOUNCES),
                options.get(RendererOptions.Rt.Composite.JITTER_SIGN_X),
                options.get(RendererOptions.Rt.Composite.JITTER_SIGN_Y),
                options.get(RendererOptions.Rt.Hdr.PEAK_NITS),
                pqActive && options.get(RendererOptions.Rt.Hdr.ENABLED), exposure(options), sharc(options));
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

    private static Sharc sharc(OptionValues options) {
        return new Sharc(options.get(RendererOptions.Rt.Sharc.ENABLED),
                options.get(RendererOptions.Rt.Sharc.CACHE_EXPONENT),
                options.get(RendererOptions.Rt.Sharc.UPDATE_TILE_SIZE),
                options.get(RendererOptions.Rt.Sharc.ACCUMULATION_FRAMES),
                options.get(RendererOptions.Rt.Sharc.STALE_FRAMES),
                options.get(RendererOptions.Rt.Sharc.SCENE_SCALE),
                options.get(RendererOptions.Rt.Sharc.GRID_LOGARITHM_BASE),
                options.get(RendererOptions.Rt.Sharc.GRID_LEVEL_BIAS),
                options.get(RendererOptions.Rt.Sharc.RADIANCE_SCALE),
                options.get(RendererOptions.Rt.Sharc.ROUGHNESS_THRESHOLD),
                options.get(RendererOptions.Rt.Sharc.ANTI_FIREFLY),
                options.get(RendererOptions.Rt.Sharc.PRIMARY_SURFACE_DEBUG));
    }
}
