package dev.upscaler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;

/**
 * Central mutable runtime configuration. For now settings are seeded from the existing
 * {@code -Dupscaler.*} system-property surface; later a config file or settings UI can call the same
 * {@code set(...)} methods without chasing parsing/defaults through renderer code.
 */
public final class UpscalerConfig {
    private static final List<RuntimeSetting<?>> SETTINGS = new CopyOnWriteArrayList<>();

    private UpscalerConfig() {
    }

    public static List<RuntimeSetting<?>> settings() {
        return List.copyOf(SETTINGS);
    }

    public static void reloadFromSystemProperties() {
        for (RuntimeSetting<?> setting : SETTINGS) {
            setting.reloadFromSystemProperties();
        }
    }

    public interface RuntimeSetting<T> {
        String key();

        T defaultValue();

        T get();

        void set(T value);

        void reloadFromSystemProperties();
    }

    public static final class BooleanSetting implements RuntimeSetting<Boolean> {
        private final String key;
        private final boolean defaultValue;
        private volatile boolean value;

        private BooleanSetting(String key, boolean defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.value = Boolean.parseBoolean(System.getProperty(key, Boolean.toString(defaultValue)));
            SETTINGS.add(this);
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public Boolean defaultValue() {
            return defaultValue;
        }

        @Override
        public Boolean get() {
            return value;
        }

        public boolean value() {
            return value;
        }

        @Override
        public void set(Boolean value) {
            this.value = value != null ? value : defaultValue;
        }

        @Override
        public void reloadFromSystemProperties() {
            set(Boolean.parseBoolean(System.getProperty(key, Boolean.toString(defaultValue))));
        }
    }

    public static final class IntSetting implements RuntimeSetting<Integer> {
        private final String key;
        private final int defaultValue;
        private final IntUnaryOperator sanitize;
        private volatile int value;

        private IntSetting(String key, int defaultValue, IntUnaryOperator sanitize) {
            this.key = key;
            this.defaultValue = sanitize.applyAsInt(defaultValue);
            this.sanitize = sanitize;
            this.value = readInt(key, this.defaultValue, sanitize);
            SETTINGS.add(this);
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public Integer defaultValue() {
            return defaultValue;
        }

        @Override
        public Integer get() {
            return value;
        }

        public int value() {
            return value;
        }

        @Override
        public void set(Integer value) {
            this.value = sanitize.applyAsInt(value != null ? value : defaultValue);
        }

        @Override
        public void reloadFromSystemProperties() {
            this.value = readInt(key, defaultValue, sanitize);
        }
    }

    public static final class FloatSetting implements RuntimeSetting<Float> {
        private final String key;
        private final float defaultValue;
        private final DoubleUnaryOperator sanitize;
        private volatile float value;

        private FloatSetting(String key, float defaultValue, DoubleUnaryOperator sanitize) {
            this.key = key;
            this.defaultValue = (float) sanitize.applyAsDouble(defaultValue);
            this.sanitize = sanitize;
            this.value = readFloat(key, this.defaultValue, sanitize);
            SETTINGS.add(this);
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public Float defaultValue() {
            return defaultValue;
        }

        @Override
        public Float get() {
            return value;
        }

        public float value() {
            return value;
        }

        @Override
        public void set(Float value) {
            this.value = (float) sanitize.applyAsDouble(value != null ? value : defaultValue);
        }

        @Override
        public void reloadFromSystemProperties() {
            this.value = readFloat(key, defaultValue, sanitize);
        }
    }

    public static final class StringSetting implements RuntimeSetting<String> {
        private final String key;
        private final String defaultValue;
        private final UnaryOperator<String> sanitize;
        private volatile String value;

        private StringSetting(String key, String defaultValue, UnaryOperator<String> sanitize) {
            this.key = key;
            this.defaultValue = sanitize.apply(defaultValue);
            this.sanitize = sanitize;
            this.value = sanitize.apply(System.getProperty(key, defaultValue));
            SETTINGS.add(this);
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public String defaultValue() {
            return defaultValue;
        }

        @Override
        public String get() {
            return value;
        }

        @Override
        public void set(String value) {
            this.value = sanitize.apply(value != null ? value : defaultValue);
        }

        @Override
        public void reloadFromSystemProperties() {
            set(System.getProperty(key, defaultValue));
        }
    }

    public static final class OptionalStringSetting implements RuntimeSetting<String> {
        private final String key;
        private volatile String value;

        private OptionalStringSetting(String key) {
            this.key = key;
            this.value = System.getProperty(key);
            SETTINGS.add(this);
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public String defaultValue() {
            return null;
        }

        @Override
        public String get() {
            return value;
        }

        @Override
        public void set(String value) {
            this.value = value;
        }

        @Override
        public void reloadFromSystemProperties() {
            this.value = System.getProperty(key);
        }
    }

