package dev.comfyfluffy.caustica.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

final class AtmosphereDimensionTest {
    @Test
    void resolvesSupportedVanillaDimensions() {
        assertEquals(
                AtmosphereDimension.OVERWORLD,
                AtmosphereDimension.resolve(Level.OVERWORLD)
        );
        assertEquals(
                AtmosphereDimension.NETHER,
                AtmosphereDimension.resolve(Level.NETHER)
        );
        assertEquals(
                AtmosphereDimension.END,
                AtmosphereDimension.resolve(Level.END)
        );
    }

    @Test
    void nullDimensionIsNotTreatedAsNetherOrEnd() {
        assertNull(AtmosphereDimension.resolve(
                (ResourceKey<Level>)null
        ));
    }

    @Test
    void onlyNetherAndEndUseTheSimpleGradient() {
        assertEquals(
                false,
                AtmosphereDimension.OVERWORLD.isSimpleGradient()
        );
        assertEquals(
                true,
                AtmosphereDimension.NETHER.isSimpleGradient()
        );
        assertEquals(
                true,
                AtmosphereDimension.END.isSimpleGradient()
        );
    }
}
