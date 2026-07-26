package dev.comfyfluffy.caustica.rt.terrain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class PortalRenderingContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static String slice(
            String source,
            String startMarker,
            String endMarker
    ) {
        int start = source.indexOf(startMarker);
        assertTrue(start >= 0, "Missing start marker: " + startMarker);

        int end = source.indexOf(endMarker, start);
        assertTrue(end > start, "Missing end marker: " + endMarker);

        return source.substring(start, end);
    }

    @Test
    void endPortalStillUsesItsVanillaLayerDecoder() throws Exception {
        String portal = read("shaders/world/world_portal.slang");
        String closestHit = read("shaders/world/world.rchit.slang");

        assertTrue(portal.contains("float3 color = srgbToLinear(textures"));
        assertTrue(portal.contains("float3 layerColor = srgbToLinear(textures"));
        assertFalse(closestHit.contains("srgbToLinear(portalSrgb)"));
        assertTrue(closestHit.contains(
                "float3 portalLinear709 = sampleVanillaEndPortal"
        ));
    }

    @Test
    void netherPortalExtractionIsFullbrightButNotGlass() throws Exception {
        String mesher = read(
                "src/main/java/dev/comfyfluffy/caustica/rt/terrain/"
                        + "RtTerrainMesher.java"
        );

        assertTrue(mesher.contains(
                "layer == ChunkSectionLayer.TRANSLUCENT "
                        + "&& !isNetherPortal(state)"
        ));
        assertTrue(mesher.contains("q.emission = isNetherPortal(state)"));
        assertTrue(mesher.contains("q.ta = sa / 1020f"));
        assertTrue(mesher.contains("prim.add(q.ta)"));
        assertTrue(mesher.contains("PRIM_FLAG_NETHER_PORTAL"));

        // Phase one has no portal-specific RIS collection.
        assertFalse(mesher.contains(
                "collectLights(collected, mesh.translucent"
        ));

        String collector = read(
                "src/main/java/dev/comfyfluffy/caustica/rt/terrain/"
                        + "RtLightCollector.java"
        );
        assertFalse(collector.contains("NETHER_PORTAL"));
    }

    @Test
    void netherPortalUsesAtlasColorAndAlphaAsEmission() throws Exception {
        String closestHit = read("shaders/world/world.rchit.slang");

        String portalHit = slice(
                closestHit,
                "if ((pr.flags & TERRAIN_PRIM_NETHER_PORTAL) != 0u) {",
                "// Stained glass / ice:"
        );

        assertTrue(closestHit.contains("float2 clampAtlasUvForLod("));
        assertTrue(portalHit.contains("clampAtlasUvForLod("));
        assertTrue(portalHit.contains("sampleSrgbLinearTrilinear("));
        assertTrue(portalHit.contains("srgbToLinear(tint)"));
        assertTrue(portalHit.contains("portalTexel.a * pr.tint.a"));
        assertTrue(portalHit.contains("stateEmission * alpha"));
        assertTrue(portalHit.contains("EMISSION_SOURCE_UNIFORM"));
        assertTrue(portalHit.contains("PAYLOAD_NETHER_PORTAL"));

        assertFalse(portalHit.contains("PAYLOAD_INTERFACE_ENTERING"));
        assertFalse(portalHit.contains("PAYLOAD_EMITTER_IN_LIST"));
        assertFalse(portalHit.contains("MATERIAL_GLASS"));
    }

    @Test
    void netherPortalEmissionIsTerminalAndRisIndependent() throws Exception {
        String raygen = read("shaders/world/world.rgen.slang");

        String portalPath = slice(
                raygen,
                "if (payloadNetherPortal()) {",
                "bool translucentSurface ="
        );

        assertTrue(raygen.contains(
                "float3 netherPortalEmissionRadiance("
        ));
        assertTrue(raygen.contains("EMISSIVE_BASE_RADIANCE"));

        assertTrue(portalPath.contains(
                "netherPortalEmissionRadiance("
        ));
        assertTrue(portalPath.contains("L += contribution"));
        assertTrue(portalPath.contains("gv_albedo = float3(0.0)"));
        assertTrue(portalPath.contains("gv_specAlb = float3(0.0)"));
        assertTrue(portalPath.contains("gv_animatedGuide = 1.0"));
        assertTrue(portalPath.contains("break;"));

        assertFalse(portalPath.contains("risOn"));
        assertFalse(portalPath.contains("payloadEmitterInList"));
        assertFalse(portalPath.contains("throughput *= 1.0 - alpha"));
        assertFalse(portalPath.contains("GLASS_TRANSMIT_BIAS"));
        assertFalse(portalPath.contains("ro = hitPos"));
        assertFalse(portalPath.contains("continue;"));
    }

    @Test
    void netherPortalBlocksShadowTransport() throws Exception {
        String anyHit = read("shaders/world/world.rahit.slang");

        String portalShadow = slice(
                anyHit,
                "if ((pr.flags & TERRAIN_PRIM_NETHER_PORTAL) != 0u) {",
                "MaterialHeader materialHeader"
        );

        assertTrue(portalShadow.contains(
                "shadowVis = float4(0.0, 0.0, 0.0, -2.0)"
        ));
        assertTrue(portalShadow.contains("return;"));

        assertFalse(portalShadow.contains("IgnoreHit()"));
        assertFalse(portalShadow.contains("1.0 - alpha"));
        assertFalse(portalShadow.contains("shadowVis.rgb *="));
    }

    @Test
    void dlssdReceivesOneFinishedPortalImage() throws Exception {
        String raygen = read("shaders/world/world.rgen.slang");
        String composite = read(
                "src/main/java/dev/comfyfluffy/caustica/rt/"
                        + "RtComposite.java"
        );
        String disocclusion = read(
                "shaders/display/dlssd_disocclusion.comp"
        );

        assertTrue(composite.contains(
                "evaluate(cmd.address(), output"
        ));

        assertFalse(raygen.contains("gColorBeforeTransparency"));
        assertFalse(raygen.contains("gTransparencyLayer"));
        assertFalse(raygen.contains("gv_portalLayerColor"));
        assertFalse(raygen.contains("gv_portalLayerOpacity"));
        assertFalse(raygen.contains("premultipliedPortal"));

        assertFalse(composite.contains("gColorBeforeTransparency"));
        assertFalse(composite.contains("gTransparencyLayer"));
        assertFalse(composite.contains("gTransparencyLayerOpacity"));

        // Keep the ordinary animated-current-color hint, but remove the
        // one-pixel portal silhouette dilation that created a frame halo.
        assertFalse(disocclusion.contains("portalSilhouette"));
        assertFalse(disocclusion.contains("animatedAt("));
        assertTrue(disocclusion.contains("biasCurrentColorImage"));
    }
}
