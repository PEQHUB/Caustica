package dev.comfyfluffy.caustica.rt.pipeline;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards atomic terrain publication: completed terrain is never replaced by a residency curtain. */
final class BorderFogContractTest {
    @Test
    void completedTerrainNeverUsesPresentationFogOrChunkFade() throws Exception {
        String common = read("shaders/world/world_common.slang");
        String skyLut = read("shaders/world/world_sky_lut.slang");
        String raygen = read("shaders/world/world.rgen.slang");
        String closestHit = read("shaders/world/world.rchit.slang");
        String miss = read("shaders/world/world.rmiss.slang");
        String composite = read("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");
        String pipeline = read("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtPipeline.java");

        assertTrue(common.contains("PAYLOAD_PRIMARY_RAY = 1u << 11u"));
        assertFalse(common.contains("public float4 borderFogColor"));
        assertFalse(common.contains("public float4 borderFogParams"));
        assertFalse(common.contains("public float4 chunkFade"));
        assertFalse(common.contains("PAYLOAD_CHUNK_REVEAL_ACTIVE"));
        assertFalse(skyLut.contains("atmosphericBoundaryFog"));
        assertTrue(raygen.contains("primaryVisibilityRay = false;"));
        assertFalse(raygen.contains("primaryHitDistance > fogStart"));
        assertFalse(raygen.contains("fogWeight"));
        assertFalse(raygen.contains("atmosphericBoundaryFog"));
        assertFalse(miss.contains("primaryRay && !aboveHorizon && !earthAtmosphere"));
        assertTrue(miss.contains("bool presentationSky = primaryRay || filterSky"));
        assertTrue(miss.contains("if (!presentationSky && earthAtmosphere && !aboveHorizon)"));
        assertTrue(pipeline.contains("VK_SHADER_STAGE_RAYGEN_BIT_KHR | VK_SHADER_STAGE_MISS_BIT_KHR"));
        assertFalse(composite.contains("EnvironmentAttributes.FOG_COLOR"));
        assertFalse(composite.contains("(renderDistanceChunks - 3.5f) * 16.0f"));
        assertFalse(raygen.contains("chunkFadeThreshold"));
        assertFalse(raygen.contains("gv_primaryChunkFade"));
        assertTrue(raygen.replace("\r\n", "\n").contains("inline\nvoid setPrimaryMissGuides"));
        assertTrue(raygen.contains("gv_hitCamRel = rayDirection * 1.0e6"));
        assertTrue(raygen.contains("gv_albedo = SKY_DIFF_ALBEDO"));
        assertTrue(raygen.contains("gv_specAlb = SKY_SPEC_ALBEDO"));
        assertFalse(closestHit.contains("chunkRevealThreshold"));
        assertFalse(closestHit.contains("publishedTime"));
        assertFalse(composite.contains("chunkSectionFadeInTime"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path)).replace("\r\n", "\n");
    }
}
