package dev.comfyfluffy.caustica.rt.accel;

import dev.comfyfluffy.caustica.CausticaConfig;
import dev.comfyfluffy.caustica.CausticaMod;
import dev.comfyfluffy.caustica.rt.RtContext;


import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Size-bucketed recycling free-list for per-frame buffers (entity mesh, BLAS scratch, etc.).
 *
 * <p>Buffers are bucketed to the next power of two (≤2× waste, O(1) reuse). The caller writes/reads
 * only the requested-size prefix; the unused tail is harmless and addresses stay correct.
 * The pool is caller-agnostic — only the (usage, hostVisible, bucket) triple matters for reuse.
 *
 * <p>Correctness: callers only {@link #release} at the {@code frameCounter + KEEP_FRAMES} horizon,
 * i.e. once a buffer's last GPU use is off all queues, so a reacquired buffer is always safe to
 * overwrite. Single-threaded (render thread).
 */
public final class RtBufferPool {
    private static final int STATS_INTERVAL = 600; // frames between stat dumps
    private static final long MIN_BUCKET = 256;
    // Cap free-list depth per key so a transient entity spike can't strand unbounded VRAM.
    private static final int MAX_FREE_PER_KEY = 64;

    private record PoolKey(int usage, boolean hostVisible, long bucket) {}

    private final Map<PoolKey, ArrayDeque<RtBuffer>> free = new HashMap<>();
    private final Runnable onCreate;
    private long created;
    private long reused;
    private long statsCounter;
    // Cumulative CPU time spent inside acquire()/release() themselves (map/deque bookkeeping),
    // not GPU work — baseline for the A/B comparison against the upcoming pool replacement.
    private long acquireNanos;
    private long releaseNanos;
    private long releaseCalls;

    public RtBufferPool() {
        this(null);
    }

    /** {@code onCreate}, if given, fires once per fresh (non-reused) VMA allocation — for {@code RtFrameStats}. */
    public RtBufferPool(Runnable onCreate) {
        this.onCreate = onCreate;
    }

    /** Acquire a buffer with capacity ≥ {@code minSize}, reusing a free one if available. */
    public RtBuffer acquire(RtContext ctx, long minSize, int usage, boolean hostVisible) {
        return acquire(ctx, minSize, usage, hostVisible, "pooled buffer " + minSize + "B");
    }

    /** Acquire a buffer with capacity ≥ {@code minSize}, reusing a free one if available. */
    public RtBuffer acquire(RtContext ctx, long minSize, int usage, boolean hostVisible, String label) {
        boolean timed = CausticaConfig.Rt.BufferPool.STATS.value();
        long start = timed ? System.nanoTime() : 0L;
        long bucket = ceilPow2(Math.max(minSize, MIN_BUCKET));
        ArrayDeque<RtBuffer> list = free.get(new PoolKey(usage, hostVisible, bucket));
        RtBuffer buf;
        if (list != null && !list.isEmpty()) {
            reused++;
            buf = list.pop();
        } else {
            created++;
            if (onCreate != null) {
                onCreate.run();
            }
            buf = ctx.createBuffer(bucket, usage, hostVisible, label + " bucket " + bucket + "B");
        }
        if (timed) {
            acquireNanos += System.nanoTime() - start;
        }
        return buf;
    }

    /** Return a buffer to the pool for reuse, or destroy it if the free-list for its bucket is full. */
    public void release(RtBuffer b) {
        boolean timed = CausticaConfig.Rt.BufferPool.STATS.value();
        long start = timed ? System.nanoTime() : 0L;
        PoolKey key = new PoolKey(b.usage, b.hostVisible, b.size);
        ArrayDeque<RtBuffer> list = free.computeIfAbsent(key, k -> new ArrayDeque<>());
        if (list.size() >= MAX_FREE_PER_KEY) {
            b.destroy();
        } else {
            list.push(b);
        }
        if (timed) {
            releaseNanos += System.nanoTime() - start;
            releaseCalls++;
        }
    }

    /** Destroy every pooled buffer (teardown; caller must have drained outstanding deferred releases first). */
    public void destroyAll() {
        for (ArrayDeque<RtBuffer> list : free.values()) {
            for (RtBuffer b : list) {
                b.destroy();
            }
        }
        free.clear();
    }

    /** Periodic stat dump (opt-in via {@code -Dcaustica.rt.poolStats}); call once per frame. */
    public void maybeLogStats() {
        if (!CausticaConfig.Rt.BufferPool.STATS.value() || (statsCounter++ % STATS_INTERVAL) != 0) {
            return;
        }
        long freeBytes = 0;
        int freeCount = 0;
        for (ArrayDeque<RtBuffer> list : free.values()) {
            for (RtBuffer b : list) {
                freeBytes += b.size;
                freeCount++;
            }
        }
        long acquireCalls = created + reused;
        double acquireAvgUs = acquireCalls == 0 ? 0.0 : acquireNanos / 1000.0 / acquireCalls;
        double releaseAvgUs = releaseCalls == 0 ? 0.0 : releaseNanos / 1000.0 / releaseCalls;
        CausticaMod.LOGGER.info("RT buffer pool: created={} reused={} | free {} buffers, {} KiB | "
                        + "acquire avg={}us total={}ms ({} calls), release avg={}us total={}ms ({} calls)",
                created, reused, freeCount, freeBytes / 1024,
                String.format("%.2f", acquireAvgUs), acquireNanos / 1_000_000, acquireCalls,
                String.format("%.2f", releaseAvgUs), releaseNanos / 1_000_000, releaseCalls);
    }

    private static long ceilPow2(long v) {
        long p = 1;
        while (p < v) {
            p <<= 1;
        }
        return p;
    }
}
