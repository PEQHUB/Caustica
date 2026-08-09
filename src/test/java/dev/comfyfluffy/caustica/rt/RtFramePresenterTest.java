package dev.comfyfluffy.caustica.rt;

import it.unimi.dsi.fastutil.longs.LongArrayList;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RtFramePresenterTest {
    @Test
    void generatedPresentsWaitForOneSemaphorePerSwapchainImage() {
        LongArrayList images = new LongArrayList(new long[] {1L, 2L});

        assertFalse(RtFramePresenter.hasPresentSemaphorePool(images, null));
        assertFalse(RtFramePresenter.hasPresentSemaphorePool(images, new long[0]));
        assertFalse(RtFramePresenter.hasPresentSemaphorePool(images, new long[1]));
        assertTrue(RtFramePresenter.hasPresentSemaphorePool(images, new long[2]));
    }

    @Test
    void generatedCountLeavesTheRealFrameImageAvailable() {
        assertEquals(0, RtFramePresenter.clampGeneratedCount(1, 1));
        assertEquals(1, RtFramePresenter.clampGeneratedCount(3, 2));
        assertEquals(2, RtFramePresenter.clampGeneratedCount(3, 3));
    }
}
