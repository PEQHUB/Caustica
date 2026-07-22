package dev.comfyfluffy.caustica.rt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerrainJobOrderTest {
    @Test
    void interactiveTaskJumpsCurrentStreamingBacklog() {
        long interactiveSequence = 33L;
        for (long streamingSequence = 1L; streamingSequence <= 32L; streamingSequence++) {
            assertTrue(TerrainJobOrder.compare(true, interactiveSequence,
                    false, streamingSequence) < 0);
        }
    }

    @Test
    void oldStreamingTaskEventuallyBeatsNewInteractiveTasks() {
        long oldStreamingSequence = 1L;
        long sufficientlyNewInteractive = 1L + TerrainJobOrder.INTERACTIVE_SEQUENCE_BOOST;
        assertTrue(TerrainJobOrder.compare(false, oldStreamingSequence,
                true, sufficientlyNewInteractive) < 0);
    }
}
