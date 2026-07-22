package dev.comfyfluffy.caustica.rt;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class RtGpuExecutorTimelineTest {
    @Test
    void priorityReorderingAssignsAndSignalsExecutionMaximum() {
        RtGpuExecutor.Build streamingA = new RtGpuExecutor.Build();
        RtGpuExecutor.Build streamingB = new RtGpuExecutor.Build();
        RtGpuExecutor.Build interactiveC = new RtGpuExecutor.Build();
        record Candidate(boolean interactive, long sequence, RtGpuExecutor.Build build) {}
        List<Candidate> candidates = new ArrayList<>(List.of(
                new Candidate(false, 1L, streamingA),
                new Candidate(false, 2L, streamingB),
                new Candidate(true, 3L, interactiveC)));
        candidates.sort((left, right) -> TerrainJobOrder.compare(
                left.interactive(), left.sequence(), right.interactive(), right.sequence()));
        long signalValue = RtGpuExecutor.assignTimelineValues(
                candidates.stream().map(Candidate::build).toList(), new AtomicLong());

        assertEquals(1L, interactiveC.value());
        assertEquals(2L, streamingA.value());
        assertEquals(3L, streamingB.value());
        assertEquals(3L, signalValue);

        RtGpuExecutor.Build cancelled = new RtGpuExecutor.Build();
        assertFalse(cancelled.submitted());
    }
}
