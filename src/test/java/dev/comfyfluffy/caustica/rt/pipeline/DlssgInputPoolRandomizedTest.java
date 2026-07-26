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
        for (int step = 0; step < 10_000; step++) {
            int slot = random.nextInt(pool.capacity());
            long token = random.nextLong();
            if (pool.state(slot) == DlssgInputPoolState.FREE) {
                int acquired = pool.tryAcquire(token);
                if (acquired >= 0) {
                    assertEquals(DlssgInputPoolState.CAPTURING, pool.state(acquired));
                    assertEquals(token, pool.ownerToken(acquired));
                    pool.releaseWithoutVendorOwnership(acquired, token);
                }
            }
        }
        assertEquals(5, pool.freeCount());
        assertEquals(0, pool.pendingCount());
    }
}
