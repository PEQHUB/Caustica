package dev.comfyfluffy.caustica;

import com.electronwill.nightconfig.core.CommentedConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CausticaConfigSceneMigrationTest {
    @Test
    void obsoleteExposureDefaultsMigrateExactlyOnce() {
        CommentedConfig config = CommentedConfig.inMemory();
        config.set("exposure.min-ev", -1.5);
        config.set("exposure.max-ev", 2.0);
        config.set("exposure.adapt-up", 0.12);
        config.set("exposure.adapt-down", 0.35);

        assertTrue(CausticaConfig.migrateLegacySceneConfig(config));
        assertEquals(CausticaConfig.CONFIG_SCHEMA_VERSION, ((Number) config.get("config-version")).intValue());
        assertEquals(-20.0, ((Number) config.get("exposure.min-ev")).doubleValue());
        assertEquals(12.0, ((Number) config.get("exposure.max-ev")).doubleValue());
        assertEquals(3.0, ((Number) config.get("exposure.adapt-up")).doubleValue());
        assertEquals(2.0, ((Number) config.get("exposure.adapt-down")).doubleValue());
        assertFalse(CausticaConfig.migrateLegacySceneConfig(config));
    }

    @Test
    void schemaTwoSuperhumanDefaultMigratesButCustomCeilingDoesNot() {
        CommentedConfig oldDefault = CommentedConfig.inMemory();
        oldDefault.set("config-version", 2);
        oldDefault.set("exposure.max-ev", 20.0);
        assertTrue(CausticaConfig.migrateLegacySceneConfig(oldDefault));
        assertEquals(12.0, ((Number) oldDefault.get("exposure.max-ev")).doubleValue());

        CommentedConfig custom = CommentedConfig.inMemory();
        custom.set("config-version", 2);
        custom.set("exposure.max-ev", 12.0);
        assertTrue(CausticaConfig.migrateLegacySceneConfig(custom));
        assertEquals(12.0, ((Number) custom.get("exposure.max-ev")).doubleValue());
    }

    @Test
    void userExposureAndTorchChoicesArePreserved() {
        CommentedConfig config = CommentedConfig.inMemory();
        config.set("exposure.min-ev", -6.0);
        config.set("exposure.adapt-up", 1.25);
        config.set("composite.torch-emission-multiplier", 0.1529052);

        assertTrue(CausticaConfig.migrateLegacySceneConfig(config));
        assertEquals(-6.0, ((Number) config.get("exposure.min-ev")).doubleValue());
        assertEquals(1.25, ((Number) config.get("exposure.adapt-up")).doubleValue());
        assertEquals(0.1529052,
                ((Number) config.get("composite.torch-emission-multiplier")).doubleValue());
    }

    @Test
    void legacyCelestialTiltIsDiscardedAtSchemaFour() {
        CommentedConfig config = CommentedConfig.inMemory();
        config.set("config-version", 3);
        config.set("composite.sun-noon-south-tilt-deg", 47.0);
        assertTrue(CausticaConfig.migrateLegacySceneConfig(config));
        assertFalse(config.contains("composite.sun-noon-south-tilt-deg"));
    }

    @Test
    void legacyExposureDialSplitsByModeAtSchemaFive() {
        CommentedConfig auto = CommentedConfig.inMemory();
        auto.set("config-version", 4);
        auto.set("exposure.mode", "auto");
        auto.set("exposure.manual-ev", -1.25);
        assertTrue(CausticaConfig.migrateLegacySceneConfig(auto));
        assertEquals(-1.25, ((Number) auto.get("exposure.compensation-ev")).doubleValue());
        assertFalse(auto.contains("exposure.manual-ev"));

        CommentedConfig manual = CommentedConfig.inMemory();
        manual.set("config-version", 4);
        manual.set("exposure.mode", "manual");
        manual.set("exposure.manual-ev", 2.5);
        assertTrue(CausticaConfig.migrateLegacySceneConfig(manual));
        assertEquals(2.5, ((Number) manual.get("exposure.manual-exposure-ev")).doubleValue());
    }

    @Test
    void legacyExposureDialNeverOverwritesTheNewDestination() {
        CommentedConfig auto = CommentedConfig.inMemory();
        auto.set("config-version", 4);
        auto.set("exposure.mode", "auto");
        auto.set("exposure.manual-ev", -1.25);
        auto.set("exposure.compensation-ev", 0.75);
        assertTrue(CausticaConfig.migrateLegacySceneConfig(auto));
        assertEquals(0.75, ((Number) auto.get("exposure.compensation-ev")).doubleValue());
        assertFalse(auto.contains("exposure.manual-ev"));

        CommentedConfig manual = CommentedConfig.inMemory();
        manual.set("config-version", 4);
        manual.set("exposure.mode", "manual");
        manual.set("exposure.manual-ev", 2.5);
        manual.set("exposure.manual-exposure-ev", -0.5);
        assertTrue(CausticaConfig.migrateLegacySceneConfig(manual));
        assertEquals(-0.5, ((Number) manual.get("exposure.manual-exposure-ev")).doubleValue());
        assertFalse(manual.contains("exposure.manual-ev"));
    }

    @Test
    void schemaFiveMoonBoostMigratesOnlyWhenItIsStillTheOldDefault() {
        CommentedConfig oldDefault = CommentedConfig.inMemory();
        oldDefault.set("config-version", 5);
        oldDefault.set("composite.moonlight-intensity-ev", 3.0);
        assertTrue(CausticaConfig.migrateLegacySceneConfig(oldDefault));
        assertEquals(0.0, ((Number) oldDefault.get("composite.moonlight-intensity-ev")).doubleValue());

        CommentedConfig custom = CommentedConfig.inMemory();
        custom.set("config-version", 5);
        custom.set("composite.moonlight-intensity-ev", 1.5);
        assertTrue(CausticaConfig.migrateLegacySceneConfig(custom));
        assertEquals(1.5, ((Number) custom.get("composite.moonlight-intensity-ev")).doubleValue());
    }

    @Test
    void schemaSevenNormalizesOnlyTheObsoleteTerrainBurstDefaults() {
        CommentedConfig oldDefaults = CommentedConfig.inMemory();
        oldDefaults.set("config-version", 6);
        oldDefaults.set("terrain.async-dispatch-per-tick", 64);
        oldDefaults.set("terrain.section-results-per-tick", 64);
        oldDefaults.set("terrain.max-inflight-sections", 192);
        assertTrue(CausticaConfig.migrateLegacySceneConfig(oldDefaults));
        assertEquals(32, ((Number) oldDefaults.get("terrain.async-dispatch-per-tick")).intValue());
        assertEquals(32, ((Number) oldDefaults.get("terrain.section-results-per-tick")).intValue());
        assertEquals(32, ((Number) oldDefaults.get("terrain.max-inflight-sections")).intValue());

        CommentedConfig custom = CommentedConfig.inMemory();
        custom.set("config-version", 6);
        custom.set("terrain.async-dispatch-per-tick", 12);
        custom.set("terrain.section-results-per-tick", 18);
        custom.set("terrain.max-inflight-sections", 48);
        assertTrue(CausticaConfig.migrateLegacySceneConfig(custom));
        assertEquals(12, ((Number) custom.get("terrain.async-dispatch-per-tick")).intValue());
        assertEquals(18, ((Number) custom.get("terrain.section-results-per-tick")).intValue());
        assertEquals(48, ((Number) custom.get("terrain.max-inflight-sections")).intValue());
    }

    @Test
    void schemaEightDerivesInputResolutionFromTheSavedDlssPreset() {
        CommentedConfig quality = CommentedConfig.inMemory();
        quality.set("config-version", 7);
        quality.set("dlss-rr.quality", 2);
        assertTrue(CausticaConfig.migrateLegacySceneConfig(quality));
        assertEquals(66, ((Number) quality.get("dlss-rr.input-scale-percent")).intValue());

        CommentedConfig custom = CommentedConfig.inMemory();
        custom.set("config-version", 7);
        custom.set("dlss-rr.quality", 2);
        custom.set("dlss-rr.input-scale-percent", 20);
        assertTrue(CausticaConfig.migrateLegacySceneConfig(custom));
        assertEquals(20, ((Number) custom.get("dlss-rr.input-scale-percent")).intValue());
    }

    @Test
    void schemaNineCollapsesLegacyCompoundedOutputAndRemovesFast() {
        CommentedConfig config = CommentedConfig.inMemory();
        config.set("config-version", 8);
        config.set("output-scale.percent", 80);
        config.set("output-scale.fast-percent", 125);
        assertTrue(CausticaConfig.migrateLegacySceneConfig(config));
        assertEquals(100, ((Number) config.get("output-scale.percent")).intValue());
        assertFalse(config.contains("output-scale.fast-percent"));
    }

    @Test
    void schemaTenMigratesLegacyFrameGenerationEnabledToMode() {
        CommentedConfig disabled = CommentedConfig.inMemory();
        disabled.set("config-version", 9);
        disabled.set("frame-generation.enabled", false);
        assertTrue(CausticaConfig.migrateLegacySceneConfig(disabled));
        assertEquals("off", disabled.get("frame-generation.mode"));

        CommentedConfig enabled = CommentedConfig.inMemory();
        enabled.set("config-version", 9);
        enabled.set("frame-generation.enabled", true);
        assertTrue(CausticaConfig.migrateLegacySceneConfig(enabled));
        assertEquals("fixed", enabled.get("frame-generation.mode"));
    }

    @Test
    void schemaTenDoesNotOverwriteExplicitModeWhenEnabledExists() {
        CommentedConfig config = CommentedConfig.inMemory();
        config.set("config-version", 9);
        config.set("frame-generation.enabled", false);
        config.set("frame-generation.mode", "fixed");
        assertTrue(CausticaConfig.migrateLegacySceneConfig(config));
        assertEquals("fixed", config.get("frame-generation.mode"));
    }
}
