package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class DlssdDiffusePathGuideContractTest {
    @Test
    void firstDiffuseContinuationProducesTheCombinedStreamlineGuide() throws Exception {
        String raygen = Files.readString(Path.of("shaders/world/world.rgen.slang"));
        assertTrue(raygen.contains("gDiffuseRayDirectionHitDistance"));
        assertTrue(raygen.contains("gv_diffuseRayDirectionHitDistance.w = max(payload.hitT, 0.0)"));
        assertTrue(raygen.contains("FRAME_FLAG_DIFFUSE_PATH_GUIDE"));
        assertTrue(raygen.contains("captureGuides && gv_diffuseGuidePending"));
        assertTrue(raygen.contains("captureGuides && scatteringDepth == 0"));
        assertTrue(!raygen.contains("captureGuides && segment == 1 && gv_diffuseGuidePending"));
        assertTrue(!raygen.matches("(?s).*captureGuides && segment == 0\\s*"
                + "&& \\(pc.flags & FRAME_FLAG_DIFFUSE_PATH_GUIDE\\).*"));
    }

    @Test
    void javaSubmitsStreamlineBufferType46OnlyInBMode() throws Exception {
        String rr = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtDlssRr.java"));
        assertTrue(rr.contains("BUFFER_DIFFUSE_RAY_DIRECTION_HIT_DISTANCE = 46"));
        assertTrue(rr.contains("if (diffusePathGuide)"));
        assertTrue(rr.contains("requiredResourceCount(diffusePathGuide, layeredTransparency, particleHistory)"));

        String bridge = Files.readString(Path.of("native/streamline_bridge/streamline_bridge.cpp"));
        assertTrue(bridge.contains("isSupportedDlssdResourceCount(resource_count)"));
        assertTrue(bridge.contains("SLBRIDGE_BUFFER_DIFFUSE_RAY_DIRECTION_HIT_DISTANCE"));
    }

    @Test
    void reconstructionSupersetHasOneStableNonOverlappingSkyAbi() throws Exception {
        String composite = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java"));
        String pipeline = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtPipeline.java"));
        String raygen = Files.readString(Path.of("shaders/world/world.rgen.slang"));
        String closestHit = Files.readString(Path.of("shaders/world/world.rchit.slang"));
        String miss = Files.readString(Path.of("shaders/world/world.rmiss.slang"));
        String skyLut = Files.readString(Path.of("shaders/world/world_sky_lut.slang"));

        assertTrue(composite.contains("BASE_GUIDE_COUNT = 11"));
        assertTrue(composite.contains("NRD_GUIDE_COUNT = 17"));
        assertTrue(closestHit.contains("[[vk::binding(1, 1)]] Sampler2D materialSurface0Tex[]"));
        assertTrue(closestHit.contains("[[vk::binding(2, 1)]] Sampler2D materialNormalAoTex[]"));
        assertTrue(closestHit.contains("[[vk::binding(3, 1)]] Sampler2D materialSurface1Tex[]"));
        int highestGuideBinding = highestSetZeroBinding(raygen, 3, 19);
        int skyBinding = shaderBinding(miss, "celestialsAtlas");
        int skyLutBinding = shaderBinding(skyLut, "skyViewLut");
        assertTrue(highestGuideBinding == 19);
        assertTrue(skyBinding > highestGuideBinding);
        assertTrue(skyLutBinding > skyBinding);
        assertTrue(pipeline.contains("int skyBinding = skyAtlas ? " + skyBinding + " : -1"));
        assertTrue(pipeline.contains("int skyLutBinding = skyAtlas ? " + skyLutBinding + " : -1"));
    }

    private static int shaderBinding(String source, String resource) {
        Matcher matcher = Pattern.compile("\\[\\[vk::binding\\((\\d+), 0\\)\\]\\][^;]*\\b"
                + Pattern.quote(resource) + "\\b").matcher(source);
        assertTrue(matcher.find(), "missing set-0 binding for " + resource);
        return Integer.parseInt(matcher.group(1));
    }

    private static int highestSetZeroBinding(String source, int minimum, int maximum) {
        Matcher matcher = Pattern.compile("\\[\\[vk::binding\\((\\d+), 0\\)\\]\\]").matcher(source);
        int highest = -1;
        while (matcher.find()) {
            int binding = Integer.parseInt(matcher.group(1));
            if (binding >= minimum && binding <= maximum) highest = Math.max(highest, binding);
        }
        return highest;
    }
}
