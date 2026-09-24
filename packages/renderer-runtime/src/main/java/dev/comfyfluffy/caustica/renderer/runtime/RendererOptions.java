package dev.comfyfluffy.caustica.renderer.runtime;

import dev.comfyfluffy.caustica.settings.Option;
import java.util.List;
import java.util.stream.Stream;

/** Renderer-owned option declarations; registration and persistence belong to the host. */
public final class RendererOptions {
    private RendererOptions() { }

    public static List<Option<?>> settings() {
        return Stream.<Option<?>>concat(Stream.of(
                Rt.Composite.DEBUG_VIEW, Rt.Composite.MAX_BOUNCES,
                Rt.Composite.JITTER_SIGN_X, Rt.Composite.JITTER_SIGN_Y,
                Rt.DlssRr.PRESET, Rt.DlssRr.QUALITY, Rt.DlssSr.PRESET, Rt.DlssSr.QUALITY,
                Rt.Denoising.ROUTE, Rt.Denoising.METHOD, Rt.Fg.ENABLED,
                Rt.Reflex.ENABLED, Rt.Reflex.LOW_LATENCY_BOOST, Rt.Reflex.MINIMUM_INTERVAL_US,
                Rt.Exposure.MODE, Rt.Exposure.MANUAL_EV, Rt.Exposure.KEY,
                Rt.Exposure.ADAPT_DARKEN, Rt.Exposure.ADAPT_BRIGHTEN,
                Rt.Exposure.LOW_PERCENTILE, Rt.Exposure.HIGH_PERCENTILE, Rt.Exposure.STRIDE,
                Rt.Exposure.CENTER_WEIGHT_SIGMA, Rt.Exposure.CENTER_WEIGHT_FLOOR,
                Rt.Exposure.ENVIRONMENT_WEIGHT_CAP, Rt.Exposure.EMISSIVE_WEIGHT_CAP, Rt.Exposure.PRE_EXPOSURE,
                Rt.Tonemap.GAMMA, Rt.Screenshots.EXR_ENABLED,
                Rt.Hdr.ENABLED, Rt.Hdr.UI_NITS, Rt.Hdr.PEAK_NITS), Rt.Sharc.OPTIONS.stream()).toList();
    }

    public static final class Rt {
        private Rt() { }
        public static final class Composite {
            private Composite() { }
            public static final Option<Integer> DEBUG_VIEW = clampedInt("caustica.rt.debugView", "composite.debug-view", 0, 0, 15).inGroup("debug");
            public static final Option<Integer> MAX_BOUNCES = clampedInt("caustica.rt.maxBounces", "composite.max-bounces", 4, 2, 8).inGroup("quality");
            public static final Option<Float> JITTER_SIGN_X = finiteFloat("caustica.rt.jitterSignX", "composite.jitter-sign-x", 1.0f);
            public static final Option<Float> JITTER_SIGN_Y = finiteFloat("caustica.rt.jitterSignY", "composite.jitter-sign-y", -1.0f);
        }

        public static final class DlssRr {
            private DlssRr() { }
            public static final List<Integer> PRESET_STEPS = List.of(0, 4, 5);
            public static final Option<Integer> PRESET = intChoice("caustica.rt.dlssRr.preset", "dlss-rr.preset", 5, PRESET_STEPS);
            public static final List<Integer> QUALITY_STEPS = List.of(3, 0, 1, 2, 5);
            public static final Option<Integer> QUALITY = intChoice("caustica.rt.dlssRr.quality", "dlss-rr.quality", 1, QUALITY_STEPS).inGroup("upscaling");
        }

        public static final class DlssSr {
            private DlssSr() { }
            public static final Option<Integer> PRESET = intChoice("caustica.rt.dlssSr.preset", "dlss-sr.preset", 0, List.of(0, 10, 11, 12, 13));
            public static final List<Integer> QUALITY_STEPS = List.of(3, 0, 1, 2, 5);
            public static final Option<Integer> QUALITY = intChoice("caustica.rt.dlssSr.quality", "dlss-sr.quality", 2, QUALITY_STEPS).inGroup("upscaling");
        }

        public static final class Denoising {
            private Denoising() { }
            public static final Option<String> ROUTE = stringChoice("caustica.rt.denoisingRoute", "denoising.route", "ray_reconstruction", List.of("ray_reconstruction", "temporal_denoiser", "raw")).inGroup("upscaling");
            public static final Option<String> METHOD = stringChoice("caustica.rt.denoisingMethod", "denoising.method", "reblur", List.of("relax", "reblur")).inGroup("upscaling");
        }

