package dev.comfyfluffy.caustica.streamline;

import static org.junit.jupiter.api.Assertions.*;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class VulkanFgPresentationPolicyTest {
    @Test
    void mailboxPolicyNormalizesOnlyFifo() {
        var policy = VulkanFgPresentationPolicy.FG_MAILBOX;
        var fifo = policy.resolve(VulkanFgPresentationPolicy.PresentMode.FIFO,
                EnumSet.of(VulkanFgPresentationPolicy.PresentMode.FIFO,
                        VulkanFgPresentationPolicy.PresentMode.MAILBOX), true);
        assertEquals(VulkanFgPresentationPolicy.PresentMode.MAILBOX, fifo.resolved());
        assertTrue(fifo.frameGenerationAllowed());
        assertTrue(fifo.reason().contains("not Streamline VSync"));

        var immediate = policy.resolve(VulkanFgPresentationPolicy.PresentMode.IMMEDIATE,
                EnumSet.of(VulkanFgPresentationPolicy.PresentMode.IMMEDIATE), true);
        assertEquals(VulkanFgPresentationPolicy.PresentMode.IMMEDIATE, immediate.resolved());
    }

    @Test
    void preserveRequestFailsClosedForFifo() {
        var result = VulkanFgPresentationPolicy.PRESERVE_REQUEST.resolve(
                VulkanFgPresentationPolicy.PresentMode.FIFO,
                EnumSet.of(VulkanFgPresentationPolicy.PresentMode.FIFO), true);
        assertFalse(result.frameGenerationAllowed());
        assertEquals(VulkanFgPresentationPolicy.PresentMode.FIFO, result.resolved());
    }
}
