package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DlssdWaterGuideContractTest {
    @Test
    void animatedWaterDoesNotAnnihilateTheWholePixelHistory() throws IOException {
        String raygen = read("shaders/world/world.rgen.slang");
        String mask = read("shaders/display/dlssd_disocclusion.comp");
        String pipeline = read("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtDlssdDisocclusionPipeline.java");
        String composite = read("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");
        String rr = read("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtDlssRr.java");

        assertTrue(raygen.contains("gAnimatedGuide[pix] = gv_animatedGuide"));
        assertTrue(raygen.contains("interfacePos.xz + pc.waterAnchor.xy"));
        assertTrue(raygen.contains("interfaceNormal = applyWaterWaves(interfaceNormal, waterDomain, waterWaveTime)"));
        assertTrue(raygen.contains("encounteredAnimatedWater = true"));
        assertFalse(raygen.contains("if (encounteredAnimatedWater) gv_animatedGuide = 1.0"));
        assertFalse(raygen.contains("if (waterWaves) gv_animatedGuide = 1.0"));
        assertTrue(raygen.contains("gv_animatedGuide = max(gv_animatedGuide, waveHistoryBias)"));
        assertTrue(raygen.contains("saturate((angularPixels - 0.25) * (1.0 / 1.75))"));
        assertTrue(raygen.contains("length(n - previousWaterNormal)"));
        assertTrue(mask.contains("imageLoad(animatedGuideImage, pixel)"));
        assertTrue(mask.contains("float currentBias = max(disocclusion, animated)"));
        assertTrue(pipeline.contains("long biasCurrent, long animatedGuide"));
        assertTrue(composite.contains("gAnimatedGuide.view"));
        assertTrue(rr.contains("BUFFER_BIAS_CURRENT_COLOR_HINT"));
        assertTrue(rr.contains("writeResource(resources, 8, biasCurrentColor"));
    }

    @Test
    void unresolvedWaterNeverExportsAbsorptionTintAsDiffuseAlbedo() throws IOException {
        String raygen = read("shaders/world/world.rgen.slang");
        assertTrue(raygen.contains("crossing < uint(MAX_OPTICAL_INTERFACE_DEPTH)"));
        assertTrue(raygen.contains("gv_albedo = float3(0.0, 0.0, 0.0)"));
        assertTrue(raygen.contains("Total internal reflection has no transmitted diffuse destination"));
        assertTrue(raygen.contains("transmitted = false"));
        assertTrue(raygen.contains("if (dot(nextDirection, nextDirection) <= 0.0) return"));
        assertTrue(raygen.contains("Crossing-budget exhaustion means no trustworthy diffuse destination"));
        assertFalse(raygen.contains("gv_opticalFallbackAlbedo"));
        assertFalse(raygen.contains("unresolvedDiffuseAlbedo"));
    }

    @Test
    void standardAndLayeredRefractionMoveTheOrdinaryGuideTupleToOneDestination() throws IOException {
        String raygen = read("shaders/world/world.rgen.slang");
        int destination = raygen.indexOf("if (destinationValid) {");
        int failure = raygen.indexOf("} else {\n            // No coherent transmitted layer", destination);
        String validPath = raygen.substring(destination, failure);

        assertTrue(validPath.contains("gv_motionHitCamRel = destinationHitCamRel"));
        assertTrue(validPath.contains("gv_albedo = destinationDiffuseAlbedo"));
        assertTrue(validPath.contains("gv_hitCamRel = destinationHitCamRel"));
        assertTrue(validPath.contains("gv_normal = destinationNormal"));
        assertTrue(validPath.contains("gv_rough = destinationRoughness"));
        assertTrue(validPath.contains("gv_specAlb = destinationSpecularAlbedo"));
        assertTrue(raygen.contains("if (writeRrGuides && gv_motionUseRefracted) specMotion = motion"));
        assertFalse(raygen.contains("if (encounteredAnimatedWater) gv_animatedGuide = 1.0"));
    }

    @Test
    void highQualityWaterKeepsTheBaselineReconstructionContract() throws IOException {
        String raygen = read("shaders/world/world.rgen.slang");
        String build = read("build.gradle");
        String bringup = read("src/main/java/dev/comfyfluffy/caustica/rt/RtDeviceBringup.java");
        String config = read("src/main/java/dev/comfyfluffy/caustica/CausticaConfig.java");

        assertTrue(raygen.contains("#define CAUSTICA_TRANSPARENCY_HQ 0"));
        assertFalse(raygen.contains("if (waterWaves) gv_animatedGuide = 1.0"));
        assertFalse(raygen.contains("gv_opticalGuidePreviousDir"));
        assertFalse(raygen.contains("gv_motionUseExplicitPrevious"));
        assertFalse(raygen.contains("previousIncidentDir = normalize((hitPos - pc.camOffset) + pc.camDelta)"));
        assertFalse(raygen.contains("rayConeSpread, pc.waterAnchor.z"));
        assertTrue(build.contains("-DCAUSTICA_TRANSPARENCY_HQ=0"));
        assertTrue(build.contains("-DCAUSTICA_TRANSPARENCY_HQ=1"));
        assertTrue(build.contains("world_hq.rgen.spv"));
        assertTrue(build.contains("world_base_hq.rgen.spv"));
        assertTrue(build.contains("world_sharc_base_hq.rgen.spv"));
        assertTrue(bringup.contains("highQualityTransparencyShader"));
        assertTrue(bringup.contains("private static String highQualityTransparencyShader(String shader)"));
        assertTrue(bringup.contains("private static String advancedNrdTransportShader(String shader)"));
        assertTrue(bringup.contains("return shader;"));
        assertTrue(config.contains("dlss-rr.high-quality-transparency\", false"));
        String composite = read("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");
        assertTrue(composite.contains("private static boolean highQualityTransparencyEnabled()"));
        assertTrue(composite.contains("persisted experimental setting inert"));
        assertTrue(composite.contains("refreshTransparencyPipelineIfNeeded(ctx)"));
        assertTrue(composite.contains("RT transparency quality active:"));
        assertTrue(composite.contains("RtTerrain.quiesceForResourceReload(ctx)"));
        assertTrue(composite.contains("RtTerrain.pauseForResourceReload()"));
        assertTrue(composite.contains("RtTerrain.resumeAfterResourceReload()"));
        assertTrue(composite.contains("if (ctx != null) {"));
        assertFalse(composite.contains("ctx != null && worldPipeline != null"));
        assertTrue(composite.contains("materialBindingsReady && !reloadRebindRequested"));
        assertFalse(composite.contains("ctx.waitIdle(\"resource reload\")"));
        String terrain = read("src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtTerrain.java");
        assertTrue(terrain.contains("public static void quiesceForResourceReload(RtContext ctx)"));
        assertTrue(terrain.contains("if (INSTANCE.resourceReloadPaused) return"));
        assertTrue(terrain.contains("if (!INSTANCE.resourceReloadPaused && RtMaterialRegistry.INSTANCE.isReady())"));
    }

    @Test
    void advancedTransparencyIsNeutralUntilItHasARealDielectricLayerContract() throws IOException {
        String raygen = read("shaders/world/world.rgen.slang");

        assertTrue(raygen.contains("#define CAUSTICA_NESTED_MEDIA 0"));
        assertTrue(raygen.contains("#define CAUSTICA_LAYERED_OPTICS 0"));
        assertTrue(raygen.contains("#define CAUSTICA_DLSS_TRANSPARENCY_RESOURCES 0"));
        assertTrue(raygen.contains("gTransparencyLayer[pix] = float4(0.0)"));
        assertTrue(raygen.contains("gTransparencyOpacity[pix] = float4(0.0)"));
        assertTrue(raygen.contains("gColorBeforeTransparency[pix] = float4(frameRadiance, 1.0)"));
        assertTrue(raygen.contains("Do not route dielectric reflection through the particle overlay ABI"));
        assertFalse(raygen.contains("gTransparencyLayer[pix] = float4(layeredPremultipliedReflection"));
    }

    @Test
    void standardDlssSpendsItsSinglePrimaryDielectricPathOnTransmission() throws IOException {
        String raygen = read("shaders/world/world.rgen.slang");

        assertTrue(raygen.contains("bool standardPrimaryTransmission = segment == 0"));
        assertTrue(raygen.contains("&& (pc.flags & FRAME_FLAG_RR_GUIDES) != 0u"));
        assertTrue(raygen.contains("chooseReflection = standardPrimaryTransmission"));
        assertTrue(raygen.contains("? dot(transmittedDir, transmittedDir) <= 0.0 : rndf(sampler) < F"));
        assertTrue(raygen.contains("Standard DLSS is explicitly refraction-priority"));
        assertTrue(raygen.contains("bool captureRefractedGuides = (writeMotionGuides && !writeNrdGuides)"));
        assertFalse(raygen.contains("if (standardPrimaryTransmission) throughput *= (1.0 - F)"));
        assertTrue(raygen.contains("conditional transmitted view at full weight"));
    }

    @Test
    void analyticWaterWavePathStaysInsideTheExistingShaderBudget() throws IOException {
        String raygen = read("shaders/world/world.rgen.slang");

        assertFalse(raygen.contains("float waterWaveFootprintWeight"));
        assertFalse(raygen.contains("filteredSlopeRoughness"));
        assertTrue(raygen.contains("waterWaveGradPair(waterDomain, pc.waterParams.w, pc.waterAnchor.z,"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(Path.of(relative)).replace("\r\n", "\n");
    }
}