        public static final class Fg {
            private Fg() { }
            public static final Option<Boolean> ENABLED = bool("caustica.rt.fg", "frame-generation.enabled", false).inGroup("upscaling");
        }

        public static final class Reflex {
            private Reflex() { }
            public static final Option<Boolean> ENABLED = bool("caustica.rt.reflex", "reflex.enabled", false).inGroup("upscaling");
            public static final Option<Boolean> LOW_LATENCY_BOOST = bool("caustica.rt.reflex.boost", "reflex.low-latency-boost", false);
            public static final Option<Integer> MINIMUM_INTERVAL_US = intAtLeast("caustica.rt.reflex.minIntervalUs", "reflex.minimum-interval-us", 0, 0);
        }

        public static final class Exposure {
            private Exposure() { }
            public static final List<String> MODES = List.of("auto", "manual");
            public static final Option<String> MODE = stringChoice("caustica.rt.exposure.mode", "exposure.mode", "auto", MODES).inGroup("exposure");
            public static final Option<Float> MANUAL_EV = clampedFloat("caustica.rt.exposure.manualEv", "exposure.manual-ev", 0.0f, -15.0f, 15.0f).inGroup("exposure");
            public static final Option<Float> KEY = exposureScale("caustica.rt.exposure.key", "exposure.key", 0.18f);
            public static final Option<Float> ADAPT_DARKEN = exposureScale("caustica.rt.exposure.adaptDarken", "exposure.adapt-darken", 2.0f);
            public static final Option<Float> ADAPT_BRIGHTEN = exposureScale("caustica.rt.exposure.adaptBrighten", "exposure.adapt-brighten", 0.4f);
            public static final Option<Float> LOW_PERCENTILE = clampedFloat("caustica.rt.exposure.lowPercentile", "exposure.low-percentile", 0.50f, 0.0f, 1.0f);
            public static final Option<Float> HIGH_PERCENTILE = clampedFloat("caustica.rt.exposure.highPercentile", "exposure.high-percentile", 0.95f, 0.0f, 1.0f);
            public static final Option<Integer> STRIDE = clampedInt("caustica.rt.exposure.stride", "exposure.stride", 2, 1, 8);
            public static final Option<Float> CENTER_WEIGHT_SIGMA = clampedFloat("caustica.rt.exposure.centerWeightSigma", "exposure.center-weight-sigma", 0.35f, 0.01f, 2.0f);
            public static final Option<Float> CENTER_WEIGHT_FLOOR = clampedFloat("caustica.rt.exposure.centerWeightFloor", "exposure.center-weight-floor", 0.15f, 0.0f, 1.0f);
            public static final Option<Float> ENVIRONMENT_WEIGHT_CAP = clampedFloat("caustica.rt.exposure.environmentWeightCap", "exposure.environment-weight-cap", 0.25f, 0.0f, 1.0f);
            public static final Option<Float> EMISSIVE_WEIGHT_CAP = clampedFloat("caustica.rt.exposure.emissiveWeightCap", "exposure.emissive-weight-cap", 0.10f, 0.0f, 1.0f);
            public static final Option<Boolean> PRE_EXPOSURE = bool("caustica.rt.exposure.preExposure", "exposure.pre-exposure", true);
        }

        public static final class Tonemap {
            private Tonemap() { }
            public static final Option<Float> GAMMA = clampedFloat("caustica.rt.tonemap.gamma", "tonemap.gamma", 1.0f, 0.1f, 5.0f).inGroup("look").sliderRange(0.5f, 1.5f);
        }

        public static final class Screenshots {
            private Screenshots() { }
            public static final Option<Boolean> EXR_ENABLED = bool("caustica.rt.screenshots.exr", "screenshots.exr-enabled", false).inGroup("debug");
        }

        public static final class Hdr {
            private Hdr() { }
            public static final Option<Boolean> ENABLED = bool("caustica.rt.hdr", "hdr.enabled", false).inGroup("output");
            public static final Option<Float> UI_NITS = clampedFloat("caustica.rt.hdr.uiNits", "hdr.ui-nits", 200.0f, 80.0f, 500.0f).inGroup("output");
            public static final List<Integer> PEAK_NITS_STEPS = List.of(500, 1000, 2000, 4000);
            public static final Option<Integer> PEAK_NITS = intChoice("caustica.rt.hdr.peakNits", "hdr.peak-nits", 1000, PEAK_NITS_STEPS).inGroup("output");
        }

