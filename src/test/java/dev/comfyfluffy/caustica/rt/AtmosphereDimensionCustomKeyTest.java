package dev.comfyfluffy.caustica.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/**
 * Custom/modded dimensions must fall back to the renderer's existing non-Earth behavior.
 * They must never accidentally activate the Nether or End simple gradient.
 */
final class AtmosphereDimensionCustomKeyTest {
    @Test
    void nullLevelResolvesNull() {
        assertNull(AtmosphereDimension.resolve((Level) null),
                "null Level must resolve null");
    }

    @Test
    void nullKeyResolvesNull() {
        assertNull(AtmosphereDimension.resolve((ResourceKey<Level>) null),
                "null ResourceKey must resolve null");
    }

    @Test
    void onlyOverworldNetherEndAreSupported() {
        assertEquals(
                AtmosphereDimension.OVERWORLD,
                AtmosphereDimension.resolve(Level.OVERWORLD));
        assertEquals(
                AtmosphereDimension.NETHER,
                AtmosphereDimension.resolve(Level.NETHER));
        assertEquals(
                AtmosphereDimension.END,
                AtmosphereDimension.resolve(Level.END));
    }

    @Test
    void onlyNetherAndEndUseSimpleGradient() {
        assertFalse(AtmosphereDimension.OVERWORLD.isSimpleGradient(),
                "Overworld must not be simple gradient");
    }

    @Test
    void editorDefaultFallsBackToOverworldForNull() {
        assertNull(AtmosphereDimension.resolve((Level) null));
        assertEquals(
                AtmosphereDimension.OVERWORLD,
                AtmosphereDimension.editorDefault(null));
    }
}
