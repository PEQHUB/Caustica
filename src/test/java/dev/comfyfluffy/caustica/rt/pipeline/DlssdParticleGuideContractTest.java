package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DlssdParticleGuideContractTest {
    @Test
    void particlesRejectCurrentAndVacatedFootprintHistory() throws IOException {
        String raygen = read("shaders/world/world.rgen.slang");
        String mask = read("shaders/display/dlssd_disocclusion.comp");
        String pipeline = read("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtDlssdDisocclusionPipeline.java");
        String composite = read("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");
        String config = read("src/main/java/dev/comfyfluffy/caustica/CausticaConfig.java");
        String build = read("build.gradle");

        int particleBranch = raygen.indexOf("if (material == MATERIAL_PARTICLE)");
        int particleResponsivity = raygen.indexOf("gv_animatedGuide = 2.0;", particleBranch);
        int nextMaterialBranch = raygen.indexOf("if (material == MATERIAL_WATER)", particleBranch);
        assertTrue(particleBranch >= 0 && particleResponsivity > particleBranch);
        assertTrue(nextMaterialBranch < 0 || particleResponsivity < nextMaterialBranch);

        assertTrue(mask.contains("imageStore(currentTemporalGuideHistory, pixel, vec4(particle))"));
        assertTrue(mask.contains("imageStore(particleHintImage, pixel, vec4(particle))"));
        assertTrue(mask.contains("particleAt(samplePixel)"));
        assertTrue(mask.contains("previousParticleAt(samplePixel)"));
        assertTrue(mask.contains("max(disocclusion, particleHistoryReject)"));

        assertTrue(mask.contains("#if CAUSTICA_PARTICLE_TEMPORAL_HISTORY"));
        assertTrue(build.contains("-DCAUSTICA_PARTICLE_TEMPORAL_HISTORY=0"));
        assertTrue(build.contains("-DCAUSTICA_PARTICLE_TEMPORAL_HISTORY=1"));
        assertTrue(build.contains("dlssd_disocclusion_particle_history.comp.spv"));
        assertTrue(pipeline.contains("particleTemporalHistory ? 10 : BASE_IMAGE_BINDINGS"));
        assertTrue(pipeline.contains("PARTICLE_HISTORY_SHADER"));
        assertTrue(pipeline.contains("temporalGuideHistoryA, long temporalGuideHistoryB"));
        assertTrue(composite.contains("gTemporalGuideHistoryA"));
        assertTrue(composite.contains("gTemporalGuideHistoryB"));
        assertTrue(composite.contains("if (particleTemporalHistory)"));
        assertTrue(composite.contains("particleTemporalHistory ? gParticleHint.view : 0L"));
        assertTrue(config.contains("dlss-rr.particle-temporal-history\", false"));
        assertFalse(config.contains("dlss-rr.particle-temporal-history\", true"));
        assertFalse(raygen.contains("if (encounteredAnimatedWater) gv_animatedGuide = 1.0"));
    }

    @Test
    void disabledVariantHasNoParticleAllocationTagOrNeighborhoodPath() throws IOException {
        String mask = read("shaders/display/dlssd_disocclusion.comp");
        String pipeline = read("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtDlssdDisocclusionPipeline.java");
        String composite = read("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");
        String rr = read("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtDlssRr.java");

        int historyGuard = mask.indexOf("#if CAUSTICA_PARTICLE_TEMPORAL_HISTORY");
        int particleBinding = mask.indexOf("layout(binding = 9");
        int historyGuardEnd = mask.indexOf("#endif", particleBinding);
        assertTrue(historyGuard >= 0 && particleBinding > historyGuard && historyGuardEnd > particleBinding);
        int particleHelper = mask.indexOf("float particleAt(ivec2 pixel)");
        int helperGuard = mask.lastIndexOf("#if CAUSTICA_PARTICLE_TEMPORAL_HISTORY", particleHelper);
        int helperGuardEnd = mask.indexOf("#endif", particleHelper);
        assertTrue(helperGuard >= 0 && particleHelper > helperGuard && helperGuardEnd > particleHelper);
        assertTrue(pipeline.contains("private static final int BASE_IMAGE_BINDINGS = 7"));
        assertTrue(pipeline.contains("particleTemporalHistory\n                        ? new long[]"));
        assertTrue(composite.contains("if (particleTemporalHistory) {"));
        assertTrue(rr.contains("if (particleHistory) {"));
        assertFalse(rr.contains("writeResource(resources, 9, particleHint"));
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