        /** The SHaRC radiance cache; it runs only in a SHaRC SDK build on a device with its features. */
        public static final class Sharc {
            private Sharc() { }
            public static final Option<Boolean> ENABLED = bool("caustica.rt.sharc.enabled", "sharc.enabled", true);
            /** Entries per cache table as a power of two. */
            public static final Option<Integer> CACHE_EXPONENT = clampedInt("caustica.rt.sharc.cacheExponent", "sharc.cache-exponent", 22, 16, 23);
            public static final Option<Integer> UPDATE_TILE_SIZE = clampedInt("caustica.rt.sharc.updateTileSize", "sharc.update-tile-size", 3, 2, 64);
            public static final Option<Integer> ACCUMULATION_FRAMES = clampedInt("caustica.rt.sharc.accumulationFrames", "sharc.accumulation-frames", 384, 1, 1024);
            public static final Option<Integer> STALE_FRAMES = clampedInt("caustica.rt.sharc.staleFrames", "sharc.stale-frames", 128, 8, 1024);
            public static final Option<Float> SCENE_SCALE = clampedFloat("caustica.rt.sharc.sceneScale", "sharc.scene-scale", 32.0f, 1.0f, 100.0f);
            public static final Option<Float> GRID_LOGARITHM_BASE = clampedFloat("caustica.rt.sharc.gridLogarithmBase", "sharc.grid-logarithm-base", 3.0f, 1.01f, 16.0f);
            public static final Option<Float> GRID_LEVEL_BIAS = clampedFloat("caustica.rt.sharc.gridLevelBias", "sharc.grid-level-bias", 0.0f, -16.0f, 16.0f);
            /** Accumulator quantization steps per unit of pre-exposed radiance. */
            public static final Option<Float> RADIANCE_SCALE = clampedFloat("caustica.rt.sharc.radianceScale", "sharc.radiance-scale", 1000.0f, 50.0f, 1000.0f);
            /** Perceptual roughness a surface must exceed to own cache entries. */
            public static final Option<Float> ROUGHNESS_THRESHOLD = clampedFloat("caustica.rt.sharc.roughnessThreshold", "sharc.roughness-threshold", 0.0f, 0.0f, 1.0f);
            public static final Option<Boolean> ANTI_FIREFLY = bool("caustica.rt.sharc.antiFirefly", "sharc.anti-firefly", true);
            /** Primary surfaces query the cache too, showing its content directly on screen. */
            public static final Option<Boolean> PRIMARY_SURFACE_DEBUG = bool("caustica.rt.sharc.primarySurfaceDebug", "sharc.primary-surface-debug", false);
            public static final List<Option<?>> OPTIONS = List.of(ENABLED, CACHE_EXPONENT, UPDATE_TILE_SIZE,
                    ACCUMULATION_FRAMES, STALE_FRAMES, SCENE_SCALE, GRID_LOGARITHM_BASE, GRID_LEVEL_BIAS,
                    RADIANCE_SCALE, ROUGHNESS_THRESHOLD, ANTI_FIREFLY, PRIMARY_SURFACE_DEBUG);
        }
    }

    private static Option<Boolean> bool(String key, String path, boolean fallback) {
        return Option.bool(path, fallback).storage(path, key);
    }
    private static Option<Integer> intAtLeast(String key, String path, int fallback, int min) {
        return clampedInt(key, path, fallback, min, Integer.MAX_VALUE);
    }
    private static Option<Integer> clampedInt(String key, String path, int fallback, int min, int max) {
        return Option.integer(path, min, max, fallback).storage(path, key);
    }
    private static Option<Float> finiteFloat(String key, String path, float fallback) {
        return clampedFloat(key, path, fallback, -Float.MAX_VALUE, Float.MAX_VALUE);
    }
    private static Option<Float> clampedFloat(String key, String path, float fallback, float min, float max) {
        return Option.range(path, min, max, fallback).storage(path, key);
    }
    private static Option<Integer> intChoice(String key, String path, int fallback, List<Integer> choices) {
        return Option.intChoice(path, fallback, choices).storage(path, key);
    }
    private static Option<Float> exposureScale(String key, String path, float fallback) {
        return clampedFloat(key, path, fallback, 1.0e-4f, 1.0e4f);
    }
    private static Option<String> stringChoice(String key, String path, String fallback, List<String> choices) {
        return Option.stringChoice(path, fallback, choices).storage(path, key);
    }
}
