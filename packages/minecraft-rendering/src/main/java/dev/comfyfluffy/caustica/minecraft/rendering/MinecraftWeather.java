package dev.comfyfluffy.caustica.minecraft.rendering;

/**
 * Minecraft's rain and thunder levels sampled at frame ingress, and the light factors vanilla derives from them.
 * {@code thunderLevel} is {@code Level.getThunderLevel}, which vanilla already scales by the rain level.
 */
public record MinecraftWeather(float rainLevel, float thunderLevel) {
    /** Vanilla's weather blend of the sky light toward its night value: 5/16 for rain, 1 - (11/16)^2 for thunder. */
    private static final float RAIN_SKY_LIGHT_ALPHA = 0.3125f;
    private static final float THUNDER_SKY_LIGHT_ALPHA = 0.52734375f;

    /**
     * Fraction of clear-weather sun-driven light that remains, for the sun's beam, the daylit sky and the fog's
     * daylight ambient. Vanilla blends its sky light level and factor toward their night values, first by the rain
     * alpha for rain without thunder, then by the thunder alpha for thunder. That scales the light above the night
     * floor by this product; the moon, airglow and stars form that floor.
     */
    public float daylightFactor() {
        return (1.0f - RAIN_SKY_LIGHT_ALPHA * (rainLevel - thunderLevel))
                * (1.0f - THUNDER_SKY_LIGHT_ALPHA * thunderLevel);
    }

    /** Fraction of the sun's and moon's direct beams that passes the rain: vanilla's sun and moon sprite alpha. */
    public float beamTransmittance() {
        return 1.0f - rainLevel;
    }
}
