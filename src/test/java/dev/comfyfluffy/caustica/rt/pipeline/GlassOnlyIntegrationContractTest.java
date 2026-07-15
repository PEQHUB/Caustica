package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Prevents glass work from silently restoring the rejected low-light or global hybrid pipelines. */
final class GlassOnlyIntegrationContractTest {
    @Test
    void terrainClassifiesGlassWithoutChangingTheWaterBucket() throws IOException {
        String terrain = source("src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtTerrain.java");
        assertTrue(terrain.contains("OPTICAL_THIN_GLASS = 2"));
        assertTrue(terrain.contains("OPTICAL_SOLID_GLASS = 3"));
        assertTrue(terrain.contains("block == Blocks.GLASS_PANE"));
        assertTrue(terrain.contains("block == Blocks.GLASS"));
        assertTrue(terrain.contains("q.translucent ? q.opticalClass : 0f"));
        assertTrue(terrain.contains("Geom g = water ? cur.water() : cur.opaque()"));
    }

    @Test
    void glassIsCompactAndWaterKeepsTheCoreTransport() throws IOException {
        String raygen = source("shaders/world/world.rgen.slang");
        String closestHit = source("shaders/world/world.rchit.slang");

        assertTrue(raygen.contains("F = 2.0 * F / (1.0 + F)"));
        assertTrue(raygen.contains("float3 transmittedDir = thinPane ? rd : refract"));
        assertTrue(raygen.contains("bool inWater = (pc.flags & 1u) != 0u;"));
        assertTrue(raygen.contains("throughput *= exp(-waterExt * payload.hitT);"));
        assertTrue(raygen.contains("waterExt = waterExtinction(surfaceWaterTint);"));
        assertTrue(raygen.contains("inWater = surfaceWaterEntering;"));
        assertTrue(closestHit.contains("bool plainWater = pr.tint.w > 0.5 && pr.tint.w < 1.5"));

        assertFalse(raygen.contains("MAX_MEDIUM_DEPTH"));
        assertFalse(raygen.contains("pathMedia"));
        assertTrue(raygen.contains("void opticalGuideHit"));
        assertTrue(raygen.contains("RAY_FLAG_CULL_BACK_FACING_TRIANGLES"));
        assertTrue(raygen.contains("gv_hitCamRel = destinationHitCamRel"));
        assertTrue(raygen.contains("gv_albedo = destinationDiffuseAlbedo"));
        assertTrue(raygen.contains("gv_specAlb = destinationSpecAlbedo"));
        assertTrue(raygen.contains("gv_opticalGuideMode == 3u"));
        assertEquals(2, occurrences(raygen, "opticalGuideHit(")); // definition plus one shared call site
        assertFalse(raygen.contains("world_dlssd_guides"));
        assertFalse(closestHit.contains("PAYLOAD_SHADOW_QUERY"));
    }

    @Test
    void rejectedLowLightAndSparseOpticalInfrastructureStayAbsent() throws IOException {
        String raygen = source("shaders/world/world.rgen.slang");
        String common = source("shaders/world/world_common.slang");
        String pipeline = source("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtPipeline.java");
        String composite = source("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");

        for (String text : new String[] {raygen, common, pipeline, composite}) {
            assertFalse(text.contains("emissiveTableAddr"));
            assertFalse(text.contains("EMITTER_NEE_PHASES"));
            assertFalse(text.contains("opticalMask"));
            assertFalse(text.contains("world_optical"));
        }
    }

    private static String source(String relative) throws IOException {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path candidate = cursor.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate).replace("\r\n", "\n");
            }
            cursor = cursor.getParent();
        }
        throw new IOException("Could not locate " + relative);
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) {
            count++;
        }
        return count;
    }
}
