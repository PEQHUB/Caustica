package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class FrameDeadlinePacerTest {
    @Test
    void advancesFromTheAbsoluteDeadlineWithoutAccumulatingDrift() {
        assertEquals(20_000L, FrameDeadlinePacer.advanceDeadline(10_000L, 10_500L, 10_000L));
    }

    @Test
    void dropsMissedDeadlinesInsteadOfBurstingCatchUpFrames() {
        assertEquals(45_001L, FrameDeadlinePacer.advanceDeadline(10_000L, 35_001L, 10_000L));
    }
}
