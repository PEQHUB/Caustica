package dev.comfyfluffy.caustica.rt.pipeline;

/** Generation-safe immutable input ownership with nonblocking retirement. */
public final class DlssgInputPool {
    public static final int NO_SLOT = -1;
    private static final int MIN_CAPACITY = 5;
    private static final int MAX_CAPACITY = 8;
    private DlssgInputPoolState[] state = new DlssgInputPoolState[0];
    private long[] generation = new long[0];
    private long[] ownerToken = new long[0];
    private long[] semaphore = new long[0];
    private long[] completionValue = new long[0];
    private long poolGeneration;

    public void reset(int requestedCapacity, long newGeneration) {
        int capacity = Math.clamp(requestedCapacity, MIN_CAPACITY, MAX_CAPACITY);
        state = new DlssgInputPoolState[capacity];
        generation = new long[capacity];
        ownerToken = new long[capacity];
        semaphore = new long[capacity];
        completionValue = new long[capacity];
        poolGeneration = newGeneration;
        for (int i = 0; i < capacity; i++) {
            state[i] = DlssgInputPoolState.FREE;
            generation[i] = newGeneration;
        }
    }

    public int tryAcquire(long token) {
        for (int i = 0; i < state.length; i++) {
            if (state[i] == DlssgInputPoolState.FREE && generation[i] == poolGeneration) {
                state[i] = DlssgInputPoolState.CAPTURING;
                ownerToken[i] = token;
                return i;
            }
        }
        return NO_SLOT;
    }

    public boolean markReady(int index, long token) {
        return owned(index, token, DlssgInputPoolState.CAPTURING)
                && setState(index, DlssgInputPoolState.READY_FOR_PRESENT);
    }

    public boolean markPending(int index, long token, long completionSemaphore, long value) {
        if (!owned(index, token, DlssgInputPoolState.READY_FOR_PRESENT)
                || completionSemaphore == 0L || value == 0L) return false;
        semaphore[index] = completionSemaphore;
        completionValue[index] = value;
        state[index] = DlssgInputPoolState.RETIREMENT_PENDING;
        return true;
    }

    public boolean releaseWithoutVendorOwnership(int index, long token) {
        if (!owned(index, token, DlssgInputPoolState.CAPTURING)
                && !owned(index, token, DlssgInputPoolState.READY_FOR_PRESENT)) return false;
        release(index);
        return true;
    }

    public boolean quarantine(int index, long token) {
        if (!owned(index, token, DlssgInputPoolState.READY_FOR_PRESENT)
                && !owned(index, token, DlssgInputPoolState.RETIREMENT_PENDING)) return false;
        state[index] = DlssgInputPoolState.QUARANTINED;
        return true;
    }

    public int retireCompleted(TimelineCounterReader reader, long device) {
        int retired = 0;
        for (int i = 0; i < state.length; i++) {
            if (state[i] != DlssgInputPoolState.RETIREMENT_PENDING) continue;
            long observed = reader.query(device, semaphore[i]);
            if (observed < 0L) {
                state[i] = DlssgInputPoolState.QUARANTINED;
            } else if (Long.compareUnsigned(observed, completionValue[i]) >= 0) {
                release(i);
                retired++;
            }
        }
        return retired;
    }

    private boolean owned(int index, long token, DlssgInputPoolState expected) {
        return index >= 0 && index < state.length && state[index] == expected
                && ownerToken[index] == token && generation[index] == poolGeneration;
    }

    private boolean setState(int index, DlssgInputPoolState next) {
        state[index] = next;
        return true;
    }

    private void release(int index) {
        state[index] = DlssgInputPoolState.FREE;
        ownerToken[index] = 0L;
        semaphore[index] = 0L;
        completionValue[index] = 0L;
    }

    public int capacity() { return state.length; }
    public long generation() { return poolGeneration; }
    public DlssgInputPoolState state(int index) { return state[index]; }
    public long ownerToken(int index) { return ownerToken[index]; }
    public long completionValue(int index) { return completionValue[index]; }
    public int freeCount() { return count(DlssgInputPoolState.FREE); }
    public int pendingCount() { return count(DlssgInputPoolState.RETIREMENT_PENDING); }
    public int quarantinedCount() { return count(DlssgInputPoolState.QUARANTINED); }
    private int count(DlssgInputPoolState expected) {
        int count = 0;
        for (DlssgInputPoolState value : state) if (value == expected) count++;
        return count;
    }

    public interface TimelineCounterReader {
        /** Return a counter, or -1 when ownership is unknown. Never waits. */
        long query(long device, long semaphore);
    }
}
