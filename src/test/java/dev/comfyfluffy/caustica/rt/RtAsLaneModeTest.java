package dev.comfyfluffy.caustica.rt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RtAsLaneModeTest {
    @Test
    void autoUsesSerializedMode() {
        assertEquals(RtDeviceBringup.AccelerationStructureLaneMode.SERIALIZED,
                RtDeviceBringup.requestedAsLaneMode("auto"));
    }

    @Test
    void serializedUsesSerializedMode() {
        assertEquals(RtDeviceBringup.AccelerationStructureLaneMode.SERIALIZED,
                RtDeviceBringup.requestedAsLaneMode("serialized"));
    }

    @Test
    void overlapIsExplicitlyAvailable() {
        assertEquals(RtDeviceBringup.AccelerationStructureLaneMode.OVERLAP,
                RtDeviceBringup.requestedAsLaneMode("overlap"));
    }

    @Test
    void invalidModeFallsBackToSerialized() {
        assertEquals(RtDeviceBringup.AccelerationStructureLaneMode.SERIALIZED,
                RtDeviceBringup.requestedAsLaneMode("unknown"));
    }
}
