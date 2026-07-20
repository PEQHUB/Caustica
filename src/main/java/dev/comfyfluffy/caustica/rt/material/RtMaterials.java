package dev.comfyfluffy.caustica.rt.material;

import dev.comfyfluffy.caustica.CausticaConfig;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.WeatheringCopperCollection;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Semantic material family resolved from block identity, never from texture names. */
public final class RtMaterials {
    private RtMaterials() {}

    /** Values 0..8 are encoded in MaterialHeader feature bits; WATER/LAVA are dedicated singleton IDs. */
    public enum Profile {
        NEUTRAL,
        FOLIAGE,
        SOIL,
        STONE,
        WOOD,
        METAL,
        GLASS,
        WOOL,
        POLISHED,
        IRON,
        GOLD,
        COPPER,
        EXPOSED_COPPER,
        WEATHERED_COPPER,
        OXIDIZED_COPPER,
        NETHERITE,
        RAW_IRON,
        RAW_GOLD,
        RAW_COPPER,
        WATER,
        LAVA;

        public float roughness() {
            return switch (this) {
                case FOLIAGE -> 0.9f;
                case SOIL -> CausticaConfig.Rt.Materials.SOIL_ROUGHNESS.value();
                case STONE -> CausticaConfig.Rt.Materials.STONE_ROUGHNESS.value();
                case WOOD -> CausticaConfig.Rt.Materials.WOOD_ROUGHNESS.value();
                case METAL, IRON, GOLD, COPPER -> CausticaConfig.Rt.Materials.METAL_ROUGHNESS.value();
                case EXPOSED_COPPER -> Math.max(0.42f, CausticaConfig.Rt.Materials.METAL_ROUGHNESS.value());
                case WEATHERED_COPPER -> Math.max(0.58f, CausticaConfig.Rt.Materials.METAL_ROUGHNESS.value());
                case OXIDIZED_COPPER -> Math.max(0.78f, CausticaConfig.Rt.Materials.METAL_ROUGHNESS.value());
                case NETHERITE -> Math.max(0.38f, CausticaConfig.Rt.Materials.METAL_ROUGHNESS.value());
                case RAW_IRON, RAW_GOLD, RAW_COPPER -> 0.72f;
                case GLASS -> CausticaConfig.Rt.Materials.GLASS_ROUGHNESS.value();
                case WOOL -> 0.96f;
                case POLISHED -> CausticaConfig.Rt.Materials.POLISHED_ROUGHNESS.value();
                case WATER -> 0.08f;
                case LAVA -> 0.7f;
                case NEUTRAL -> 0.9f;
            };
        }

        public float metalness() {
            return switch (this) {
                case METAL, IRON, GOLD, COPPER, NETHERITE -> 1.0f;
                // A block-scale metalness blend represents unresolved oxide/mineral coverage. The
                // individual lobes remain physical conductor/dielectric responses in the shader.
                case EXPOSED_COPPER -> 0.72f;
                case WEATHERED_COPPER -> 0.35f;
                case RAW_IRON, RAW_GOLD, RAW_COPPER -> 0.68f;
                default -> 0.0f;
            };
        }

        public float fallbackSss() {
            return this == FOLIAGE ? CausticaConfig.Rt.Materials.FOLIAGE_BACKLIGHTING.value() : 0.0f;
        }

        public float fiberWeight() {
            return this == WOOL ? CausticaConfig.Rt.Materials.WOOL_FIBER_SHEEN.value() : 0.0f;
        }
    }

    public static final int SEMANTIC_PROFILE_COUNT = Profile.RAW_COPPER.ordinal() + 1;
    public static final float WATER_ROUGH = Profile.WATER.roughness();
    public static final float LAVA_ROUGH = Profile.LAVA.roughness();
    public static final float ENTITY_ROUGH = 0.8f;

    private static final Set<Block> FOLIAGE = Set.of(
            Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN,
            Blocks.SHORT_DRY_GRASS, Blocks.TALL_DRY_GRASS, Blocks.DEAD_BUSH,
            Blocks.VINE, Blocks.SEAGRASS, Blocks.TALL_SEAGRASS, Blocks.KELP, Blocks.KELP_PLANT,
            Blocks.SUGAR_CANE, Blocks.BAMBOO, Blocks.BAMBOO_SAPLING, Blocks.LILY_PAD,
            Blocks.SWEET_BERRY_BUSH, Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT,
            Blocks.WEEPING_VINES, Blocks.WEEPING_VINES_PLANT,
            Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT,
            Blocks.NETHER_SPROUTS, Blocks.WARPED_ROOTS, Blocks.CRIMSON_ROOTS,
            Blocks.HANGING_ROOTS, Blocks.AZALEA, Blocks.FLOWERING_AZALEA);

    private static final Set<Block> POLISHED = Set.of(
            Blocks.QUARTZ_BLOCK, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_PILLAR,
            Blocks.SMOOTH_STONE, Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE,
            Blocks.POLISHED_GRANITE, Blocks.POLISHED_DIORITE, Blocks.POLISHED_ANDESITE,
            Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BASALT,
            Blocks.POLISHED_TUFF, Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE,
            Blocks.BRICKS, Blocks.STONE_BRICKS, Blocks.MUD_BRICKS, Blocks.NETHER_BRICKS,
            Blocks.RED_NETHER_BRICKS, Blocks.END_STONE_BRICKS, Blocks.RESIN_BRICKS,
            Blocks.TUFF_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS);

