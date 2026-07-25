package dev.comfyfluffy.caustica.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

final class AtmosphereFrameFlagsTest {
    @Test
    void overworldUsesPhysicalAtmosphereFlag() {
        assertEquals(
                1 << 10,
                RtComposite.atmosphereFrameFlags(Level.OVERWORLD)
        );
    }

    @Test
    void netherUsesSimpleDimensionAtmosphereFlag() {
        assertEquals(
                1 << 15,
                RtComposite.atmosphereFrameFlags(Level.NETHER)
        );
    }

    @Test
    void endUsesSimpleDimensionAtmosphereFlag() {
        assertEquals(
                1 << 15,
                RtComposite.atmosphereFrameFlags(Level.END)
        );
    }

    @Test
    void nullDimensionUsesExistingFallback() {
        assertEquals(
                0,
                RtComposite.atmosphereFrameFlags(null)
        );
    }
}
