package dev.comfyfluffy.caustica.streamline;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class StreamlineFrameTraceTest {
    @Test
    void publishesPrimitiveEventsInSequence() {
        StreamlineFrameTrace trace = new StreamlineFrameTrace();
        trace.setEnabled(true);
        trace.record(StreamlineFrameTraceEvent.FRAME_BEGIN, 99L, 1L, 2L, 7,
                StreamlineFrameTraceEvent.PRODUCER_CLIENT);
        StreamlineFrameTrace.MutableEvent event = new StreamlineFrameTrace.MutableEvent();
        assertTrue(trace.copy(0L, event));
        assertEquals(StreamlineFrameTraceEvent.FRAME_BEGIN, event.eventType);
        assertEquals(99L, event.frameToken);
        assertEquals(7, event.frameIndex);
        assertEquals(1L, event.arg0);
        assertEquals(2L, event.arg1);
    }

    @Test
    void disabledTraceDoesNotAdvanceStorage() {
        StreamlineFrameTrace trace = new StreamlineFrameTrace();
        trace.record(StreamlineFrameTraceEvent.FRAME_BEGIN, 1L, 0L, 0L, 0,
                StreamlineFrameTraceEvent.PRODUCER_CLIENT);
        assertEquals(0L, trace.nextSequence());
    }
}
