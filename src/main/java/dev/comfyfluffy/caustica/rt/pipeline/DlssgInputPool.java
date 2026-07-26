package dev.comfyfluffy.caustica.rt.pipeline;

/** Immutable slot ownership for all Streamline-tagged DLSS-G inputs. */
public final class DlssgInputPool {
    public static final int NO_SLOT = -1;
    private static final int MIN_CAPACITY = 5;
    private static final int MAX_CAPACITY = 8;

    private DlssgInputPoolState[] states = new DlssgInputPoolState[0];
    private long[] generations = new long[0];
    private long[] owners = new long[0];
    private long[] semaphores = new long[0];
    private long[] completionValues = new long[0];
    private long poolGeneration;

    public void reset(int requestedCapacity, long newGeneration) {
        int capacity = Math.clamp(requestedCapacity, MIN_CAPACITY, MAX_CAPACITY);
        states = new DlssgInputPoolState[capacity];
        generations = new long[capacity];
        owners = new long[capacity];
        semaphores = new long[capacity];
        completionValues = new long[capacity];
        poolGeneration = newGeneration;
        for (int i = 0; i < capacity; i++) {
            states[i] = DlssgInputPoolState.FREE;
            generations[i] = newGeneration;
        }
    }

    public int tryAcquire(long token) {
        if (token == 0L) return NO_SLOT;
        for (int i = 0; i < states.length; i++) {
            if (states[i] == DlssgInputPoolState.FREE && generations[i] == poolGeneration) {
                states[i] = DlssgInputPoolState.CAPTURING;
                owners[i] = token;
                return i;
            }
        }
        return NO_SLOT;
    }

    public boolean isCapturing(int index, long token, long generation) {
        return owned(index, token, generation, DlssgInputPoolState.CAPTURING);
    }

    public boolean markReady(int index, long token) {
        return owned(index, token, poolGeneration, DlssgInputPoolState.CAPTURING)
                && setState(index, DlssgInputPoolState.READY_FOR_PRESENT);
    }

    public boolean markPending(int index, long token, long semaphore, long value) {
        if (!owned(index, token, poolGeneration, DlssgInputPoolState.READY_FOR_PRESENT)
                || semaphore == 0L || value == 0L) return false;
        semaphores[index] = semaphore;
        completionValues[index] = value;
        states[index] = DlssgInputPoolState.RETIREMENT_PENDING;
        return true;
    }

    public boolean releaseWithoutVendorOwnership(int index, long token) {
        if (!owned(index, token, poolGeneration, DlssgInputPoolState.CAPTURING)
                && !owned(index, token, poolGeneration, DlssgInputPoolState.READY_FOR_PRESENT)) return false;
        release(index);
        return true;
    }

    public boolean releaseSubmittedSynchronously(int index, long token) {
        return releaseWithoutVendorOwnership(index, token);
    }

    public boolean quarantine(int index, long token) {
        if (!owned(index, token, poolGeneration, DlssgInputPoolState.READY_FOR_PRESENT)
                && !owned(index, token, poolGeneration, DlssgInputPoolState.RETIREMENT_PENDING)
                && !owned(index, token, poolGeneration, DlssgInputPoolState.CAPTURING)) return false;
        states[index] = DlssgInputPoolState.QUARANTINED;
        return true;
    }

    /** Poll every pending slot without waiting. A negative result means ownership is unknown. */
    public int retireCompleted(TimelineCounterReader reader, long device) {
        int retired = 0;
        for (int i = 0; i < states.length; i++) {
            if (states[i] != DlssgInputPoolState.RETIREMENT_PENDING) continue;
            long observed = reader.query(device, semaphores[i]);
            if (observed < 0L) {
                states[i] = DlssgInputPoolState.QUARANTINED;
            } else if (Long.compareUnsigned(observed, completionValues[i]) >= 0) {
                release(i);
                retired++;
            }
        }
        return retired;
    }

    /** Lifecycle-only fallback after the device has been proven idle. */
    public void releaseAllAfterDeviceIdle() {
        for (int i = 0; i < states.length; i++) release(i);
    }

    private boolean owned(int index, long token, long generation, DlssgInputPoolState expected) {
        return index >= 0 && index < states.length && states[index] == expected
                && owners[index] == token && generations[index] == generation && generation == poolGeneration;
    }

    private boolean setState(int index, DlssgInputPoolState next) {
        states[index] = next;
        return true;
    }

    private void release(int index) {
        states[index] = DlssgInputPoolState.FREE;
        owners[index] = 0L;
        semaphores[index] = 0L;
        completionValues[index] = 0L;
    }

    public int capacity() { return states.length; }
    public long generation() { return poolGeneration; }
    public DlssgInputPoolState state(int index) { return states[index]; }
    public long ownerToken(int index) { return owners[index]; }
    public long completionValue(int index) { return completionValues[index]; }
    public int freeCount() { return count(DlssgInputPoolState.FREE); }
    public int capturingCount() { return count(DlssgInputPoolState.CAPTURING); }
    public int readyCount() { return count(DlssgInputPoolState.READY_FOR_PRESENT); }
    public int pendingCount() { return count(DlssgInputPoolState.RETIREMENT_PENDING); }
    public int quarantinedCount() { return count(DlssgInputPoolState.QUARANTINED); }

    private int count(DlssgInputPoolState expected) {
        int count = 0;
        for (DlssgInputPoolState value : states) if (value == expected) count++;
        return count;
    }

    public interface TimelineCounterReader {
        /** Return a counter, or -1 when ownership is unknown. Never waits. */
        long query(long device, long semaphore);
    }
}
