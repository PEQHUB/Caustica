package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DlssdWaterGuideContractTest {
    @Test
    void animatedWaterReachesStreamlineBiasHintEndToEnd() throws IOException {
        String raygen = read("shaders/world/world.rgen.slang");
        String mask = read("shaders/display/dlssd_disocclusion.comp");
        String pipeline = read("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtDlssdDisocclusionPipeline.java");
        String composite = read("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");
        String rr = read("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtDlssRr.java");

        assertTrue(raygen.contains("gAnimatedGuide[pix] = gv_animatedGuide"));
        assertTrue(raygen.contains("interfacePos.xz + pc.waterAnchor.xy"));
        assertTrue(raygen.contains("interfaceNormal = applyWaterWaves(interfaceNormal, waterDomain, waterWaveTime)"));
        assertTrue(raygen.contains("encounteredAnimatedWater = true"));
        assertTrue(raygen.contains("if (encounteredAnimatedWater) gv_animatedGuide = 1.0"));
        assertTrue(raygen.indexOf("if (encounteredAnimatedWater) gv_animatedGuide = 1.0")
                < raygen.indexOf("if (destinationValid)"));
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
    void layeredRefractionMovesTheOrdinaryGuideTupleToOneDestination() throws IOException {
        String raygen = read("shaders/world/world.rgen.slang");
        int destination = raygen.indexOf("if (destinationValid) {");
        int failure = raygen.indexOf("} else {\n            // No coherent transmitted layer", destination);
        String validPath = raygen.substring(destination, failure);

        assertTrue(validPath.contains("gv_motionHitCamRel = destinationHitCamRel"));
        assertTrue(validPath.contains("gv_albedo = destinationDiffuseAlbedo"));
        assertTrue(validPath.contains("#if CAUSTICA_LAYERED_OPTICS"));
        assertTrue(validPath.contains("gv_hitCamRel = destinationHitCamRel"));
        assertTrue(validPath.contains("gv_normal = destinationNormal"));
        assertTrue(validPath.contains("gv_rough = destinationRoughness"));
        assertTrue(validPath.contains("gv_specAlb = destinationSpecularAlbedo"));
        assertTrue(raygen.contains("matching Streamline's documented transparency-layer contract"));
        assertTrue(raygen.contains("if (writeRrGuides) specMotion = motion"));
        assertTrue(raygen.contains("if (encounteredAnimatedWater) gv_animatedGuide = 1.0"));
        assertTrue(raygen.contains("until a true previous-time optical walk exists"));
    }

    @Test
    void highQualityWaterKeepsTheBaselineReconstructionContract() throws IOException {
        String raygen = read("shaders/world/world.rgen.slang");
        String build = read("build.gradle");
        String bringup = read("src/main/java/dev/comfyfluffy/caustica/rt/RtDeviceBringup.java");
        String config = read("src/main/java/dev/comfyfluffy/caustica/CausticaConfig.java");

        assertTrue(raygen.contains("#define CAUSTICA_TRANSPARENCY_HQ 0"));
        assertTrue(raygen.contains("if (waterWaves) gv_animatedGuide = 1.0"));
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
        assertTrue(bringup.contains("if (!RtReconstruction.usesDlss()"));
        assertTrue(config.contains("dlss-rr.high-quality-transparency\", false"));
        String composite = read("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");
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
    void highQualityTransparencySplitsTheFirstDielectricIntoDeterministicLayers() throws IOException {
        String raygen = read("shaders/world/world.rgen.slang");

        assertTrue(raygen.contains("#if CAUSTICA_TRANSPARENCY_HQ && !CAUSTICA_OFFLINE"
                + " && !CAUSTICA_SHARC_UPDATE"));
        assertTrue(raygen.contains("#if CAUSTICA_NESTED_MEDIA && !CAUSTICA_NRD"));
        assertTrue(raygen.contains("#define CAUSTICA_PRIMARY_DIELECTRIC_SPLIT 1"));
        assertTrue(raygen.contains("#define CAUSTICA_PRIMARY_DIELECTRIC_SPLIT 0"));
        assertTrue(raygen.contains("if (segment == 0 && primaryOpticalSample != 0u)"));
        assertTrue(raygen.contains("PRIMARY_OPTICAL_TRANSMISSION, primaryDielectricHit"));
        assertTrue(raygen.contains("PRIMARY_OPTICAL_REFLECTION, reflectionDielectricHit"));
        assertTrue(raygen.contains("if (primaryDielectricHit && !primaryOnly)"));
        assertTrue(raygen.contains("sampleRadiance = F * reflectionRadiance + (1.0 - F) * transmissionRadiance"));
        assertTrue(raygen.contains("layeredPremultipliedReflection += F * reflectionRadiance"));
        assertTrue(raygen.contains("layeredPremultipliedTransmission += (1.0 - F) * transmissionRadiance"));
        assertTrue(raygen.contains("layeredPremultipliedTransmission / (1.0 - layeredOpacity)"));
        assertTrue(raygen.contains("gColorBeforeTransparency[pix] = float4(layeredBaseRadiance, 1.0)"));
        assertTrue(raygen.contains("outImage[pix] = float4(frameRadiance, 1.0)"));
        assertTrue(raygen.contains("sampler.state = sampleHash(sampler.state ^ 0xa511e9b3u)"));
        assertTrue(raygen.contains("sampler.branchSalt = sampleHash(sampler.branchSalt ^ 0x6c8e9cf5u)"));
        assertTrue(raygen.contains("+ sampler.branchSalt"));
        assertTrue(raygen.contains("PathSampler reflectionSampler = sampler"));
        assertTrue(raygen.contains("float4 mediumIorStack = float4(1.0)"));
        assertTrue(raygen.contains("solidDielectricExtinction(glassTint)"));
        assertTrue(raygen.contains("gv_specAlb = float3(F, F, F)"));
        assertTrue(raygen.contains("static const float WATER_GUIDE_ROUGH = 0.02"));
        assertTrue(raygen.contains("static const float GLASS_GUIDE_ROUGH = 0.04"));
        assertFalse(raygen.contains("static const float WATER_GUIDE_ROUGH = 0.0;"));
        assertFalse(raygen.contains("static const float GLASS_GUIDE_ROUGH = 0.0;"));
    }

    @Test
    void multiSampleLayerDecompositionExactlyRecomposesCorrelatedFresnel() {
        double f0 = 0.1, t0 = 10.0, r0 = 2.0;
        double f1 = 0.9, t1 = 1.0, r1 = 8.0;
        double finalAverage = 0.5 * (f0 * r0 + (1.0 - f0) * t0
                + f1 * r1 + (1.0 - f1) * t1);
        double opacity = 0.5 * (f0 + f1);
        double layer = 0.5 * (f0 * r0 + f1 * r1);
        double premultipliedTransmission = 0.5 * ((1.0 - f0) * t0 + (1.0 - f1) * t1);
        double base = premultipliedTransmission / (1.0 - opacity);
        assertEquals(finalAverage, layer + (1.0 - opacity) * base, 1.0e-12);
    }

    private static String read(String relative) throws IOException {
        return Files.readString(Path.of(relative)).replace("\r\n", "\n");
    }
}
