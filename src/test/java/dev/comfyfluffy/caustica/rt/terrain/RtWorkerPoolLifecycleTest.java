package dev.comfyfluffy.caustica.rt.terrain;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

final class RtWorkerPoolLifecycleTest {

    @Test
    void shutdownDrainsAcceptedWorkAndRejectsNewWorkWhileStopping()
            throws Exception {

        RtWorkerPool pool = new RtWorkerPool();

        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();
        AtomicReference<Throwable> shutdownFailure =
            new AtomicReference<>();

        pool.submit(false, () -> {
            taskStarted.countDown();
            awaitUninterruptibly(releaseTask);
            completed.incrementAndGet();
        });

        assertTrue(
            taskStarted.await(5L, TimeUnit.SECONDS),
            "worker task did not start"
        );

        Thread stopper = new Thread(() -> {
            try {
                pool.shutdown();
            } catch (Throwable throwable) {
                shutdownFailure.set(throwable);
            }
        }, "rt-worker-pool-shutdown-test");

        stopper.start();

        try {
            awaitShuttingDown(pool);

            assertThrows(
                IllegalStateException.class,
                () -> pool.submit(false, () -> {
                    throw new AssertionError(
                        "submission during shutdown was executed"
                    );
                })
            );
        } finally {
            releaseTask.countDown();
        }

        stopper.join(TimeUnit.SECONDS.toMillis(5L));

        assertFalse(
            stopper.isAlive(),
            "worker-pool shutdown did not complete"
        );

        assertNull(
            shutdownFailure.get(),
            "worker-pool shutdown failed"
        );

        assertEquals(
            1,
            completed.get(),
            "accepted work was not drained exactly once"
        );

        /*
         * Preserve the previous restartable behavior after a completed
         * shutdown.
         */
        CountDownLatch restarted = new CountDownLatch(1);
        pool.submit(false, restarted::countDown);

        assertTrue(
            restarted.await(5L, TimeUnit.SECONDS),
            "worker pool did not restart after completed shutdown"
        );

        pool.shutdown();
    }

    private static void awaitShuttingDown(RtWorkerPool pool)
            throws InterruptedException {

        long deadline =
            System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);

        while (!pool.isShuttingDown()) {
            if (System.nanoTime() >= deadline) {
                fail("worker pool did not enter shutdown state");
            }

            Thread.sleep(1L);
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;

        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
