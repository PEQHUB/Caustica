package dev.comfyfluffy.caustica.rt.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TerrainStreamBudgetTest {
    @Test
    void adaptiveBudgetScalesBetweenBaseAndMaximum() {
        assertEquals(1_500_000L, TerrainStreamBudget.adaptiveBudgetNanos(1.5, 6.0, 0, 0, 32));
        assertEquals(3_750_000L, TerrainStreamBudget.adaptiveBudgetNanos(1.5, 6.0, 0, 16, 32));
        assertEquals(6_000_000L, TerrainStreamBudget.adaptiveBudgetNanos(1.5, 6.0, 256, 0, 32));
    }

    @Test
    void configuredMaximumCannotShrinkBelowBaseAndFallbackIsFixed() {
        assertEquals(6_000_000L, TerrainStreamBudget.adaptiveBudgetNanos(6.0, 1.5, 256, 32, 32));
        assertEquals(8_000_000L, TerrainStreamBudget.fixedBudgetNanos(8.0));
        assertEquals(108_000_000L, TerrainStreamBudget.deadline(100_000_000L, 8_000_000L));
    }
}
