package dev.comfyfluffy.caustica.rt.terrain;

import dev.comfyfluffy.caustica.CausticaConfig;
import dev.comfyfluffy.caustica.CausticaMod;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared daemon thread pool for CPU-heavy RT work that must stay off the render thread — terrain
 * tessellation and terrain buffer/BLAS preparation, with LOD build / atlas stitching as future
 * candidates. Workers may create distinct Vulkan/VMA objects and enqueue command recording onto
 * {@code RtGpuExecutor}; they never access or submit the graphics queue. Each task delivers exactly one
 * terminal result through the terrain lifecycle barrier.
 *
 * <p>Sized at {@code -Dcaustica.rt.workerThreads} (default {@code clamp(cores/2, 1, 4)}) to leave
 * cores for Minecraft's own chunk meshers. Core threads time out when idle; all are daemon so they
 * never block JVM exit.
 */
public final class RtWorkerPool {
    private ThreadPoolExecutor exec;
    private boolean shuttingDown;

    private final AtomicLong nextSequence = new AtomicLong();

    /**
     * Package-private so lifecycle behavior can be tested without mutating
     * the process-wide singleton.
     */
    RtWorkerPool() {}

    private static int resolveThreads() {
        return CausticaConfig.Rt.WORKER_THREADS.value();
    }

    /**
     * The caller must hold this object's monitor.
     */
    private ThreadPoolExecutor executorLocked() {
        if (!Thread.holdsLock(this)) {
            throw new AssertionError("RtWorkerPool lock is not held");
        }

        if (shuttingDown) {
            throw new IllegalStateException("RT worker pool is shutting down");
        }

        if (exec == null) {
            int threads = resolveThreads();

            ThreadFactory factory = new ThreadFactory() {
                private final AtomicInteger n = new AtomicInteger();

                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread =
                        new Thread(runnable, "rt-worker-" + n.incrementAndGet());

                    thread.setDaemon(true);
                    thread.setPriority(Thread.NORM_PRIORITY - 1);
                    return thread;
                }
            };

            ThreadPoolExecutor created = new ThreadPoolExecutor(
                threads,
                threads,
                30L,
                TimeUnit.SECONDS,
                new PriorityBlockingQueue<>(),
                factory
            );

            created.allowCoreThreadTimeOut(true);
            exec = created;

            CausticaMod.LOGGER.info(
                "RT worker pool started with {} thread(s)",
                threads
            );
        }

        return exec;
    }

    /** Submit worker-owned RT preparation; completion is delivered by the task. */
    public void submit(Runnable job) {
        submit(false, job);
    }

    /** Submit terrain work with bounded interactive priority. */
    public void submit(boolean interactive, Runnable job) {
        if (job == null) {
            throw new NullPointerException("job");
        }

        synchronized (this) {
            /*
             * Calling execute while holding the same lock used by shutdown closes
             * the executor-selection/shutdown race. Once this returns, the task
             * has either been accepted or an exception has been delivered to the
             * caller.
             */
            executorLocked().execute(
                new WorkerTask(
                    interactive,
                    nextSequence.incrementAndGet(),
                    job
                )
            );
        }
    }

    synchronized boolean isShuttingDown() {
        return shuttingDown;
    }

    /**
     * Stop all workers after joining every accepted job.
     * Safe to call when never started and safe for concurrent callers.
     */
    public void shutdown() {
        final ThreadPoolExecutor stopping;

        synchronized (this) {
            if (exec == null) {
                return;
            }

            stopping = exec;

            if (!shuttingDown) {
                /*
                 * No submitter can enter while this monitor is held.
                 */
                stopping.shutdown();
                shuttingDown = true;
            }
        }

        boolean interrupted = false;

        while (!stopping.isTerminated()) {
            try {
                stopping.awaitTermination(
                    Long.MAX_VALUE,
                    TimeUnit.NANOSECONDS
                );
            } catch (InterruptedException ignored) {
                /*
                 * Accepted terrain jobs own terminal callbacks, so teardown must
                 * still drain them. Restore interruption after the drain.
                 */
                interrupted = true;
            }
        }

        synchronized (this) {
            if (exec == stopping) {
                exec = null;
                shuttingDown = false;
                notifyAll();
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Interrupted while stopping RT worker pool"
            );
        }
    }

    private record WorkerTask(boolean interactive, long sequence, Runnable delegate)
            implements Runnable, Comparable<WorkerTask> {
        @Override
        public int compareTo(WorkerTask other) {
            return TerrainJobOrder.compare(interactive, sequence,
                    other.interactive, other.sequence);
        }

        @Override
        public void run() {
            delegate.run();
        }
    }
}
