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
        assertEquals(4, pool.freeCount());
    }

    @Test
    void leaseIdentityIncludesTokenAndGeneration() {
        DlssgInputPool pool = new DlssgInputPool();
        pool.reset(5, 9L);
        int slot = pool.tryAcquire(3L);
        assertTrue(pool.isCapturing(slot, 3L, 9L));
        assertFalse(pool.isCapturing(slot, 4L, 9L));
        assertFalse(pool.isCapturing(slot, 3L, 8L));
        assertTrue(pool.markReady(slot, 3L));
        assertTrue(pool.releaseSubmittedSynchronously(slot, 3L));
        assertEquals(5, pool.freeCount());
    }

    @Test
    void deviceIdleReleaseClearsQuarantine() {
        DlssgInputPool pool = new DlssgInputPool();
        pool.reset(5, 1L);
        int slot = pool.tryAcquire(1L);
        assertTrue(pool.quarantine(slot, 1L));
        pool.releaseAllAfterDeviceIdle();
        assertEquals(5, pool.freeCount());
    }
}
