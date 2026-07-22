package dev.comfyfluffy.caustica.rt.pipeline;

import dev.comfyfluffy.caustica.CausticaConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class OmmDeviceGateContractTest {
    @Test
    void ommConfigurationDefaultsAreExplicit() {
        assertTrue(CausticaConfig.Rt.Omm.ENABLED.defaultValue());
        assertEquals("auto", CausticaConfig.Rt.Compatibility.OMM_MODE.defaultValue());
    }
}
