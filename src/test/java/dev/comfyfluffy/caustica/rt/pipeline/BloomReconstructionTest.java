package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Contract and numerical checks for bloom tent upsampling. */
final class BloomReconstructionTest {
    @Test
    void shaderUsesCenteredContinuousTentWithoutChangingBlendWeights() throws IOException {
        String source = shader();

        assertTrue(source.contains("(vec2(p) + 0.5) * vec2(sourceSize) / vec2(destinationSize) - 0.5"));
        assertTrue(source.contains("vec2(2.0) - abs(vec2(samplePixel) - center)"));
        assertTrue(source.contains("return color / weightSum;"));
        assertFalse(source.contains("tentEighth(p / 2)"));
        assertFalse(source.contains("tentQuarter(p / 2)"));
        assertTrue(source.contains("loadQuarter(p) * 0.55 + wide * 0.45"));
        assertTrue(source.contains("loadHalf(p) * 0.35 + medium * 0.65"));
    }

    @Test
    void normalizedTentPreservesConstantRadianceForOddExtents() {
        double[] source = new double[3 * 5];
        Arrays.fill(source, 7.25);

        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 7; x++) {
                assertEquals(7.25, reconstruct(source, 3, 5, x, y, 7, 9), 1.0e-12);
            }
        }
    }

    @Test
    void integerCenteredTentRetainsOriginalBlurWidth() {
        double[] source = {0.0, 0.0, 1.0, 0.0, 0.0};

        assertEquals(0.0, reconstruct(source, 5, 1, 0, 0, 5, 1), 1.0e-12);
        assertEquals(0.25, reconstruct(source, 5, 1, 1, 0, 5, 1), 1.0e-12);
        assertEquals(0.5, reconstruct(source, 5, 1, 2, 0, 5, 1), 1.0e-12);
        assertEquals(0.25, reconstruct(source, 5, 1, 3, 0, 5, 1), 1.0e-12);
        assertEquals(0.0, reconstruct(source, 5, 1, 4, 0, 5, 1), 1.0e-12);
    }

    @Test
    void continuousCenteringDoesNotRepeatIntegerPixelPairs() {
        double[] source = {
                0.0, 1.0, 0.0,
                0.0, 1.0, 0.0,
                0.0, 1.0, 0.0
        };

        double left = reconstruct(source, 3, 3, 0, 2, 5, 5);
        double next = reconstruct(source, 3, 3, 1, 2, 5, 5);

        assertNotEquals(left, next, 1.0e-12);
        assertEquals(left, reconstruct(source, 3, 3, 4, 2, 5, 5), 1.0e-12);
        assertEquals(next, reconstruct(source, 3, 3, 3, 2, 5, 5), 1.0e-12);
    }

    private static double reconstruct(double[] source, int sourceWidth, int sourceHeight,
            int destinationX, int destinationY, int destinationWidth, int destinationHeight) {
        double centerX = (destinationX + 0.5) * sourceWidth / destinationWidth - 0.5;
        double centerY = (destinationY + 0.5) * sourceHeight / destinationHeight - 0.5;
        int firstX = (int) Math.floor(centerX) - 1;
        int firstY = (int) Math.floor(centerY) - 1;
        double value = 0.0;
        double weightSum = 0.0;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int sampleX = firstX + x;
                int sampleY = firstY + y;
                double weightX = Math.max(0.0, 2.0 - Math.abs(sampleX - centerX));
                double weightY = Math.max(0.0, 2.0 - Math.abs(sampleY - centerY));
                double weight = weightX * weightY;
                int clampedX = Math.max(0, Math.min(sourceWidth - 1, sampleX));
                int clampedY = Math.max(0, Math.min(sourceHeight - 1, sampleY));
                value += source[clampedY * sourceWidth + clampedX] * weight;
                weightSum += weight;
            }
        }
        return value / weightSum;
    }

    private static String shader() throws IOException {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path candidate = cursor.resolve("shaders").resolve("display").resolve("bloom.comp");
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate).replace("\r\n", "\n");
            }
            cursor = cursor.getParent();
        }
        throw new IOException("Could not locate shaders/display/bloom.comp");
    }
}
