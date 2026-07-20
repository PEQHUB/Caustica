package dev.comfyfluffy.caustica.rt.material;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.WeatheringCopperCollection;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RtSemanticMaterialProfilesTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void compactMineralsAreNeverMisclassifiedAsConductors() {
        assertProfile(Blocks.DIAMOND_BLOCK, RtMaterials.Profile.POLISHED);
        assertProfile(Blocks.EMERALD_BLOCK, RtMaterials.Profile.POLISHED);
        assertProfile(Blocks.LAPIS_BLOCK, RtMaterials.Profile.POLISHED);
        assertProfile(Blocks.REDSTONE_BLOCK, RtMaterials.Profile.POLISHED);
    }

    @Test
    void pureAndRawVanillaMetalsReceivePhysicalIdentities() {
        assertProfile(Blocks.IRON_BLOCK, RtMaterials.Profile.IRON);
        assertProfile(Blocks.IRON_DOOR, RtMaterials.Profile.IRON);
        assertProfile(Blocks.IRON_TRAPDOOR, RtMaterials.Profile.IRON);
        assertProfile(Blocks.IRON_BARS, RtMaterials.Profile.IRON);
        assertProfile(Blocks.ANVIL, RtMaterials.Profile.IRON);
        assertProfile(Blocks.GOLD_BLOCK, RtMaterials.Profile.GOLD);
        assertProfile(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE, RtMaterials.Profile.GOLD);
        assertProfile(Blocks.NETHERITE_BLOCK, RtMaterials.Profile.NETHERITE);
        assertProfile(Blocks.RAW_IRON_BLOCK, RtMaterials.Profile.RAW_IRON);
        assertProfile(Blocks.RAW_GOLD_BLOCK, RtMaterials.Profile.RAW_GOLD);
        assertProfile(Blocks.RAW_COPPER_BLOCK, RtMaterials.Profile.RAW_COPPER);
    }

    @Test
    void everyRegisteredCopperCollectionTracksOxidationAndWaxing() {
        List<WeatheringCopperCollection<Block>> collections = List.of(
                Blocks.COPPER_BLOCK, Blocks.CUT_COPPER, Blocks.CHISELED_COPPER,
                Blocks.CUT_COPPER_STAIRS, Blocks.CUT_COPPER_SLAB,
                Blocks.COPPER_DOOR, Blocks.COPPER_TRAPDOOR, Blocks.COPPER_GRATE,
                Blocks.COPPER_BULB, Blocks.COPPER_CHEST, Blocks.COPPER_GOLEM_STATUE,
                Blocks.LIGHTNING_ROD, Blocks.COPPER_BARS, Blocks.COPPER_CHAIN,
                Blocks.COPPER_LANTERN);
        for (WeatheringCopperCollection<Block> collection : collections) {
            verifyCopperStates(collection.weathering());
            verifyCopperStates(collection.waxed());
        }
    }

    private static void verifyCopperStates(WeatheringCopperCollection.ByState<Block> blocks) {
        assertProfile(blocks.pick(WeatheringCopper.WeatherState.UNAFFECTED), RtMaterials.Profile.COPPER);
        assertProfile(blocks.pick(WeatheringCopper.WeatherState.EXPOSED), RtMaterials.Profile.EXPOSED_COPPER);
        assertProfile(blocks.pick(WeatheringCopper.WeatherState.WEATHERED), RtMaterials.Profile.WEATHERED_COPPER);
        assertProfile(blocks.pick(WeatheringCopper.WeatherState.OXIDIZED), RtMaterials.Profile.OXIDIZED_COPPER);
    }

    private static void assertProfile(Block block, RtMaterials.Profile profile) {
        assertEquals(profile, RtMaterials.profile(block.defaultBlockState()), block.toString());
    }
}
