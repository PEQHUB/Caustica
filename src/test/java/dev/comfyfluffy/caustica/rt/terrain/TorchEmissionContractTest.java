package dev.comfyfluffy.caustica.rt.terrain;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TorchEmissionContractTest {
    private static final Path MATERIAL_ROOT = Path.of(
            "src/main/resources/assets/minecraft/textures/block");

    @Test
    void everyActiveTorchClassUsesTheBoostButOtherEmittersDoNot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        assertTrue(RtTerrain.isActiveTorch(Blocks.TORCH.defaultBlockState()));
        assertTrue(RtTerrain.isActiveTorch(Blocks.WALL_TORCH.defaultBlockState()));
        assertTrue(RtTerrain.isActiveTorch(Blocks.SOUL_TORCH.defaultBlockState()));
        assertTrue(RtTerrain.isActiveTorch(Blocks.COPPER_TORCH.defaultBlockState()));
        assertTrue(RtTerrain.isActiveTorch(Blocks.REDSTONE_TORCH.defaultBlockState()));
        assertFalse(RtTerrain.isActiveTorch(Blocks.REDSTONE_TORCH.defaultBlockState()
                .setValue(RedstoneTorchBlock.LIT, false)));
        assertFalse(RtTerrain.isActiveTorch(Blocks.GLOWSTONE.defaultBlockState()));
    }

    @Test
    void shaderAppliesTheTorchScaleAfterAuthoredEmissionDecode() throws Exception {
        String source = Files.readString(Path.of("shaders/world/world.rchit.slang"));
        assertTrue(source.contains("max(ConstPtr<WorldPush>(pcAddr.worldPushAddr)[0].torchEmissionScale, 0.0)"));
        assertTrue(source.contains("emission = mappedEmission * emissionScale;"));
    }

    @Test
    void builtInMaterialMapsEmitOnlyFromTheTorchCaps() throws Exception {
        assertMask("torch_s.png", Set.of(point(7, 6), point(8, 6), point(7, 7), point(8, 7)));
        assertMask("soul_torch_s.png", Set.of(point(7, 6), point(8, 6), point(7, 7), point(8, 7)));
        assertMask("copper_torch_s.png", Set.of(point(7, 6), point(8, 6), point(7, 7), point(8, 7)));
        assertMask("redstone_torch_s.png", Set.of(
                point(6, 5), point(7, 5), point(8, 5), point(9, 5),
                point(6, 6), point(7, 6), point(8, 6), point(9, 6),
                point(6, 7), point(7, 7), point(8, 7), point(9, 7),
                point(6, 8), point(7, 8), point(8, 8), point(9, 8)));
    }

    private static void assertMask(String name, Set<Integer> emitting) throws Exception {
        BufferedImage image = ImageIO.read(MATERIAL_ROOT.resolve(name).toFile());
        assertEquals(16, image.getWidth(), name);
        assertEquals(16, image.getHeight(), name);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int rgba = image.getRGB(x, y);
                assertEquals(0, (rgba >>> 16) & 0xFF, name + " red");
                assertEquals(10, (rgba >>> 8) & 0xFF, name + " green");
                assertEquals(0, rgba & 0xFF, name + " blue");
                assertEquals(emitting.contains(point(x, y)) ? 254 : 255, (rgba >>> 24) & 0xFF,
                        name + " alpha at " + x + "," + y);
            }
        }
    }

    private static int point(int x, int y) {
        return (y << 8) | x;
    }
}
