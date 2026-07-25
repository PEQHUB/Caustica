package dev.comfyfluffy.caustica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import org.junit.jupiter.api.Test;

final class CausticaConfigSchema15Test {
    @Test
    void untouchedEndPlaceholderReceivesVisibleBrightness() {
        CommentedConfig config = schema14PlaceholderEndProfile();

        assertTrue(CausticaConfig.migrateLegacySceneConfig(config));

        assertEquals(15, ((Number) config.get("config-version")).intValue());
        assertEquals(2.0,
                ((Number) config.get("composite.sky.end.brightness-ev")).doubleValue(),
                1.0e-6);
    }

    @Test
    void editedEndProfilePreservesUserBrightness() {
        CommentedConfig config = schema14PlaceholderEndProfile();
        config.set("composite.sky.end.horizon-r", 0.125);

        CausticaConfig.migrateLegacySceneConfig(config);

        assertEquals(0.0,
                ((Number) config.get("composite.sky.end.brightness-ev")).doubleValue(),
                1.0e-6);
    }

    @Test
    void freshEndDefaultIsVisible() {
        assertEquals(2.0f,
                CausticaConfig.Rt.Composite.EndAtmosphere.BRIGHTNESS_EV.defaultValue(),
                1.0e-6f);
    }

    private static CommentedConfig schema14PlaceholderEndProfile() {
        CommentedConfig config = CommentedConfig.inMemory();
        config.set("config-version", 14);
        config.set("composite.sky.end.horizon-r", 0.040);
        config.set("composite.sky.end.horizon-g", 0.018);
        config.set("composite.sky.end.horizon-b", 0.075);
        config.set("composite.sky.end.zenith-r", 0.004);
        config.set("composite.sky.end.zenith-g", 0.002);
        config.set("composite.sky.end.zenith-b", 0.012);
        config.set("composite.sky.end.brightness-ev", 0.0);
        config.set("composite.sky.end.saturation", 1.0);
        config.set("composite.sky.end.gradient-power", 1.25);
        return config;
    }
}
