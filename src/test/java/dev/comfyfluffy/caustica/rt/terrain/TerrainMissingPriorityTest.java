package dev.comfyfluffy.caustica.rt.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerrainMissingPriorityTest {
    @Test
    void dirtyPromotionAndResetAreExplicit() {
        TerrainMissingPriority priority = new TerrainMissingPriority();
        priority.mark(11L, false);
        assertFalse(priority.contains(11L));

        priority.mark(11L, true);
        assertTrue(priority.contains(11L));
        priority.remove(11L);
        assertFalse(priority.contains(11L));

        priority.mark(12L, true);
        priority.clear();
        assertFalse(priority.contains(12L));
    }
}
