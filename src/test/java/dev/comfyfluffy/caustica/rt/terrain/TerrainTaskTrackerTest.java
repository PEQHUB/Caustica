package dev.comfyfluffy.caustica.rt.terrain;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerrainTaskTrackerTest {
    @Test
    void cancellationRevokesPublicationWithoutReleasingAdmission() {
        TerrainTaskTracker tracker = new TerrainTaskTracker();
        TerrainTaskTracker.Ticket old = tracker.accept(7L, 1L);

        assertTrue(tracker.isCurrent(old));
        tracker.cancelCurrent(7L);
        assertTrue(old.cancelled());
        assertFalse(tracker.isCurrent(old));
        assertEquals(1, tracker.outstanding());

        TerrainTaskTracker.Ticket replacement = tracker.accept(7L, 2L);
        assertEquals(2, tracker.outstanding());
        assertTrue(tracker.retire(old));
        assertEquals(1, tracker.outstanding());
        assertTrue(tracker.isCurrent(replacement));
        assertTrue(tracker.retire(replacement));
        assertEquals(0, tracker.outstanding());
        assertFalse(tracker.retire(replacement));
    }

    @Test
    void pruningCancelsOnlyTicketsOutsideTheDesiredSet() {
        TerrainTaskTracker tracker = new TerrainTaskTracker();
        TerrainTaskTracker.Ticket kept = tracker.accept(11L, 1L);
        TerrainTaskTracker.Ticket removed = tracker.accept(12L, 2L);

        var cancelled = tracker.cancelNotIn(new LongOpenHashSet(new long[] {11L}));
        assertEquals(1, cancelled.size());
        assertEquals(12L, cancelled.getLong(0));
        assertTrue(tracker.isCurrent(kept));
        assertTrue(removed.cancelled());
        assertEquals(2, tracker.outstanding());

        tracker.retire(removed);
        tracker.retire(kept);
        assertEquals(0, tracker.outstanding());
    }
}
