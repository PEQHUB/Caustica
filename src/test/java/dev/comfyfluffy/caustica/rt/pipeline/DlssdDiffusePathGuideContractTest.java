package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DlssdDiffusePathGuideContractTest {
    @Test
    void firstDiffuseContinuationProducesTheCombinedStreamlineGuide() throws Exception {
        String raygen = Files.readString(Path.of("shaders/world/world.rgen.slang"));
        assertTrue(raygen.contains("gDiffuseRayDirectionHitDistance"));
        assertTrue(raygen.contains("gv_diffuseRayDirectionHitDistance.w = max(payload.hitT, 0.0)"));
        assertTrue(raygen.contains("FRAME_FLAG_DIFFUSE_PATH_GUIDE"));
    }

    @Test
    void javaSubmitsStreamlineBufferType46OnlyInBMode() throws Exception {
        String rr = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtDlssRr.java"));
        assertTrue(rr.contains("BUFFER_DIFFUSE_RAY_DIRECTION_HIT_DISTANCE = 46"));
        assertTrue(rr.contains("if (diffusePathGuide)"));
        assertTrue(rr.contains("requiredResourceCount(diffusePathGuide)"));

        String bridge = Files.readString(Path.of("native/streamline_bridge/streamline_bridge.cpp"));
        assertTrue(bridge.contains("isSupportedDlssdResourceCount(resource_count)"));
        assertTrue(bridge.contains("SLBRIDGE_BUFFER_DIFFUSE_RAY_DIRECTION_HIT_DISTANCE"));
    }

    @Test
    void extraGuideKeepsMaterialAndSkyDescriptorsAfterTheStorageImages() throws Exception {
        String composite = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java"));
        String closestHit = Files.readString(Path.of("shaders/world/world.rchit.slang"));
        String miss = Files.readString(Path.of("shaders/world/world.rmiss.slang"));

        assertTrue(composite.contains("GUIDE_COUNT = 11"));
        assertTrue(closestHit.contains("[[vk::binding(14, 0)]] Sampler2D blockSpecAtlas"));
        assertTrue(closestHit.contains("[[vk::binding(15, 0)]] Sampler2D blockNormalAtlas"));
        assertTrue(miss.contains("[[vk::binding(16, 0)]] Sampler2D celestialsAtlas"));
    }
}
