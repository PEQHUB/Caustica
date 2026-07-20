package dev.comfyfluffy.caustica.rt.entity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class RtEntityCaptureParityTest {
    @Test
    void exactSubmissionOracleAcceptsBitwiseIdentity() {
        RtEntityCapture actual = quad(1f);
        RtEntityCapture reference = quad(1f);

        assertDoesNotThrow(() -> actual.assertSubmissionBitwiseIdentical(
                0, 0, 0, 0, reference, "identical"));
    }

    @Test
    void exactSubmissionOracleRejectsOneBitPositionDelta() {
        RtEntityCapture actual = quad(1f);
        RtEntityCapture reference = quad(Math.nextUp(1f));

        assertThrows(IllegalStateException.class, () -> actual.assertSubmissionBitwiseIdentical(
                0, 0, 0, 0, reference, "one-bit delta"));
    }

    @Test
    void exactSubmissionOracleDoesNotNormalizeSignedZero() {
        RtEntityCapture actual = quad(0f);
        RtEntityCapture reference = quad(-0f);

        assertThrows(IllegalStateException.class, () -> actual.assertSubmissionBitwiseIdentical(
                0, 0, 0, 0, reference, "signed zero"));
    }

    private static RtEntityCapture quad(float firstX) {
        RtEntityCapture capture = new RtEntityCapture();
        capture.addDirectQuad(
                new float[] {firstX, 1f, 1f, 0f},
                new float[] {0f, 0f, 1f, 1f},
                new float[] {0f, 0f, 0f, 0f},
                new float[] {0f, 1f, 1f, 0f},
                new float[] {0f, 0f, 1f, 1f},
                0f, 0f, 1f, -1);
        return capture;
    }
}
