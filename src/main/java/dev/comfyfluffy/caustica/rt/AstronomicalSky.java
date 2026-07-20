package dev.comfyfluffy.caustica.rt;

/**
 * A single fixed celestial loop driven entirely by Minecraft's interpolated sky clock.
 *
 * <p>The loop uses an equinox-style tilted circle at a fixed presentation latitude. With zero
 * declination the Sun crosses the horizon exactly one quarter-turn before and after noon, so the
 * physical-looking arc retains vanilla sunrise, noon, sunset and midnight without seasons or a time
 * warp. The Moon is the exact antipode of the Sun and therefore rises as the Sun sets. Minecraft's
 * eight-day moon phase remains a presentation/illuminance input rather than changing that orbit.</p>
 */
final class AstronomicalSky {
    private static final double TAU = Math.PI * 2.0;
    private static final double FIXED_LATITUDE = Math.toRadians(40.0);

    private AstronomicalSky() {
    }

    record State(float[] sunDirection, float[] moonDirection, float[] celestialPole,
                 float solarHourAngle, float lunarHourAngle, float siderealAngle,
                 float moonLitFraction, float dayFactor, float twilightFactor,
                 float solarEnvelope, float starBrightness) {
    }

    static State calculate(float solarClockAngle, float starClockAngle, int moonPhase) {
        double solarHourAngle = wrapPi(solarClockAngle);
        float[] sun = horizontalDirection(FIXED_LATITUDE, solarHourAngle);
        float[] moon = new float[] {-sun[0], -sun[1], -sun[2]};
        float[] pole = new float[] {0.0f, (float)Math.sin(FIXED_LATITUDE),
                (float)Math.cos(FIXED_LATITUDE)};

        float dayFactor = smoothstep(sineDegrees(-0.2666), sineDegrees(0.2666), sun[1]);
        float civilTwilight = smoothstep(sineDegrees(-6.0), sineDegrees(-0.2666), sun[1]);
        float twilightFactor = civilTwilight * (1.0f - dayFactor);
        float solarEnvelope = Math.max(dayFactor, civilTwilight);
        float starBrightness = 1.0f - smoothstep(sineDegrees(-12.0), sineDegrees(-6.0), sun[1]);
        double phaseTurns = Math.floorMod(moonPhase, 8) / 8.0;
        float moonLitFraction = (float)(0.5 * (1.0 + Math.cos(TAU * phaseTurns)));

        return new State(sun, moon, pole, (float)solarHourAngle,
                (float)wrapPi(solarHourAngle + Math.PI), (float)wrapPi(starClockAngle),
                moonLitFraction, dayFactor, twilightFactor, solarEnvelope, starBrightness);
    }

    private static float[] horizontalDirection(double latitude, double hourAngle) {
        double east = -Math.sin(hourAngle);
        double up = Math.cos(latitude) * Math.cos(hourAngle);
        double north = -Math.sin(latitude) * Math.cos(hourAngle);
        return new float[] {(float)east, (float)up, (float)north};
    }

    private static float sineDegrees(double degrees) {
        return (float)Math.sin(Math.toRadians(degrees));
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Math.clamp((value - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    /** Fraction of a circular lunar disc above the geometric horizon. */
    static float lunarDiscHorizonVisibility(float moonDirectionY, float angularRadius) {
        double altitude = Math.asin(Math.clamp(moonDirectionY, -1.0f, 1.0f));
        double radius = Math.max(angularRadius, 1.0e-6f);
        double normalizedAltitude = Math.clamp(altitude / radius, -1.0, 1.0);
        if (normalizedAltitude <= -1.0) return 0.0f;
        if (normalizedAltitude >= 1.0) return 1.0f;
        return (float)((Math.acos(-normalizedAltitude)
                + normalizedAltitude * Math.sqrt(Math.max(1.0 - normalizedAltitude * normalizedAltitude, 0.0)))
                / Math.PI);
    }

    private static double wrapPi(double angle) {
        double wrapped = angle % TAU;
        if (wrapped <= -Math.PI) wrapped += TAU;
        if (wrapped > Math.PI) wrapped -= TAU;
        return wrapped;
    }
}