    public static final class Rt {
        public static final BooleanSetting ENABLED = bool("upscaler.rt", true);
        public static final StringSetting OUTPUT_MODE = string("upscaler.rt.output", "rt", Rt::sanitizeOutputMode);
        public static final BooleanSetting CANCEL_VANILLA_WORLD = bool("upscaler.rt.cancelVanillaWorld", false);
        public static final BooleanSetting CANCEL_VANILLA_WORLD_LOG = bool("upscaler.rt.cancelVanillaWorld.log", true);
        public static final BooleanSetting PBR = bool("upscaler.rt.pbr", true);
        public static final IntSetting WORKER_THREADS = intAtLeast("upscaler.rt.workerThreads", defaultWorkerThreads(), 1);

        private Rt() {
        }

        public static final class Composite {
            public static final BooleanSetting ENABLED = bool("upscaler.rt.composite", false);
            public static final IntSetting DEBUG_VIEW = intValue("upscaler.rt.debugView", 0);
            public static final IntSetting SPP = intAtLeast("upscaler.rt.spp", 1, 1);
            public static final BooleanSetting WATER_WAVES = bool("upscaler.rt.waterWaves", true);
            public static final FloatSetting SUN_ANGULAR_RADIUS =
                    radians("upscaler.rt.sunAngularRadius", 0.6f);
            public static final FloatSetting MOON_ANGULAR_RADIUS =
                    radians("upscaler.rt.moonAngularRadius", 1.5f);
            public static final FloatSetting SUN_NOON_SOUTH_TILT =
                    radians("upscaler.rt.sunNoonSouthDeg", 0.0f);
            public static final FloatSetting RENDER_SCALE =
                    clampedFloat("upscaler.rt.renderScale", 0.5f, 0.25f, 1.0f);
            public static final FloatSetting JITTER_SIGN_X = finiteFloat("upscaler.rt.jitterSignX", 1.0f);
            public static final FloatSetting JITTER_SIGN_Y = finiteFloat("upscaler.rt.jitterSignY", -1.0f);

            private Composite() {
            }
        }

        public static final class Terrain {
            public static final IntSetting VIEW_SECTIONS_V = intAtLeast("upscaler.rt.viewSectionsV", 6, 0);
            public static final IntSetting ASYNC_DISPATCH_PER_TICK =
                    intAtLeast("upscaler.rt.asyncDispatchPerTick", 32, 0);
            public static final IntSetting SECTION_RESULTS_PER_TICK =
                    intAtLeast("upscaler.rt.sectionResultsPerTick", 32, 0);
            public static final IntSetting ASYNC_DISPATCH_MOVING_PER_TICK =
                    intAtLeast("upscaler.rt.asyncDispatchMovingPerTick",
                            Math.min(ASYNC_DISPATCH_PER_TICK.value(), 16), 0);
            public static final IntSetting SECTION_RESULTS_MOVING_PER_TICK =
                    intAtLeast("upscaler.rt.sectionResultsMovingPerTick",
                            Math.min(SECTION_RESULTS_PER_TICK.value(), 16), 0);
            public static final IntSetting MAX_INFLIGHT_SECTIONS =
                    intAtLeast("upscaler.rt.maxInflightSections", 192, 0);
            public static final IntSetting SECTION_TABLE_INITIAL_CAPACITY =
                    intAtLeast("upscaler.rt.sectionTableInitialCapacity", 512, 1);
            public static final IntSetting REBASE_DISTANCE_BLOCKS =
                    intAtLeast("upscaler.rt.rebaseDistanceBlocks", 128, 0);

            private Terrain() {
            }
        }

        public static final class Omm {
            public static final BooleanSetting ENABLED = bool("upscaler.rt.omm", true);
            public static final IntSetting SUBDIVISION = clampedInt("upscaler.rt.ommSubdivision", 4, 0, 12);
            public static final BooleanSetting STATS = bool("upscaler.rt.ommStats", false);

            private Omm() {
            }
        }

        public static final class Entities {
            public static final BooleanSetting ENABLED = bool("upscaler.rt.entities", true);
            public static final BooleanSetting PARTICLES_ENABLED = bool("upscaler.rt.particles", true);
            public static final IntSetting MAX_ENTITIES = intAtLeast("upscaler.rt.maxEntities", 1024, 1);
            public static final IntSetting BE_VIEW_CHUNKS = intAtLeast("upscaler.rt.beViewChunks", 8, 0);
            public static final IntSetting BE_BUILDS_PER_FRAME = intAtLeast("upscaler.rt.beBuildsPerFrame", 8, 0);
            public static final BooleanSetting REFIT = bool("upscaler.rt.entityRefit", true);
            public static final IntSetting REFIT_REBUILD_INTERVAL =
                    intAtLeast("upscaler.rt.refitRebuildInterval", 120, 1);
            public static final IntSetting CAPTURE_INITIAL_VERTICES =
                    intAtLeast("upscaler.rt.entityCaptureInitialVertices", 1024, 1);

            private Entities() {
            }

