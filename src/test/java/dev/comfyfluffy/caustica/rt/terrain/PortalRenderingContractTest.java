package dev.comfyfluffy.caustica.rt.terrain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class PortalRenderingContractTest {
    @Test
    void endPortalDecodesEachTextureLayerBeforeHdrAccumulation() throws Exception {
        String portal = Files.readString(Path.of("shaders/world/world_portal.slang"));
        String closestHit = Files.readString(Path.of("shaders/world/world.rchit.slang"));

        assertTrue(portal.contains("float3 color = srgbToLinear(textures"));
        assertTrue(portal.contains("float3 layerColor = srgbToLinear(textures"));
        assertFalse(closestHit.contains("srgbToLinear(portalSrgb)"));
        assertTrue(closestHit.contains("float3 portalLinear709 = sampleVanillaEndPortal"));
    }

    @Test
    void netherPortalPreservesVertexAlphaAndAvoidsGenericFiveTimesRisPower() throws Exception {
        String mesher = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtTerrainMesher.java"));
        String closestHit = Files.readString(Path.of("shaders/world/world.rchit.slang"));
        String collector = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtLightCollector.java"));

        assertTrue(mesher.contains("q.ta = sa / 1020f"));
        assertTrue(mesher.contains("prim.add(q.ta)"));
        assertTrue(closestHit.contains("portalTexel.a * pr.tint.a"));
        assertTrue(collector.contains("radianceStrength = netherPortal ? 1.0f"));
        assertTrue(collector.contains("radianceStrength * vertexAlpha"));
    }

    @Test
    void animatedPortalAndSilhouetteAreHardDisocclusions() throws Exception {
        String mask = Files.readString(Path.of("shaders/display/dlssd_disocclusion.comp"));

        assertTrue(mask.contains("portalSilhouette = max(portalSilhouette"));
        assertTrue(mask.contains("disocclusion = max(disocclusion, portalSilhouette)"));
        assertTrue(mask.contains("biasCurrentColorImage"));
    }

    @Test
    void netherPortalUsesDlssdLayeredTransparencyInputs() throws Exception {
        String raygen = Files.readString(Path.of("shaders/world/world.rgen.slang"));
        String composite = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java"));

        assertTrue(raygen.contains("gColorBeforeTransparency"));
        assertTrue(raygen.contains("float4(premultipliedPortal, opacity)"));
        assertTrue(raygen.contains("(frameRadiance - premultipliedPortal) / transmission"));
        assertTrue(composite.contains("evaluate(cmd.address(), gColorBeforeTransparency"));
        assertTrue(composite.contains("gTransparencyLayer, null, dlssdOutput"));
        assertFalse(composite.contains("gTransparencyLayerOpacity"));
    }
}
