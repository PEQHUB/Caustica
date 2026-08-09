package dev.comfyfluffy.caustica.rt.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RtDlssFgTest {
    @Test
    void unknownDriverMaximumAllowsOnlyOneGeneratedFrameAfterProbe() {
        assertEquals(3, RtDlssFg.effectiveMultiFrameCount(3, 0, false));
        assertEquals(1, RtDlssFg.effectiveMultiFrameCount(3, 0, true));
        assertEquals(2, RtDlssFg.effectiveMultiFrameCount(3, 2, true));
    }
}
