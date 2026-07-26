package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DlssgInputPoolRandomizedTest {
    @Test
    void ownershipNeverChangesWithoutAnExplicitNonblockingTransition() {
        Random random = new Random(0xD155L); // deterministic model seed
        DlssgInputPool pool = new DlssgInputPool();
        pool.reset(5, 3L);
        final long seed = 0xD155L;
        for (int step = 0; step < 100_000; step++) {
            try {
                int slot = random.nextInt(pool.capacity());
                long token = Math.max(1L, random.nextLong());
                switch (random.nextInt(9)) {
                    case 0 -> pool.tryAcquire(token);
                    case 1 -> {
                        if (pool.state(slot) == DlssgInputPoolState.CAPTURING)
                            assertTrue(pool.isCapturing(slot, pool.ownerToken(slot), pool.generation()));
                    }
                    case 2 -> pool.markReady(slot, pool.ownerToken(slot));
                    case 3 -> pool.markPending(slot, pool.ownerToken(slot), 11L, 10L);
                    case 4 -> pool.retireCompleted((device, semaphore) -> 9L, 1L);
                    case 5 -> pool.retireCompleted((device, semaphore) -> 10L, 1L);
                    case 6 -> pool.releaseSubmittedSynchronously(slot, pool.ownerToken(slot));
                    case 7 -> pool.quarantine(slot, pool.ownerToken(slot));
                    case 8 -> {
                        pool.releaseAllAfterDeviceIdle();
                        pool.reset(5, 100L + step);
                        assertEquals(100L + step, pool.generation());
                    }
                    default -> throw new AssertionError();
                }
            } catch (Throwable failure) {
                fail("seed=" + seed + ", step=" + step, failure);
            }
        }
        assertEquals(5, pool.freeCount());
        assertEquals(0, pool.pendingCount());
    }
}
