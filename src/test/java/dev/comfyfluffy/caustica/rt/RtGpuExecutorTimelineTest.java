package dev.comfyfluffy.caustica.rt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class RtGpuExecutorTimelineTest {
    @Test
    void timelineValuesFollowExecutionOrder() {
        RtGpuExecutor.Build streamingA = new RtGpuExecutor.Build();
        RtGpuExecutor.Build streamingB = new RtGpuExecutor.Build();
        RtGpuExecutor.Build interactiveC = new RtGpuExecutor.Build();
        interactiveC.assignTimelineValue(1L);
        streamingA.assignTimelineValue(2L);
        streamingB.assignTimelineValue(3L);

        assertEquals(1L, interactiveC.value());
        assertEquals(2L, streamingA.value());
        assertEquals(3L, streamingB.value());

        RtGpuExecutor.Build cancelled = new RtGpuExecutor.Build();
        assertFalse(cancelled.submitted());
    }
}
