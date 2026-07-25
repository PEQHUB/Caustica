package dev.comfyfluffy.caustica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import org.junit.jupiter.api.Test;

final class CausticaConfigSchema16Test {
    private static final double EPSILON = 1.0e-6;

    @Test
    void schema14UntouchedEndProfileReachesApprovedDefaults() {
        CommentedConfig config = schema14PlaceholderEndProfile();

        assertTrue(CausticaConfig.migrateLegacySceneConfig(config));

        assertEquals(16, ((Number) config.get("config-version")).intValue());

        assertProfile(config, "end",
                0.04, 0.02, 0.08, 0.00, 0.00, 0.00, 0.0, 2.00, 0.72);
    }

    @Test
    void schema15UntouchedNetherProfileReachesApprovedDefaults() {
        CommentedConfig config = CommentedConfig.inMemory();
        config.set("config-version", 15);
        writeProfile(config, "nether",
                0.300, 0.035, 0.010, 0.025, 0.004, 0.002, 0.0, 1.0, 0.75);

        assertTrue(CausticaConfig.migrateLegacySceneConfig(config));

        assertProfile(config, "nether",
                2.00, 0.26, 0.00, 0.00, 0.00, 0.00, -3.0, 1.00, 8.00);
    }

    @Test
    void editedEndProfileRemainsUserAuthoritative() {
        CommentedConfig config = CommentedConfig.inMemory();
        config.set("config-version", 15);
        writeProfile(config, "end",
                0.125, 0.018, 0.075, 0.004, 0.002, 0.012, 2.0, 1.0, 1.25);

        CausticaConfig.migrateLegacySceneConfig(config);

        assertEquals(0.125, number(config, "composite.sky.end.horizon-r"), EPSILON);
        assertEquals(2.0, number(config, "composite.sky.end.brightness-ev"), EPSILON);
        assertEquals(1.25, number(config, "composite.sky.end.gradient-power"), EPSILON);
    }

    @Test
    void compiledDefaultsMatchApprovedScreenshots() {
        assertEquals(2.00f, CausticaConfig.Rt.Composite.NetherAtmosphere.HORIZON_R.defaultValue(), EPSILON);
        assertEquals(0.26f, CausticaConfig.Rt.Composite.NetherAtmosphere.HORIZON_G.defaultValue(), EPSILON);
        assertEquals(-3.0f, CausticaConfig.Rt.Composite.NetherAtmosphere.BRIGHTNESS_EV.defaultValue(), EPSILON);
        assertEquals(8.00f, CausticaConfig.Rt.Composite.NetherAtmosphere.GRADIENT_POWER.defaultValue(), EPSILON);

        assertEquals(0.04f, CausticaConfig.Rt.Composite.EndAtmosphere.HORIZON_R.defaultValue(), EPSILON);
        assertEquals(0.02f, CausticaConfig.Rt.Composite.EndAtmosphere.HORIZON_G.defaultValue(), EPSILON);
        assertEquals(0.08f, CausticaConfig.Rt.Composite.EndAtmosphere.HORIZON_B.defaultValue(), EPSILON);
        assertEquals(0.0f, CausticaConfig.Rt.Composite.EndAtmosphere.BRIGHTNESS_EV.defaultValue(), EPSILON);
        assertEquals(2.00f, CausticaConfig.Rt.Composite.EndAtmosphere.SATURATION.defaultValue(), EPSILON);
        assertEquals(0.72f, CausticaConfig.Rt.Composite.EndAtmosphere.GRADIENT_POWER.defaultValue(), EPSILON);
    }

    private static CommentedConfig schema14PlaceholderEndProfile() {
        CommentedConfig config = CommentedConfig.inMemory();
        config.set("config-version", 14);
        writeProfile(config, "end",
                0.040, 0.018, 0.075, 0.004, 0.002, 0.012, 0.0, 1.0, 1.25);
        return config;
    }

    private static void assertProfile(CommentedConfig config, String dimension,
                                      double horizonR, double horizonG, double horizonB,
                                      double zenithR, double zenithG, double zenithB,
                                      double brightnessEv, double saturation, double gradientPower) {
        String prefix = "composite.sky." + dimension + ".";
        assertEquals(horizonR, number(config, prefix + "horizon-r"), EPSILON);
        assertEquals(horizonG, number(config, prefix + "horizon-g"), EPSILON);
        assertEquals(horizonB, number(config, prefix + "horizon-b"), EPSILON);
        assertEquals(zenithR, number(config, prefix + "zenith-r"), EPSILON);
        assertEquals(zenithG, number(config, prefix + "zenith-g"), EPSILON);
        assertEquals(zenithB, number(config, prefix + "zenith-b"), EPSILON);
        assertEquals(brightnessEv, number(config, prefix + "brightness-ev"), EPSILON);
        assertEquals(saturation, number(config, prefix + "saturation"), EPSILON);
        assertEquals(gradientPower, number(config, prefix + "gradient-power"), EPSILON);
    }

    private static void writeProfile(CommentedConfig config, String dimension,
                                     double horizonR, double horizonG, double horizonB,
                                     double zenithR, double zenithG, double zenithB,
                                     double brightnessEv, double saturation, double gradientPower) {
        String prefix = "composite.sky." + dimension + ".";
        config.set(prefix + "horizon-r", horizonR);
        config.set(prefix + "horizon-g", horizonG);
        config.set(prefix + "horizon-b", horizonB);
        config.set(prefix + "zenith-r", zenithR);
        config.set(prefix + "zenith-g", zenithG);
        config.set(prefix + "zenith-b", zenithB);
        config.set(prefix + "brightness-ev", brightnessEv);
        config.set(prefix + "saturation", saturation);
        config.set(prefix + "gradient-power", gradientPower);
    }

    private static double number(CommentedConfig config, String path) {
        return ((Number) config.get(path)).doubleValue();
    }
}
