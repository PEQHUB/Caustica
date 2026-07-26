package dev.comfyfluffy.caustica.streamline;

import java.util.Collection;
import java.util.Objects;

/** Explicit Caustica presentation policy; MAILBOX is not reported as vendor VSync. */
public enum VulkanFgPresentationPolicy {
    PRESERVE_REQUEST,
    FG_MAILBOX,
    UNCAPPED_IMMEDIATE;

    public enum PresentMode {
        FIFO, FIFO_RELAXED, MAILBOX, IMMEDIATE, OTHER
    }

    public record Decision(PresentMode requested, PresentMode resolved, boolean frameGenerationAllowed,
            boolean mailboxPresentationSelected, String reason) {
    }

    public Decision resolve(PresentMode requested, Collection<PresentMode> supported, boolean fgRequested) {
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(supported, "supported");
        boolean fifo = requested == PresentMode.FIFO || requested == PresentMode.FIFO_RELAXED;
        if (!fgRequested) return new Decision(requested, requested, false, false, "Frame Generation is disabled");
        return switch (this) {
            case PRESERVE_REQUEST -> fifo
                    ? new Decision(requested, requested, false, false,
                            "FIFO preserved; Frame Generation is unavailable on this Vulkan configuration")
                    : new Decision(requested, requested, true, false, "Requested non-FIFO mode preserved");
            case FG_MAILBOX -> fifo && supported.contains(PresentMode.MAILBOX)
                    ? new Decision(requested, PresentMode.MAILBOX, true, true,
                            "MAILBOX selected for tear-free low-latency presentation; this is not Streamline VSync")
                    : fifo ? new Decision(requested, requested, false, false,
                            "Frame Generation requires a non-FIFO Vulkan present mode")
                    : new Decision(requested, requested, true, false, "Requested non-FIFO mode preserved");
            case UNCAPPED_IMMEDIATE -> supported.contains(PresentMode.IMMEDIATE)
                    ? new Decision(requested, PresentMode.IMMEDIATE, true, false,
                            "IMMEDIATE selected; tearing is possible")
                    : supported.contains(PresentMode.MAILBOX)
                    ? new Decision(requested, PresentMode.MAILBOX, true, true,
                            "IMMEDIATE unavailable; MAILBOX selected as the non-FIFO fallback")
                    : new Decision(requested, requested, false, false,
                            "No supported non-FIFO Vulkan present mode is available");
        };
    }
}
