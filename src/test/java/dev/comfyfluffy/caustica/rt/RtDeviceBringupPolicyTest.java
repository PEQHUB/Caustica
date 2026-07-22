package dev.comfyfluffy.caustica.rt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RtDeviceBringupPolicyTest {
    @Test
    void portableNvidiaAutoSuppressesOmm() {
        assertFalse(RtDeviceBringup.effectiveOmmRequested(
                true, RtDeviceBringup.SerBackend.NONE, true, "auto"));
    }

    @Test
    void serNvidiaAutoKeepsOmmAvailable() {
        assertTrue(RtDeviceBringup.effectiveOmmRequested(
                true, RtDeviceBringup.SerBackend.EXT, true, "auto"));
    }

    @Test
    void explicitOmmOnOverridesPortableProfile() {
        assertTrue(RtDeviceBringup.effectiveOmmRequested(
                true, RtDeviceBringup.SerBackend.NONE, true, "on"));
    }

    @Test
    void explicitOmmOffWinsOnModernNvidia() {
        assertFalse(RtDeviceBringup.effectiveOmmRequested(
                true, RtDeviceBringup.SerBackend.EXT, true, "off"));
    }

    @Test
    void nonNvidiaAutoKeepsSupportedOmmAvailable() {
        assertTrue(RtDeviceBringup.effectiveOmmRequested(
                false, RtDeviceBringup.SerBackend.NONE, true, "auto"));
    }

    @Test
    void userOmmGateDisablesEveryPolicy() {
        assertFalse(RtDeviceBringup.effectiveOmmRequested(
                false, RtDeviceBringup.SerBackend.EXT, false, "on"));
    }

    @Test
    void missingSerDoesNotMakeRequiredRtUnsupported() {
        assertTrue(new RtDeviceBringup.FeatureSupport(
                List.of(), RtDeviceBringup.SerBackend.NONE, false, false).supportsRt());
    }

    @Test
    void missingRequiredFeatureStillMakesRtUnsupported() {
        assertFalse(new RtDeviceBringup.FeatureSupport(
                List.of("rayQuery"), RtDeviceBringup.SerBackend.NONE, false, false).supportsRt());
    }
}
