package dev.comfyfluffy.caustica.rt.terrain;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class LavaDaylightHotfixContractTest {
    @Test
    void lavaPrimitiveOwnsASeparatePayloadFlag() throws Exception {
        String mesher = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtTerrainMesher.java"));
        String common = Files.readString(Path.of("shaders/world/world_common.slang"));
        String closestHit = Files.readString(Path.of("shaders/world/world.rchit.slang"));

        assertTrue(mesher.contains("fluidCapture.lava = fluid.is(FluidTags.LAVA)"));
        int materialIdLane = mesher.lastIndexOf("prim.add(Float.intBitsToFloat(materialId));");
        int tintWLane = mesher.lastIndexOf("prim.add(0f);", materialIdLane);
        int lavaFlagLane = mesher.indexOf("prim.add(Float.intBitsToFloat(lava ? PRIM_FLAG_LAVA : 0));",
                materialIdLane);
        int aux0Lane = mesher.indexOf("prim.add(0f);", lavaFlagLane);
        int aux1Lane = mesher.indexOf("prim.add(0f);", aux0Lane + 1);
        assertTrue(tintWLane >= 0 && tintWLane < materialIdLane && materialIdLane < lavaFlagLane
                        && lavaFlagLane < aux0Lane && aux0Lane < aux1Lane,
                "lava must occupy TerrainPrim.flags, after tint and materialId and before aux0/aux1");
        assertTrue(common.contains("PAYLOAD_LAVA_SURFACE = 1u << 16u"));
        assertTrue(closestHit.contains("(pr.flags & TERRAIN_PRIM_LAVA) != 0u"));
        assertTrue(closestHit.contains("payload.flags |= PAYLOAD_LAVA_SURFACE"));
    }

    @Test
    void lavaOverridesOnlyReflectedMaterialAndGuides() throws Exception {
        String raygen = Files.readString(Path.of("shaders/world/world.rgen.slang"));

        int lavaBranch = raygen.indexOf("if (lavaSurface)");
        int guideCapture = raygen.indexOf("if (captureGuides && segment == 0)", lavaBranch);
        int emission = raygen.indexOf("float3 materialEmissive = albedo * emission * emissiveRadiance;", guideCapture);
        assertTrue(lavaBranch >= 0);
        assertTrue(raygen.indexOf("perceptualRough = 1.0;", lavaBranch) < guideCapture);
        assertTrue(raygen.indexOf("diffAlb = albedo * 0.15;", lavaBranch) < guideCapture);
        assertTrue(raygen.indexOf("F0 = float3(0.02, 0.02, 0.02);", lavaBranch) < guideCapture);
        assertTrue(guideCapture > lavaBranch, "RR guides must observe the lava material override");
        assertTrue(emission > guideCapture, "lava emission must continue to use the original albedo");
    }
}
