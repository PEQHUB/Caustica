package dev.comfyfluffy.caustica.rt.terrain;

import dev.comfyfluffy.caustica.CausticaConfig;
import dev.comfyfluffy.caustica.CausticaMod;
import dev.comfyfluffy.caustica.rt.TerrainJobOrder;

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
    public static final RtWorkerPool INSTANCE = new RtWorkerPool();

    private ThreadPoolExecutor exec;
    private final AtomicLong nextSequence = new AtomicLong();

    private RtWorkerPool() {}

    private static int resolveThreads() {
        return CausticaConfig.Rt.WORKER_THREADS.value();
    }

    private synchronized ThreadPoolExecutor executor() {
        if (exec == null) {
            int threads = resolveThreads();
            ThreadFactory factory = new ThreadFactory() {
                private final AtomicInteger n = new AtomicInteger();
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "rt-worker-" + n.incrementAndGet());
                    t.setDaemon(true);
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    return t;
                }
            };
            ThreadPoolExecutor e = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS,
                    new PriorityBlockingQueue<>(), factory);
            e.allowCoreThreadTimeOut(true);
            exec = e;
            CausticaMod.LOGGER.info("RT worker pool started with {} thread(s)", threads);
        }
        return exec;
    }

    /** Submit worker-owned RT preparation; completion is delivered by the task itself. */
    public void submit(Runnable job) {
        submit(false, job);
    }

    /** Submit terrain work with bounded interactive priority. */
    public void submit(boolean interactive, Runnable job) {
        executor().execute(new WorkerTask(interactive, nextSequence.incrementAndGet(), job));
    }

    /** Stop all workers and drop queued jobs. Safe to call when never started. */
    public synchronized void shutdown() {
        if (exec != null) {
            exec.shutdownNow();
            exec = null;
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
