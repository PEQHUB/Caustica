package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DlssdPresetContractTest {
    @Test
    void currentNvidiaPresetContractAcceptsOnlyDefaultDAndE() {
        assertEquals(0, RtDlssRr.normalizePreset(0));
        assertEquals(4, RtDlssRr.normalizePreset(4));
        assertEquals(5, RtDlssRr.normalizePreset(5));
        assertEquals(0, RtDlssRr.normalizePreset(1));
        assertEquals(0, RtDlssRr.normalizePreset(2));
        assertEquals(0, RtDlssRr.normalizePreset(3));
        assertEquals(0, RtDlssRr.normalizePreset(15));
    }

    @Test
    void uiAndNativeBridgeExposeTheSamePresetSet() throws Exception {
        String config = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/CausticaConfig.java"));
        String screen = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/client/CausticaSettingsScreen.java"));
        String bridge = Files.readString(Path.of("native/streamline_bridge/streamline_bridge.cpp"));
        assertTrue(config.contains("Rt.DlssRr.PRESET, Rt.DlssRr.QUALITY"));
        assertTrue(screen.contains("DLSS_RR_PRESETS = List.of(0, 4, 5)"));
        assertTrue(bridge.contains("sl::DLSSDPreset::ePresetD"));
        assertTrue(bridge.contains("sl::DLSSDPreset::ePresetE"));
        assertFalse(bridge.contains("preset >= static_cast<uint32_t>(sl::DLSSDPreset::ePresetD)"));
    }
}
