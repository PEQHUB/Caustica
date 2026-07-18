package dev.comfyfluffy.caustica.rt.pipeline;

import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StreamlinePresentRecoveryTest {
    @Test
    void swapchainInvalidationIsRecoverable() {
        assertTrue(RtDlssFg.isRecoverablePresentResult(KHRSwapchain.VK_SUBOPTIMAL_KHR));
        assertTrue(RtDlssFg.isRecoverablePresentResult(KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR));
        assertFalse(RtDlssFg.isRecoverablePresentResult(VK10.VK_ERROR_DEVICE_LOST));
        assertFalse(RtDlssFg.isRecoverablePresentResult(VK10.VK_SUCCESS));
    }
}
