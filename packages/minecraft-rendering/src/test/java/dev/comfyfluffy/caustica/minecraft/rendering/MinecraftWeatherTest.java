package dev.comfyfluffy.caustica.minecraft.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MinecraftWeatherTest {
    @Test void clearWeatherKeepsAllLight() {
        var clear = new MinecraftWeather(0, 0);

        assertEquals(1f, clear.daylightFactor());
        assertEquals(1f, clear.beamTransmittance());
    }

    @Test void fullRainKeepsElevenSixteenthsOfDaylightAndBlocksBothBeams() {
        var rain = new MinecraftWeather(1, 0);

        assertEquals(11f / 16f, rain.daylightFactor());
        assertEquals(0f, rain.beamTransmittance());
    }

    @Test void fullThunderKeepsTheSquareOfTheRainDaylight() {
        var thunder = new MinecraftWeather(1, 1);

        assertEquals(121f / 256f, thunder.daylightFactor());
        assertEquals(0f, thunder.beamTransmittance());
    }

    @Test void rampsInterpolateLinearlyInEachLevel() {
        var halfRain = new MinecraftWeather(.5f, 0);
        assertEquals(1f - 5f / 32f, halfRain.daylightFactor());
        assertEquals(.5f, halfRain.beamTransmittance());

        // Thunder rising under full rain moves from the rain factor toward the thunder factor.
        var halfThunder = new MinecraftWeather(1, .5f);
        assertEquals((1f - 5f / 32f) * (1f - 135f / 512f), halfThunder.daylightFactor(), 1.0e-7f);
        assertEquals(0f, halfThunder.beamTransmittance());
    }

    @Test void scalesVanillaSkyLightAboveItsNightValueAtEveryTimeOfDay() {
        // Sky light factor (day 1, night 0.24) and sky light level (day 15, night 4) share the weather layer.
        float[][] dayAndNight = {{1f, .24f}, {15f, 4f}};
        for (float[] attribute : dayAndNight) {
            float day = attribute[0];
            float night = attribute[1];
            for (float clear : new float[]{day, (day + night) / 2, night}) {
                for (int rainStep = 0; rainStep <= 10; rainStep++) {
                    for (int thunderStep = 0; thunderStep <= rainStep; thunderStep++) {
                        var weather = new MinecraftWeather(rainStep / 10f, thunderStep / 10f);
                        float vanilla = vanillaWeatherLayer(clear, night, weather);
                        assertEquals((clear - night) * weather.daylightFactor(), vanilla - night, 1.0e-4f);
                    }
                }
            }
        }
    }

    /**
     * 26.2 {@code WeatherAttributes.addLayer}: rain without thunder, then thunder, each linearly blend the value
     * toward {@code FloatModifier.ALPHA_BLEND}'s result, a blend toward the night value by 5/16 and 135/256.
     */
    private static float vanillaWeatherLayer(float value, float night, MinecraftWeather weather) {
        float rainOnly = weather.rainLevel() - weather.thunderLevel();
        if (rainOnly > 0) value = lerp(rainOnly, value, lerp(.3125f, value, night));
        if (weather.thunderLevel() > 0) value = lerp(weather.thunderLevel(), value, lerp(.52734375f, value, night));
        return value;
    }

    private static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }
}
