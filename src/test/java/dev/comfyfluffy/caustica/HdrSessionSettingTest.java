package dev.comfyfluffy.caustica;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class HdrSessionSettingTest {
    @Test
    void liveToggleIsARequestForTheNextSurfaceReconciliation() {
        assertTrue(CausticaConfig.Rt.Hdr.enabled() == CausticaConfig.Rt.Hdr.ENABLED.get());
        assertFalse(CausticaConfig.Rt.Hdr.enabled() != CausticaConfig.Rt.Hdr.ENABLED.get());
    }
}
