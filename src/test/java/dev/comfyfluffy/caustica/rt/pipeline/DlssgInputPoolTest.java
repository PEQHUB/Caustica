package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DlssgInputPoolTest {
    @Test
    void pendingSlotIsRetiredOnlyAfterCounterReachesValue() {
        DlssgInputPool pool = new DlssgInputPool();
        pool.reset(5, 7L);
        int slot = pool.tryAcquire(10L);
        assertTrue(pool.markReady(slot, 10L));
        assertTrue(pool.markPending(slot, 10L, 20L, 4L));
        assertEquals(0, pool.retireCompleted((device, semaphore) -> 3L, 1L));
        assertEquals(DlssgInputPoolState.RETIREMENT_PENDING, pool.state(slot));
        assertEquals(1, pool.retireCompleted((device, semaphore) -> 4L, 1L));
        assertEquals(DlssgInputPoolState.FREE, pool.state(slot));
    }

    @Test
    void unknownCompletionQuarantinesWithoutWaiting() {
        DlssgInputPool pool = new DlssgInputPool();
        pool.reset(5, 1L);
        int slot = pool.tryAcquire(3L);
        pool.markReady(slot, 3L);
        pool.markPending(slot, 3L, 2L, 1L);
        pool.retireCompleted((device, semaphore) -> -1L, 1L);
        assertEquals(DlssgInputPoolState.QUARANTINED, pool.state(slot));
        assertEquals(0, pool.freeCount() - 4);
    }
}
