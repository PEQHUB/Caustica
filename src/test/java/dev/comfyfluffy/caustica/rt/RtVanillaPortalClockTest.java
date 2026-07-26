package dev.comfyfluffy.caustica.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class RtVanillaPortalClockTest {
    @Test
    void matchesVanillaDayFractionAndPartialTick() {
        assertEquals(0.0f, RtVanillaPortalClock.portalGameTime(0L, 0.0f));
        assertEquals(0.5f, RtVanillaPortalClock.portalGameTime(12_000L, 0.0f));
        assertEquals(12_000.5f / 24_000.0f,
                RtVanillaPortalClock.portalGameTime(12_000L, 0.5f));
    }

    @Test
    void wrapsLargeAndNegativeTickValuesBeforeFloatConversion() {
        assertEquals(0.0f, RtVanillaPortalClock.portalGameTime(24_000L * 10_000_000L, 0.0f));
        assertEquals(23_999.5f / 24_000.0f,
                RtVanillaPortalClock.portalGameTime(-1L, 0.5f));
    }
}
