package dev.comfyfluffy.caustica.rt.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

final class RtTerrainMesherPortalClassificationTest {
    @Test
    void netherPortalUsesDedicatedEmissiveGlass() {
        var state = Blocks.NETHER_PORTAL.defaultBlockState();

        assertTrue(
                RtTerrainMesher.usesTransmissiveMaterial(
                        state,
                        ChunkSectionLayer.TRANSLUCENT
                )
        );

        assertTrue(RtTerrainMesher.isNetherPortal(state));
        assertEquals(
                RtTerrainMesher.OPTICAL_NETHER_PORTAL,
                RtTerrainMesher.opticalClassForTest(state)
        );
    }

    @Test
    void glassRetainsPhysicalTransmission() {
        assertTrue(
                RtTerrainMesher.usesTransmissiveMaterial(
                        Blocks.GLASS.defaultBlockState(),
                        ChunkSectionLayer.TRANSLUCENT
                )
        );
    }

    @Test
    void nonTranslucentLayersNeverUseTransmission() {
        assertFalse(
                RtTerrainMesher.usesTransmissiveMaterial(
                        Blocks.STONE.defaultBlockState(),
                        ChunkSectionLayer.SOLID
                )
        );
    }

    @Test
    void unknownTranslucentStatePreservesFallback() {
        assertTrue(
                RtTerrainMesher.usesTransmissiveMaterial(
                        null,
                        ChunkSectionLayer.TRANSLUCENT
                )
        );
    }
}
