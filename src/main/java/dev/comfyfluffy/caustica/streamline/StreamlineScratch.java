package dev.comfyfluffy.caustica.streamline;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/** Long-lived, role-owned native scratch for Streamline calls. */
public final class StreamlineScratch implements AutoCloseable {
    public enum Role { FRAME, PRESENT, LIFECYCLE }

    private final Arena arena = Arena.ofShared();
    private final long ownerThreadId = Thread.currentThread().threadId();
    private final MemorySegment frameTokenOut = allocate(8);
    private final MemorySegment apiErrorOut = allocate(8);
    private final MemorySegment abiInfo = allocate(36);
    private final MemorySegment constants = allocate(StreamlineAbi.CONSTANTS_SIZE);
    private final MemorySegment fgResources = allocate(StreamlineAbi.RESOURCE_DESC_SIZE * 8);
    private final MemorySegment dlssdResources = allocate(StreamlineAbi.RESOURCE_DESC_SIZE * 8);
    private final MemorySegment dlssgOptions = allocate(StreamlineAbi.DLSSG_OPTIONS_SIZE);
    private final MemorySegment dlssgState = allocate(StreamlineAbi.DLSSG_STATE_SIZE);
    private final MemorySegment dlssgEstimateOptions = allocate(StreamlineAbi.DLSSG_OPTIONS_SIZE);
    private final MemorySegment reflexOptions = allocate(StreamlineAbi.REFLEX_OPTIONS_SIZE);
    private final MemorySegment reflexState = allocate(StreamlineAbi.REFLEX_STATE_SIZE);
    private final MemorySegment featureRequirements = allocate(32);
    private final MemorySegment featureVersion = allocate(32);
    private final MemorySegment traceState = allocate(StreamlineAbi.TRACE_STATE_SIZE);

    public StreamlineScratch() {
    }

    private MemorySegment allocate(long bytes) {
        return arena.allocate(bytes, 8);
    }

    public void assertOwnerThread() {
        if (Boolean.getBoolean("caustica.streamline.debugOwnership")
                && Thread.currentThread().threadId() != ownerThreadId) {
            throw new IllegalStateException("Streamline scratch used from the wrong owner thread");
        }
    }

    public void clear(MemorySegment segment) {
        assertOwnerThread();
        segment.fill((byte) 0);
    }

    public MemorySegment frameTokenOut() { return frameTokenOut; }
    public MemorySegment apiErrorOut() { return apiErrorOut; }
    public MemorySegment abiInfo() { return abiInfo; }
    public MemorySegment constants() { return constants; }
    public MemorySegment fgResources() { return fgResources; }
    public MemorySegment dlssdResources() { return dlssdResources; }
    public MemorySegment dlssgOptions() { return dlssgOptions; }
    public MemorySegment dlssgState() { return dlssgState; }
    public MemorySegment dlssgEstimateOptions() { return dlssgEstimateOptions; }
    public MemorySegment reflexOptions() { return reflexOptions; }
    public MemorySegment reflexState() { return reflexState; }
    public MemorySegment featureRequirements() { return featureRequirements; }
    public MemorySegment featureVersion() { return featureVersion; }
    public MemorySegment traceState() { return traceState; }

    @Override
    public void close() {
        arena.close();
    }
}
