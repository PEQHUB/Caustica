package dev.comfyfluffy.caustica.rt;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CelestialTimelineContractTest {
    @Test
    void fixedOrbitMatchesVanillaDayNightAnchors() {
        AstronomicalSky.State noon = AstronomicalSky.calculate(0.0f, 0.0f, 0);
        AstronomicalSky.State sunrise = AstronomicalSky.calculate(
                (float)(-Math.PI * 0.5), 0.0f, 0);
        AstronomicalSky.State sunset = AstronomicalSky.calculate(
                (float)(Math.PI * 0.5), 0.0f, 0);
        AstronomicalSky.State midnight = AstronomicalSky.calculate((float)Math.PI, 0.0f, 0);

        assertEquals(Math.cos(Math.toRadians(40.0)), noon.sunDirection()[1], 1.0e-5);
        assertTrue(sunrise.sunDirection()[0] > 0.99f);
        assertEquals(0.0f, sunrise.sunDirection()[1], 1.0e-5f);
        assertTrue(sunset.sunDirection()[0] < -0.99f);
        assertEquals(0.0f, sunset.sunDirection()[1], 1.0e-5f);
        assertTrue(midnight.sunDirection()[1] < 0.0f);
    }

    @Test
    void moonIsAlwaysAntipodalAndRisesImmediatelyAfterSunset() {
        for (int phase = 0; phase < 8; ++phase) {
            AstronomicalSky.State state = AstronomicalSky.calculate(1.2345f, 0.0f, phase);
            assertEquals(-1.0f, dot(state.sunDirection(), state.moonDirection()), 1.0e-5f);
        }
        AstronomicalSky.State afterSunset = AstronomicalSky.calculate(
                (float)(Math.PI * 0.5 + 0.01), 0.0f, 0);
        assertTrue(afterSunset.sunDirection()[1] < 0.0f);
        assertTrue(afterSunset.moonDirection()[1] > 0.0f);
    }

    @Test
    void vanillaMoonPhaseControlsOnlyPresentationIlluminance() {
        AstronomicalSky.State full = AstronomicalSky.calculate(0.0f, 0.0f, 0);
        AstronomicalSky.State quarter = AstronomicalSky.calculate(0.0f, 0.0f, 2);
        AstronomicalSky.State newMoon = AstronomicalSky.calculate(0.0f, 0.0f, 4);
        assertEquals(1.0f, full.moonLitFraction(), 1.0e-5f);
        assertEquals(0.5f, quarter.moonLitFraction(), 1.0e-5f);
        assertEquals(0.0f, newMoon.moonLitFraction(), 1.0e-5f);
        assertEquals(-1.0f, dot(full.sunDirection(), newMoon.moonDirection()), 1.0e-5f);
    }

    @Test
    void vanillaStarAngleDirectlyOwnsSiderealRotation() {
        float starAngle = 1.75f;
        AstronomicalSky.State state = AstronomicalSky.calculate(0.25f, starAngle, 0);
        assertEquals(starAngle, state.siderealAngle(), 1.0e-5f);
    }

    @Test
    void solarAltitudeOwnsDayTwilightAndStars() {
        AstronomicalSky.State noon = AstronomicalSky.calculate(0.0f, 0.0f, 0);
        AstronomicalSky.State midnight = AstronomicalSky.calculate((float)Math.PI, 0.0f, 0);
        assertEquals(1.0f, noon.dayFactor(), 1.0e-5f);
        assertEquals(1.0f, noon.solarEnvelope(), 1.0e-5f);
        assertEquals(0.0f, noon.starBrightness(), 1.0e-5f);
        assertEquals(0.0f, midnight.dayFactor(), 1.0e-5f);
        assertEquals(0.0f, midnight.solarEnvelope(), 1.0e-5f);
        assertEquals(1.0f, midnight.starBrightness(), 1.0e-5f);
    }

    @Test
    void lunarDiscVisibilityCrossesTheHorizonContinuously() {
        float radius = (float)Math.toRadians(0.2727);
        assertEquals(0.0f, AstronomicalSky.lunarDiscHorizonVisibility((float)Math.sin(-radius), radius),
                1.0e-5f);
        assertEquals(0.5f, AstronomicalSky.lunarDiscHorizonVisibility(0.0f, radius), 1.0e-5f);
        assertEquals(1.0f, AstronomicalSky.lunarDiscHorizonVisibility((float)Math.sin(radius), radius),
                1.0e-5f);
        float low = AstronomicalSky.lunarDiscHorizonVisibility((float)Math.sin(-0.5f * radius), radius);
        float high = AstronomicalSky.lunarDiscHorizonVisibility((float)Math.sin(0.5f * radius), radius);
        assertTrue(low > 0.0f && low < 0.5f);
        assertEquals(1.0f, low + high, 1.0e-5f);
    }

    @Test
    void lunarIlluminanceIsCalibratedByPhaseAndVisibility() {
        assertEquals(0.25f, RtComposite.lunarIlluminanceLux(1.0f, 1.0f,
                0.0f, 1.0f, 1.0f, 1.0f), 1.0e-6f);
        assertEquals(0.125f, RtComposite.lunarIlluminanceLux(1.0f, 0.5f,
                0.0f, 1.0f, 1.0f, 1.0f), 1.0e-6f);
        assertEquals(0.0f, RtComposite.lunarIlluminanceLux(1.0f, 0.0f,
                0.0f, 1.0f, 1.0f, 1.0f), 1.0e-6f);
        assertEquals(0.0f, RtComposite.lunarIlluminanceLux(1.0f, 1.0f,
                0.0f, 1.0f, 0.0f, 1.0f), 1.0e-6f);
    }

    @Test
    void minecraftClockAndPhaseAreTheOnlyTimelineInputs() throws Exception {
        String composite = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java"));
        String orbit = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/AstronomicalSky.java"));
        for (String attribute : new String[] {"SUN_ANGLE", "STAR_ANGLE", "MOON_PHASE", "SKY_COLOR"}) {
            assertTrue(composite.contains("EnvironmentAttributes." + attribute));
        }
        for (String seasonalInput : new String[] {"getGameTime()", "DAY_OF_YEAR_OFFSET",
                "ASTRONOMICAL_LATITUDE_DEG", "solarDeclination", "ascendingNode"}) {
            assertFalse(composite.contains(seasonalInput));
            assertFalse(orbit.contains(seasonalInput));
        }
        assertTrue(orbit.contains("new float[] {-sun[0], -sun[1], -sun[2]}"));
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
}