    /** Compact mineral blocks use metallic sound effects but are polished dielectrics, not conductors. */
    private static final Set<Block> NON_METAL_MINERALS = Set.of(
            Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK, Blocks.LAPIS_BLOCK, Blocks.REDSTONE_BLOCK,
            Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST, Blocks.QUARTZ_BLOCK,
            Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_PILLAR);

    private static final Set<Block> GOLD = Set.of(
            Blocks.GOLD_BLOCK, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE, Blocks.BELL);

    private static final Map<Block, WeatheringCopper.WeatherState> COPPER_AGE = copperAges();

    public static float roughness(BlockState state) {
        return profile(state).roughness();
    }

    /** Stable profile priority: glass, metal, foliage, wool, wood, soil, polished, stone, neutral. */
    public static Profile profile(BlockState state) {
        if (state == null) return Profile.NEUTRAL;
        Block block = state.getBlock();
        SoundType sound = state.getSoundType();
        if (state.is(BlockTags.ICE) || sound == SoundType.GLASS) return Profile.GLASS;
        if (NON_METAL_MINERALS.contains(block)) return Profile.POLISHED;
        if (block == Blocks.RAW_IRON_BLOCK) return Profile.RAW_IRON;
        if (block == Blocks.RAW_GOLD_BLOCK) return Profile.RAW_GOLD;
        if (block == Blocks.RAW_COPPER_BLOCK) return Profile.RAW_COPPER;
        if (GOLD.contains(block)) return Profile.GOLD;
        if (block == Blocks.NETHERITE_BLOCK || sound == SoundType.NETHERITE_BLOCK) return Profile.NETHERITE;
        WeatheringCopper.WeatherState copperAge = COPPER_AGE.get(block);
        if (copperAge != null) return copperProfile(copperAge);
        if (state.is(BlockTags.COPPER) || sound == SoundType.COPPER) return Profile.COPPER;
        if (isIron(sound)) return Profile.IRON;
        // Vanilla uses the generic METAL sound for several iron/steel constructions as well as compact
        // minerals (excluded above). Unknown modded metals keep an albedo-tinted conductor fallback.
        if (sound == SoundType.METAL) {
            return "minecraft".equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace())
                    ? Profile.IRON : Profile.METAL;
        }
        if (block instanceof LeavesBlock || state.is(BlockTags.LEAVES) || state.is(BlockTags.CROPS)
                || state.is(BlockTags.FLOWERS) || FOLIAGE.contains(block)) return Profile.FOLIAGE;
        if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS)
                || sound == SoundType.WOOL || block instanceof BedBlock
                || block instanceof AbstractBannerBlock) return Profile.WOOL;
        if (sound == SoundType.WOOD || sound == SoundType.BAMBOO_WOOD
                || state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)
                || state.is(BlockTags.WOODEN_DOORS) || state.is(BlockTags.WOODEN_TRAPDOORS)
                || state.is(BlockTags.WOODEN_STAIRS) || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.WOODEN_FENCES) || state.is(BlockTags.WOODEN_BUTTONS)
                || state.is(BlockTags.WOODEN_PRESSURE_PLATES)) return Profile.WOOD;
        if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
                || state.is(BlockTags.TERRACOTTA) || sound == SoundType.GRAVEL
                || sound == SoundType.MUD || sound == SoundType.SAND) return Profile.SOIL;
        if (state.is(BlockTags.STONE_BRICKS) || state.is(BlockTags.GLAZED_TERRACOTTA)
                || POLISHED.contains(block)) return Profile.POLISHED;
        if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(BlockTags.STONE_ORE_REPLACEABLES) || sound == SoundType.STONE) return Profile.STONE;
        return Profile.NEUTRAL;
    }

    public static float metalness(BlockState state) {
        return profile(state).metalness();
    }

    private static boolean isIron(SoundType sound) {
        return sound == SoundType.IRON || sound == SoundType.ANVIL || sound == SoundType.CHAIN;
    }

    private static Profile copperProfile(WeatheringCopper.WeatherState state) {
        return switch (state) {
            case UNAFFECTED -> Profile.COPPER;
            case EXPOSED -> Profile.EXPOSED_COPPER;
            case WEATHERED -> Profile.WEATHERED_COPPER;
            case OXIDIZED -> Profile.OXIDIZED_COPPER;
        };
    }

    private static Map<Block, WeatheringCopper.WeatherState> copperAges() {
        IdentityHashMap<Block, WeatheringCopper.WeatherState> ages = new IdentityHashMap<>();
        List<WeatheringCopperCollection<Block>> collections = List.of(
                Blocks.COPPER_BLOCK, Blocks.CUT_COPPER, Blocks.CHISELED_COPPER,
                Blocks.CUT_COPPER_STAIRS, Blocks.CUT_COPPER_SLAB,
                Blocks.COPPER_DOOR, Blocks.COPPER_TRAPDOOR, Blocks.COPPER_GRATE,
                Blocks.COPPER_BULB, Blocks.COPPER_CHEST, Blocks.COPPER_GOLEM_STATUE,
                Blocks.LIGHTNING_ROD, Blocks.COPPER_BARS, Blocks.COPPER_CHAIN,
                Blocks.COPPER_LANTERN);
        for (WeatheringCopperCollection<Block> collection : collections) {
            addCopperAges(ages, collection.weathering());
            addCopperAges(ages, collection.waxed());
        }
        return Collections.unmodifiableMap(ages);
    }

    private static void addCopperAges(Map<Block, WeatheringCopper.WeatherState> ages,
                                      WeatheringCopperCollection.ByState<Block> blocks) {
        for (WeatheringCopper.WeatherState state : WeatheringCopper.WeatherState.values()) {
            ages.put(blocks.pick(state), state);
        }
    }
}
