package dev.comfyfluffy.caustica;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void registersToneMappingSettingsForConfigRoundTrips() {
        CausticaConfig.ensureRegistered();
        assertTrue(hasSetting("caustica.rt.sdr.toneMapper"));
        assertTrue(hasSetting("caustica.rt.hdr.toneMapper"));
    }

    @Test
    void analyticalToneControlsRejectNonfiniteValues() {
        var sdrContrast = CausticaConfig.Rt.Sdr.AGX_CONTRAST;
        var hdrPaperWhite = CausticaConfig.Rt.Hdr.PAPER_WHITE_NITS;
        float previousSdrContrast = sdrContrast.value();
        float previousHdrPaperWhite = hdrPaperWhite.value();
        try {
            sdrContrast.set(Float.NaN);
            hdrPaperWhite.set(Float.POSITIVE_INFINITY);
            assertEquals(sdrContrast.defaultValue(), sdrContrast.value());
            assertEquals(hdrPaperWhite.defaultValue(), hdrPaperWhite.value());
        } finally {
            sdrContrast.set(previousSdrContrast);
            hdrPaperWhite.set(previousHdrPaperWhite);
        }
    }

    @Test
    void risCandidatesPreserveTheUpstreamDefaultAndClampAllRuntimeInputs() {
        CausticaConfig.IntSetting setting = CausticaConfig.Rt.Lights.RIS_CANDIDATES;
        int previous = setting.value();
        try {
            assertEquals(8, setting.defaultValue());
            setting.set(-1);
            assertEquals(0, setting.value());
            setting.set(64);
            assertEquals(32, setting.value());
        } finally {
            setting.set(previous);
        }
    }

    @Test
    void samplingDefaultsMatchTheRendererProfile() {
        assertEquals(8, CausticaConfig.Rt.Lights.RIS_CANDIDATES.defaultValue());
        assertEquals(4, CausticaConfig.Rt.Composite.MAX_BOUNCES.defaultValue());
    }

    @Test
    void registersSamplingSettingsForConfigRoundTrips() {
        CausticaConfig.ensureRegistered();
        assertTrue(hasSetting("caustica.rt.risCandidates"));
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

    private static boolean hasSetting(String key) {
        return CausticaConfig.settings().stream().anyMatch(setting -> setting.key().equals(key));
    }
}
