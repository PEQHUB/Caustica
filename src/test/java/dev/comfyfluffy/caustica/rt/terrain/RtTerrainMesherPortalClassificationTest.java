package dev.comfyfluffy.caustica.rt.terrain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

final class RtTerrainMesherPortalClassificationTest {
    @Test
    void netherPortalIsNotPhysicalGlass() {
        assertFalse(
                RtTerrainMesher.usesTransmissiveMaterial(
                        Blocks.NETHER_PORTAL.defaultBlockState(),
                        ChunkSectionLayer.TRANSLUCENT
                )
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
    void unknownTranslucentStatePreservesOldFallback() {
        assertTrue(
                RtTerrainMesher.usesTransmissiveMaterial(
                        null,
                        ChunkSectionLayer.TRANSLUCENT
                )
        );
    }
}
