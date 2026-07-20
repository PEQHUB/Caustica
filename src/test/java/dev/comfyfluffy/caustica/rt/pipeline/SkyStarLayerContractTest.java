package dev.comfyfluffy.caustica.rt.pipeline;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class SkyStarLayerContractTest {
    @Test
    void sharedEvaluationPreservesDeterministicLayoutAndAddsCircularFootprintCoverage() throws Exception {
        String stars = Files.readString(Path.of("shaders/world/world_stars.slang"));
        String layer = Files.readString(Path.of("shaders/display/sky_stars.comp.slang"));
        assertTrue(stars.contains("starHash13"));
        assertTrue(stars.contains("starRotateAxis"));
        assertTrue(stars.contains("SKY_STAR_CELLS = 21.0"));
        assertTrue(stars.contains("starCircularCoverage"));
        assertTrue(stars.contains("float edge = max(pixelFootprint * 0.5"));
        assertTrue(stars.contains("smoothstep(max(size - edge, 0.0), size + edge, radius)"));
        assertTrue(layer.contains("maxAngularFootprint"));
        assertTrue(layer.contains("circularStars"));
        assertTrue(layer.contains("cornerUv"));
        assertTrue(layer.contains("primarySkyDepth"));
        assertTrue(layer.contains("- world.jitter"));
    }

    @Test
    void primarySuppressionAndMaskAreScopedToRealtimePrimarySky() throws Exception {
        String miss = Files.readString(Path.of("shaders/world/world.rmiss.slang"));
        String raygen = Files.readString(Path.of("shaders/world/world.rgen.slang"));
        String common = Files.readString(Path.of("shaders/world/world_common.slang"));
        assertTrue(common.contains("FRAME_FLAG_DIRECT_SKY_STARS = 1u << 14u"));
        assertTrue(miss.contains("bool directSkyStarLayer = (pc.flags & FRAME_FLAG_DIRECT_SKY_STARS) != 0u;"));
        assertTrue(miss.contains("(!primaryRay || !directSkyStarLayer)"));
        assertTrue(raygen.contains("binding(9, 0)"));
        assertTrue(raygen.contains("gPrimarySkyMask"));
        assertTrue(raygen.contains("gv_primarySky = true"));
        assertTrue(raygen.contains("gPrimarySkyMask[pix] = gv_primarySky ? 1.0 : 0.0"));
        assertTrue(raygen.contains("primaryOnly || writeSkyMask"));
    }

    @Test
    void descriptorReuseAndPresentationOrderingStayExplicit() throws Exception {
        String composite = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java"));
        String pipeline = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtSkyStarLayerPipeline.java"));
        assertTrue(composite.contains("setExtraStorageImage(6, gPrimarySkyMask.view)"));
        assertTrue(composite.contains("setImages(displayInput.view, gPrimarySkyMask.view, gDepth.view)"));
        assertTrue(composite.contains("VK10.VK_FORMAT_R16_SFLOAT, \"primary sky star-layer mask\""));
        int layer = composite.indexOf("skyStarLayerPipeline.dispatch");
        int exposure = composite.indexOf("exposure.record", layer);
        int bloom = composite.indexOf("bloomPipeline.dispatch", layer);
        assertTrue(layer >= 0 && exposure > layer && bloom > layer);
        assertTrue(composite.contains("(flags & FRAME_FLAG_DIRECT_SKY_STARS) != 0"));
        assertTrue(pipeline.contains("sky_stars.comp.spv"));
        assertTrue(pipeline.contains(".size(24)"));
        assertTrue(pipeline.contains("vkCmdDispatch(commandBuffer, (outputWidth + 15) / 16"));
    }

    @Test
    void geometryEdgesAreRejectedBeforeAdditiveSceneLinearWrite() throws Exception {
        String layer = Files.readString(Path.of("shaders/display/sky_stars.comp.slang"));
        int mask = layer.indexOf("primarySkyMask.Load");
        int add = layer.indexOf("scene.rgb + star");
        assertTrue(mask >= 0 && add > mask);
        assertTrue(layer.contains("primarySkyDepth.Load"));
        assertTrue(layer.contains("if (minSource.x < 0 || minSource.y < 0"));
        assertTrue(layer.contains("outputImage[pixel]"));
    }
}
