package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DlssgRecoveryContractTest {
    @Test
    void outOfDatePresentIsRecoverable() {
        DlssgTransitionReason reason = DlssgErrorClassifier.classifyPresent(-1000001004);
        assertEquals(DlssgTransitionReason.PRESENT_OUT_OF_DATE, reason);
        assertTrue(DlssgErrorClassifier.recoverable(reason));
    }

    @Test
    void discontinuityDetectorDoesNotTreatZeroSamplesAsOverflow() {
        FrameDiscontinuityDetector detector = new FrameDiscontinuityDetector();
        detector.onSourceFrameStart(1L, true);
        detector.onSourceFrameStart(1L, true);
        assertEquals(35_000_000L, detector.thresholdNs());
    }
}
