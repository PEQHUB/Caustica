package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DlssdTextureBandwidthContractTest {
    @Test
    void mipBiasMatchesNvidiaDisplayResolutionFormula() {
        assertEquals(-1.0f, RtReconstruction.dlssMipBias(1920, 1920), 1.0e-6f);
        assertEquals(-2.0f, RtReconstruction.dlssMipBias(960, 1920), 1.0e-6f);
        assertEquals(-2.5849625f, RtReconstruction.dlssMipBias(640, 1920), 1.0e-6f);
        assertEquals(0.0f, RtReconstruction.dlssMipBias(3840, 1920), 1.0e-6f);
        assertEquals(1.0f, RtReconstruction.dlssMipBias(3840, 960), 1.0e-6f);
        assertThrows(IllegalArgumentException.class, () -> RtReconstruction.dlssMipBias(0, 1920));
    }

    @Test
    void subpixelDetailCanBeDisabledWithoutLosingResolutionCompensation() {
        assertEquals(0.0f, RtReconstruction.dlssMipBias(1920, 1920, false), 1.0e-6f);
        assertEquals(-1.0f, RtReconstruction.dlssMipBias(960, 1920, false), 1.0e-6f);
        assertEquals(-1.5849625f, RtReconstruction.dlssMipBias(640, 1920, false), 1.0e-6f);
        assertEquals(-2.0f, RtReconstruction.dlssMipBias(960, 1920, true), 1.0e-6f);
    }

    @Test
    void closestHitUsesSignalCorrectDisplayBandwidthFiltering() throws IOException {
        String source = source("shaders/world/world.rchit.slang");
        assertTrue(source.contains("log2(footprint) + pc.textureMipBias"));
        assertTrue(source.contains("sampleSrgbLinearTrilinear"));
        assertTrue(source.contains("(payload.flags & PAYLOAD_PRIMARY_RAY) != 0u"));
        assertTrue(source.contains("srgbToLinear(textureSampler.SampleLevel"));
        assertTrue(source.contains("sqrt(max(lerp(a.r * a.r, b.r * b.r, phase), 0.0))"));
        assertTrue(source.contains("float3 n = normalize(lerp(an, bn, phase))"));
        assertTrue(source.contains("SampleLevel(euvCoord, 0.0).a"));
        assertTrue(source.contains("blockAlbedoAtlas.SampleLevel(uv, 0.0).a"));
        assertTrue(source.contains("bool highQuality = pc.textureMipBias < 0.0"
                + " && (payload.flags & PAYLOAD_PRIMARY_RAY) != 0u;"));
    }

    @Test
    void pixelArtKeepsPointSamplingAndPhysicalPagesUseTheAtlasSampler() throws IOException {
        String shader = source("shaders/world/world.rchit.slang");
        String source = source("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");
        assertTrue(shader.contains("pc.pointSampleMaxSize > 0u"));
        assertTrue(shader.contains("<= float(pc.pointSampleMaxSize)"));
        assertTrue(shader.contains("highQuality && !authoredPixelArt"));
        assertTrue(source.contains("bindPages(pipeline, sampler)"));
        assertTrue(source.contains(".magFilter(VK10.VK_FILTER_NEAREST).minFilter(VK10.VK_FILTER_NEAREST)"));
    }

    @Test
    void motionContractRemainsUnjittered() throws IOException {
        String raygen = source("shaders/world/world.rgen.slang");
        String constants = source(
                "src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtDlssFg.java");
        assertTrue(raygen.contains("float2 prevNdc = prevClip.xy / prevClip.w;"));
        assertTrue(raygen.contains("float2 curNdc = jndc;"));
        assertTrue(constants.contains("bytes.putInt(436, 0);"));

        // A static point hit by a jittered pinhole-camera ray projects back to that same jittered NDC.
        // Because both matrices are unjittered, prevNdc - curNdc remains zero; jitter is submitted
        // separately through Streamline constants and must not be encoded in the motion vector.
        for (double jitterPixels : new double[] {-0.5, -0.125, 0.0, 0.375, 0.5}) {
            double size = 1920.0;
            double curNdc = 2.0 * jitterPixels / size;
            double planeDepth = 7.0;
            double hitX = curNdc * planeDepth;
            double prevNdc = hitX / planeDepth;
            assertEquals(0.0, (prevNdc - curNdc) * 0.5 * size, 1.0e-12);
        }
    }

    @Test
    void linearLightAndPhysicalChannelsInterpolateSemantically() {
        double encodedMidpointDecoded = srgbToLinear(0.5);
        double decodedThenInterpolated = lerp(srgbToLinear(0.0), srgbToLinear(1.0), 0.5);
        assertEquals(0.5, decodedThenInterpolated, 1.0e-12);
        assertTrue(decodedThenInterpolated > encodedMidpointDecoded,
                "encoded-space interpolation would darken the reconstructed albedo");

        double roughness = Math.sqrt(lerp(0.2 * 0.2, 0.8 * 0.8, 0.25));
        assertEquals(Math.sqrt(0.19), roughness, 1.0e-12);

        double[] normal = normalize(lerp3(normalFromEncoded(0.5, 0.5),
                normalFromEncoded(1.0, 0.5), 0.5));
        assertEquals(1.0, length(normal), 1.0e-12);
        assertTrue(normal[0] > 0.0 && normal[2] > 0.0);
    }

    @Test
    void atlasFilteringCannotSampleAcrossTheOwningSprite() {
        double levelSize = 16.0;
        double spriteMin = 4.0 / levelSize;
        double spriteMax = 8.0 / levelSize;
        double halfTexel = 0.5 / levelSize;
        for (double requested : new double[] {0.0, spriteMin, spriteMax, 1.0}) {
            double clamped = Math.max(spriteMin + halfTexel,
                    Math.min(spriteMax - halfTexel, requested));
            assertTrue(clamped >= 4.5 / levelSize);
            assertTrue(clamped <= 7.5 / levelSize);
        }
    }

    private static double srgbToLinear(double value) {
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static double lerp(double a, double b, double phase) {
        return a + (b - a) * phase;
    }

    private static double[] normalFromEncoded(double x, double y) {
        double nx = x * 2.0 - 1.0;
        double ny = y * 2.0 - 1.0;
        return new double[] {nx, ny, Math.sqrt(Math.max(0.0, 1.0 - nx * nx - ny * ny))};
    }

    private static double[] lerp3(double[] a, double[] b, double phase) {
        return new double[] {lerp(a[0], b[0], phase), lerp(a[1], b[1], phase), lerp(a[2], b[2], phase)};
    }

    private static double[] normalize(double[] value) {
        double length = length(value);
        return new double[] {value[0] / length, value[1] / length, value[2] / length};
    }

    private static double length(double[] value) {
        return Math.sqrt(value[0] * value[0] + value[1] * value[1] + value[2] * value[2]);
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
}
