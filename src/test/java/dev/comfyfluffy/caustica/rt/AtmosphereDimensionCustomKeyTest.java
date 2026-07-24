package dev.comfyfluffy.caustica.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/**
 * Custom/modded dimensions must fall back to the renderer's existing non-Earth behavior.
 * They must never accidentally activate the Nether or End simple gradient.
 */
final class AtmosphereDimensionCustomKeyTest {
    private static ResourceKey<Level> customDimension(String namespace, String path) {
        return ResourceKey.create(
                Level.DIMENSION_REGISTRY,
                new ResourceLocation(namespace, path));
    }

    @Test
    void customModDimensionReturnsNull() {
        ResourceKey<Level> key = customDimension("biomesoplenty", "origin_valley");
        assertNull(AtmosphereDimension.resolve(key),
                "Custom modded dimension must not match Nether or End");
    }

    @Test
    void customDimensionDoesNotUseSimpleGradient() {
        ResourceKey<Level> key = customDimension("twilightforest", "twilight_forest");
        assertNull(AtmosphereDimension.resolve(key));
    }

    @Test
    void editorDefaultFallsBackToOverworld() {
        ResourceKey<Level> key = customDimension("ad_astra", "orbit");
        assertNull(AtmosphereDimension.resolve(key),
                "Unrecognized key must resolve null");
    }

    @Test
    void resolveViaLevelNullKey() {
        assertNull(AtmosphereDimension.resolve((Level) null),
                "null Level must resolve null");
    }

    @Test
    void onlyOverworldNetherEndAreSupported() {
        for (AtmosphereDimension d : AtmosphereDimension.values()) {
            switch (d) {
                case OVERWORLD -> assertEquals(
                        AtmosphereDimension.OVERWORLD,
                        AtmosphereDimension.resolve(Level.OVERWORLD));
                case NETHER -> assertEquals(
                        AtmosphereDimension.NETHER,
                        AtmosphereDimension.resolve(Level.NETHER));
                case END -> assertEquals(
                        AtmosphereDimension.END,
                        AtmosphereDimension.resolve(Level.END));
            }
        }
    }

    @Test
    void customDimensionNeverActivatesSimpleGradient() {
        ResourceKey<Level> key = customDimension("example", "my_dim");
        assertFalse(AtmosphereDimension.OVERWORLD.isSimpleGradient(),
                "Overworld must not be simple gradient");
    }
}