            public static int entityListCapacity() {
                return Math.max(16, MAX_ENTITIES.value());
            }

            public static int entityBufferListCapacity() {
                return (int) Math.min(Integer.MAX_VALUE, (long) entityListCapacity() * 5L);
            }

            public static int entityMapCapacity() {
                return (int) Math.min(Integer.MAX_VALUE, Math.max(16L, (long) MAX_ENTITIES.value() * 2L));
            }
        }

        public static final class EntityTextures {
            public static final IntSetting MAX_TEXTURES = intAtLeast("upscaler.rt.maxEntityTextures", 256, 1);
            public static final BooleanSetting PBR = bool("upscaler.rt.entityPbr", true);

            private EntityTextures() {
            }
        }

        public static final class DlssRr {
            public static final BooleanSetting ENABLED = bool("upscaler.rt.dlssRr", false);
            public static final IntSetting PRESET = intValue("upscaler.rt.dlssRr.preset", 0);
            public static final IntSetting QUALITY = intValue("upscaler.rt.dlssRr.quality", 0);

            private DlssRr() {
            }
        }

        public static final class Exposure {
            public static final StringSetting MODE = string("upscaler.rt.exposure.mode", "auto", Exposure::sanitizeMode);
            public static final FloatSetting FIXED = exposureScale("upscaler.rt.exposure.fixed", 1.1f);
            public static final FloatSetting MANUAL_EV = finiteFloat("upscaler.rt.exposure.manualEv", 0.0f);
            public static final FloatSetting KEY = exposureScale("upscaler.rt.exposure.key", 0.18f);
            public static final FloatSetting MIN_EV = finiteFloat("upscaler.rt.exposure.minEv", -0.5f);
            public static final FloatSetting MAX_EV = finiteFloat("upscaler.rt.exposure.maxEv", 2.5f);
            public static final FloatSetting ADAPT_UP = exposureScale("upscaler.rt.exposure.adaptUp", 0.12f);
            public static final FloatSetting ADAPT_DOWN = exposureScale("upscaler.rt.exposure.adaptDown", 0.35f);

            private Exposure() {
            }

            public static float minEv() {
                return Math.min(MIN_EV.value(), MAX_EV.value());
            }

            public static float maxEv() {
                return Math.max(MIN_EV.value(), MAX_EV.value());
            }

            public static float clampScale(float value) {
                return Math.clamp(value, 1.0e-4f, 1.0e4f);
            }

            private static String sanitizeMode(String value) {
                if ("manual".equalsIgnoreCase(value) || "auto".equalsIgnoreCase(value)) {
                    return value.toLowerCase();
                }
                return "fixed";
            }
        }

        public static final class BufferPool {
            public static final BooleanSetting STATS = bool("upscaler.rt.poolStats", false);

            private BufferPool() {
            }
        }

        private static String sanitizeOutputMode(String value) {
            return "vanilla".equalsIgnoreCase(value) ? "vanilla" : "rt";
        }
    }

    public static final class Ngx {
        public static final OptionalStringSetting PATH = optionalString("upscaler.ngx.path");

        private Ngx() {
        }
    }

    private static BooleanSetting bool(String key, boolean fallback) {
        return new BooleanSetting(key, fallback);
    }

    private static StringSetting string(String key, String fallback, UnaryOperator<String> sanitize) {
        return new StringSetting(key, fallback, sanitize);
    }

    private static OptionalStringSetting optionalString(String key) {
        return new OptionalStringSetting(key);
    }

    private static IntSetting intValue(String key, int fallback) {
        return new IntSetting(key, fallback, v -> v);
    }

    private static IntSetting intAtLeast(String key, int fallback, int min) {
        return new IntSetting(key, fallback, v -> Math.max(min, v));
    }

    private static IntSetting clampedInt(String key, int fallback, int min, int max) {
        return new IntSetting(key, fallback, v -> Math.clamp(v, min, max));
    }

    private static FloatSetting finiteFloat(String key, float fallback) {
        return new FloatSetting(key, fallback, v -> Float.isFinite((float) v) ? v : fallback);
    }

    private static FloatSetting exposureScale(String key, float fallback) {
        return new FloatSetting(key, fallback, v -> Math.clamp(v, 1.0e-4f, 1.0e4f));
    }

    private static FloatSetting clampedFloat(String key, float fallback, float min, float max) {
        return new FloatSetting(key, fallback, v -> Math.clamp(v, min, max));
    }

    private static FloatSetting radians(String key, float fallbackDegrees) {
        return new FloatSetting(key, fallbackDegrees, v -> Math.toRadians(v));
    }

    private static int readInt(String key, int fallback, IntUnaryOperator sanitize) {
        String value = System.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return sanitize.applyAsInt(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float readFloat(String key, float fallback, DoubleUnaryOperator sanitize) {
        String value = System.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return (float) sanitize.applyAsDouble(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int defaultWorkerThreads() {
        return Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 1, 4);
    }
}
