package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FrameLifecycleTest {
    @Test
    void normalLifecycleRequiresOrderedMarkers() {
        FrameLifecycle lifecycle = new FrameLifecycle();
        assertTrue(lifecycle.beginNormal(1, 44L, 10L));
        assertFalse(lifecycle.markSimulationEnd());
        assertTrue(lifecycle.markSleep());
        assertTrue(lifecycle.markSimulationStart());
        assertTrue(lifecycle.markSimulationEnd());
        assertTrue(lifecycle.markRenderSubmitStart());
        assertTrue(lifecycle.markApplicationSubmit());
        assertTrue(lifecycle.markRenderSubmitEnd());
        assertTrue(lifecycle.markPresentStart());
        assertTrue(lifecycle.markPresentEnd(0));
        assertEquals(FrameLifecycleState.COMPLETE, lifecycle.state());
    }

    @Test
    void outOfBandCannotSleepOrEmitSimulationMarkers() {
        FrameLifecycle lifecycle = new FrameLifecycle();
        assertTrue(lifecycle.beginOutOfBand(2, 45L, 10L));
        assertFalse(lifecycle.markSleep());
        assertFalse(lifecycle.markSimulationStart());
        assertTrue(lifecycle.markPresentStart());
        assertTrue(lifecycle.markPresentEnd(0));
        assertTrue(lifecycle.realFrameOnly());
    }
}
