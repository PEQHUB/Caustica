package dev.comfyfluffy.caustica.rt.terrain;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TerrainCompletionFairnessTest {
    @Test
    void normalCompletionWinsAfterFourInteractiveResults() {
        TerrainCompletionFairness<String> fairness = new TerrainCompletionFairness<>();
        ConcurrentLinkedQueue<String> interactive = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> normal = new ConcurrentLinkedQueue<>();
        interactive.add("i1");
        interactive.add("i2");
        interactive.add("i3");
        interactive.add("i4");
        interactive.add("i5");
        normal.add("n1");

        assertEquals("i1", fairness.poll(interactive, normal));
        assertEquals("i2", fairness.poll(interactive, normal));
        assertEquals("i3", fairness.poll(interactive, normal));
        assertEquals("i4", fairness.poll(interactive, normal));
        assertEquals("n1", fairness.poll(interactive, normal));
    }

    @Test
    void resetClearsPriorInteractiveStreak() {
        TerrainCompletionFairness<String> fairness = new TerrainCompletionFairness<>();
        ConcurrentLinkedQueue<String> interactive = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> normal = new ConcurrentLinkedQueue<>();
        interactive.add("i1");
        interactive.add("i2");
        interactive.add("i3");
        interactive.add("i4");
        interactive.add("i5");

        assertEquals("i1", fairness.poll(interactive, normal));
        assertEquals("i2", fairness.poll(interactive, normal));
        assertEquals("i3", fairness.poll(interactive, normal));
        assertEquals("i4", fairness.poll(interactive, normal));
        fairness.reset();
        normal.add("n1");
        assertEquals("i5", fairness.poll(interactive, normal));
    }
}
