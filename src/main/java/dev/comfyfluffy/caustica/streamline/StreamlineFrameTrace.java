package dev.comfyfluffy.caustica.streamline;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicLong;

/** Fixed-size multi-producer trace. Producers publish only primitive fields. */
public final class StreamlineFrameTrace {
    public static final int CAPACITY = 1 << 15;
    private static final int MASK = CAPACITY - 1;
    private static final long NOT_PUBLISHED = Long.MIN_VALUE;
    private static final VarHandle PUBLISHED = MethodHandles.arrayElementVarHandle(long[].class);

    private final AtomicLong cursor = new AtomicLong();
    private final long[] publishedSequence = new long[CAPACITY];
    private final long[] timestampNs = new long[CAPACITY];
    private final long[] frameToken = new long[CAPACITY];
    private final long[] arg0 = new long[CAPACITY];
    private final long[] arg1 = new long[CAPACITY];
    private final int[] frameIndex = new int[CAPACITY];
    private final int[] eventType = new int[CAPACITY];
    private final int[] threadId = new int[CAPACITY];
    private volatile boolean enabled;

    public StreamlineFrameTrace() {
        java.util.Arrays.fill(publishedSequence, NOT_PUBLISHED);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public long nextSequence() {
        return cursor.get();
    }

    public void record(int event, long token, long value0, long value1, int frame, int producer) {
        if (!enabled) {
            return;
        }
        long sequence = cursor.getAndIncrement();
        int slot = (int) sequence & MASK;
        PUBLISHED.setRelease(publishedSequence, slot, NOT_PUBLISHED);
        timestampNs[slot] = System.nanoTime();
        frameToken[slot] = token;
        arg0[slot] = value0;
        arg1[slot] = value1;
        frameIndex[slot] = frame;
        eventType[slot] = event;
        threadId[slot] = producer;
        PUBLISHED.setRelease(publishedSequence, slot, sequence);
    }

    public boolean copy(long sequence, MutableEvent out) {
        int slot = (int) sequence & MASK;
        if ((long) PUBLISHED.getAcquire(publishedSequence, slot) != sequence) {
            return false;
        }
        out.sequence = sequence;
        out.timestampNs = timestampNs[slot];
        out.frameToken = frameToken[slot];
        out.arg0 = arg0[slot];
        out.arg1 = arg1[slot];
        out.frameIndex = frameIndex[slot];
        out.eventType = eventType[slot];
        out.threadId = threadId[slot];
        return (long) PUBLISHED.getAcquire(publishedSequence, slot) == sequence;
    }

    public static final class MutableEvent {
        public long sequence;
        public long timestampNs;
        public long frameToken;
        public long arg0;
        public long arg1;
        public int frameIndex;
        public int eventType;
        public int threadId;
    }
}
