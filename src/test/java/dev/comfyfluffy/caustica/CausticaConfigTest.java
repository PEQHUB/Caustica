package dev.comfyfluffy.caustica;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CausticaConfigTest {
    @Test
    void peakNitsUsesThe50NitGrid() {
        CausticaConfig.IntSetting setting = CausticaConfig.Rt.Hdr.PEAK_NITS;
        int previous = setting.value();
        try {
            setting.set(1050);
            assertEquals(1050, setting.value());

            setting.set(1055);
            assertEquals(1050, setting.value());
        } finally {
            setting.set(previous);
        }
    }

    @Test
    void acesUsesTheNearestPackagedPeak() {
        assertEquals(500, CausticaConfig.Rt.Hdr.nearestAcesLutNits(500));
        assertEquals(500, CausticaConfig.Rt.Hdr.nearestAcesLutNits(750));
        assertEquals(1000, CausticaConfig.Rt.Hdr.nearestAcesLutNits(900));
        assertEquals(4000, CausticaConfig.Rt.Hdr.nearestAcesLutNits(5000));
    }

    @Test
    void risCandidatesDefaultToDisabledAndClampAllRuntimeInputs() {
        CausticaConfig.IntSetting setting = CausticaConfig.Rt.Lights.RIS_CANDIDATES;
        int previous = setting.value();
        try {
            assertEquals(0, setting.defaultValue());
            setting.set(-1);
            assertEquals(0, setting.value());
            setting.set(33);
            assertEquals(32, setting.value());
            setting.set(32);
            assertEquals(32, setting.value());
        } finally {
            setting.set(previous);
        }
    }

    @Test
    void defaultsMatchRequestedRendererProfile() {
        assertEquals(8, CausticaConfig.Rt.Composite.MAX_BOUNCES.defaultValue());
        assertEquals(0, CausticaConfig.Rt.Lights.RIS_CANDIDATES.defaultValue());
        assertEquals(1, CausticaConfig.Rt.Fg.MULTI_FRAME_COUNT.defaultValue());
        assertFalse(CausticaConfig.Rt.Fg.ENABLED.defaultValue());
        assertEquals(0, CausticaConfig.Rt.DlssRr.PRESET.defaultValue());
        assertTrue(CausticaConfig.Rt.Terrain.BLAS_COMPACTION.defaultValue());
        assertFalse(CausticaConfig.Rt.Omm.STATS.defaultValue());
        assertEquals(64, CausticaConfig.Rt.Entities.BE_BUILDS_PER_FRAME.defaultValue());
    }

    @Test
    void defaultsProfileBreakerLeavesVersion17AndNewerProfilesAlone() {
        assertEquals(17, CausticaConfig.DEFAULTS_PROFILE_VERSION);
        assertTrue(CausticaConfig.shouldResetToDefaults(null));
        assertTrue(CausticaConfig.shouldResetToDefaults(16));
        assertFalse(CausticaConfig.shouldResetToDefaults(17));
        assertFalse(CausticaConfig.shouldResetToDefaults(18L));
        assertTrue(CausticaConfig.shouldResetToDefaults("17"));
        assertEquals(17, CausticaConfig.profileVersionForSave(null));
        assertEquals(17, CausticaConfig.profileVersionForSave(16));
        assertEquals(18, CausticaConfig.profileVersionForSave(18L));
    }

    @Test
    void registersCompleteLightOverlayDiagnosticsGroups() {
        CausticaConfig.ensureRegistered();
        assertTrue(hasSetting("caustica.rt.risCandidates"));
        assertTrue(hasSetting("caustica.rt.blockOutline"));
        assertTrue(hasSetting("caustica.rt.heavyCrashDiagnostics"));
    }

    private static boolean hasSetting(String key) {
        return CausticaConfig.settings().stream().anyMatch(setting -> setting.key().equals(key));
    }

    @Test
    void paperWhiteCannotExceedTheSelectedPeak() {
        var paperWhite = CausticaConfig.Rt.Hdr.PAPER_WHITE_NITS;
        var peak = CausticaConfig.Rt.Hdr.PEAK_NITS;
        float previousPaperWhite = paperWhite.value();
        int previousPeak = peak.value();
        try {
            paperWhite.set(200.0f);
            peak.set(50);
            assertEquals(50.0f, CausticaConfig.Rt.Hdr.paperWhiteNits());
            assertEquals(1.0f, CausticaConfig.Rt.Hdr.headroom());
        } finally {
            paperWhite.set(previousPaperWhite);
            peak.set(previousPeak);
        }
    }
}
