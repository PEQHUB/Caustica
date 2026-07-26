package dev.comfyfluffy.caustica.streamline;

import com.mojang.blaze3d.systems.GpuSurface;
import dev.comfyfluffy.caustica.CausticaConfig;
import dev.comfyfluffy.caustica.CausticaMod;
import dev.comfyfluffy.caustica.rt.RtComposite;
import dev.comfyfluffy.caustica.rt.RtHdr;
import dev.comfyfluffy.caustica.rt.pipeline.RtDlssFg;
import net.minecraft.client.Minecraft;

import java.util.Collection;
import java.util.EnumSet;

/** Coordinates DLSS-G plugin ownership with Minecraft's existing surface reconfiguration transaction. */
public final class StreamlineSwapchainCoordinator {
    public static final StreamlineSwapchainCoordinator INSTANCE = new StreamlineSwapchainCoordinator();

    private boolean configuring;
    private boolean configured;
    private boolean pluginRequestedForSwapchain;
    private boolean pluginForSwapchain;
    private boolean physicalFifo;
    private boolean vsyncRequested;
    private boolean mailboxSupported;
    private boolean mailboxPresentationSelected;
    private GpuSurface.PresentMode requestedPresentMode;
    private GpuSurface.PresentMode presentMode;
    private Collection<GpuSurface.PresentMode> supportedPresentModes = EnumSet.noneOf(GpuSurface.PresentMode.class);
    private int width;
    private int height;
    private int format;
    private int imageCount;
    private long generation;
    private boolean reconfigureRequested;
    private boolean hdrRequestForSwapchain;
    private boolean hdrEffective;
    private int hdrColorSpace;
    private String hdrFallbackReason = "";
    private StreamlineRuntime.SwapchainTrace nativeSwapchain = StreamlineRuntime.SwapchainTrace.unknown();

    private StreamlineSwapchainCoordinator() {
    }

    /** Request Minecraft's normal, render-thread surface recreation rather than replacing its lifecycle. */
    public void requestReconfigure() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (reconfigureRequested || configuring) {
            return;
        }
        reconfigureRequested = true;
        minecraft.execute(minecraft::invalidateSurfaceConfiguration);
    }

    /** Reconcile config/TOML changes that did not originate in the options screen. */
    public void synchronizeRequestedState() {
        if (!configured || configuring || reconfigureRequested) {
            return;
        }
        boolean desiredPlugin = requestedPresentMode != null
                && presentationDecision(requestedPresentMode).frameGenerationAllowed();
        boolean desiredHdr = CausticaConfig.Rt.Hdr.enabled();
        if (desiredPlugin != pluginRequestedForSwapchain || desiredHdr != hdrRequestForSwapchain) {
            requestReconfigure();
        }
    }

    /** Called at configure HEAD, before Minecraft waits and destroys its old swapchain. */
    public void configureStarting() {
        configuring = true;
        configured = false;
        reconfigureRequested = false;
        nativeSwapchain = StreamlineRuntime.SwapchainTrace.unknown();
        RtHdr.clearStagedSelection();
        RtDlssFg.INSTANCE.suspendForSwapchainChange();
    }

    /** Resolve the explicit presentation policy without conflating MAILBOX with vendor VSync. */
    public GpuSurface.Configuration normalizeConfiguration(GpuSurface.Configuration configuration,
            Collection<GpuSurface.PresentMode> supportedPresentModes) {
        requestedPresentMode = configuration.presentMode();
        vsyncRequested = isVsyncConfiguration(configuration);
        mailboxSupported = supportedPresentModes.contains(GpuSurface.PresentMode.MAILBOX);
        this.supportedPresentModes = supportedPresentModes.isEmpty()
                ? EnumSet.noneOf(GpuSurface.PresentMode.class) : EnumSet.copyOf(supportedPresentModes);
        mailboxPresentationSelected = false;
        VulkanFgPresentationPolicy.Decision decision = presentationDecision(configuration.presentMode());
        presentMode = toGpuMode(decision.resolved());
        mailboxPresentationSelected = decision.mailboxPresentationSelected();
        if (presentMode == configuration.presentMode()) return configuration;
        CausticaMod.LOGGER.debug(
                "Frame Generation presentation policy {}: requested {} -> {} ({})",
                CausticaConfig.Rt.Fg.PRESENTATION_POLICY.get(), configuration.presentMode(), presentMode,
                decision.reason());
        return new GpuSurface.Configuration(configuration.width(), configuration.height(), presentMode);
    }

    private VulkanFgPresentationPolicy.Decision presentationDecision(GpuSurface.PresentMode requested) {
        VulkanFgPresentationPolicy policy = VulkanFgPresentationPolicy.valueOf(
                CausticaConfig.Rt.Fg.PRESENTATION_POLICY.get().toUpperCase(java.util.Locale.ROOT)
                        .replace('-', '_'));
        EnumSet<VulkanFgPresentationPolicy.PresentMode> supported =
                EnumSet.noneOf(VulkanFgPresentationPolicy.PresentMode.class);
        for (GpuSurface.PresentMode mode : supportedPresentModes) supported.add(toPolicyMode(mode));
        return policy.resolve(toPolicyMode(requested), supported, CausticaConfig.Rt.Fg.requested());
    }

    private static VulkanFgPresentationPolicy.PresentMode toPolicyMode(GpuSurface.PresentMode mode) {
        return switch (mode) {
            case FIFO -> VulkanFgPresentationPolicy.PresentMode.FIFO;
            case FIFO_RELAXED -> VulkanFgPresentationPolicy.PresentMode.FIFO_RELAXED;
            case MAILBOX -> VulkanFgPresentationPolicy.PresentMode.MAILBOX;
            case IMMEDIATE -> VulkanFgPresentationPolicy.PresentMode.IMMEDIATE;
            default -> VulkanFgPresentationPolicy.PresentMode.OTHER;
        };
    }

    private static GpuSurface.PresentMode toGpuMode(VulkanFgPresentationPolicy.PresentMode mode) {
        return switch (mode) {
            case FIFO -> GpuSurface.PresentMode.FIFO;
            case FIFO_RELAXED -> GpuSurface.PresentMode.FIFO_RELAXED;
            case MAILBOX -> GpuSurface.PresentMode.MAILBOX;
            case IMMEDIATE -> GpuSurface.PresentMode.IMMEDIATE;
            case OTHER -> GpuSurface.PresentMode.FIFO;
        };
    }

    /** Called after the old swapchain is destroyed and immediately before replacement creation. */
    public boolean prepareReplacement(GpuSurface.Configuration configuration) {
        physicalFifo = isVsyncConfiguration(configuration);
        presentMode = configuration.presentMode();
        boolean desiredPlugin = CausticaConfig.Rt.Fg.requested() && !physicalFifo;
        pluginRequestedForSwapchain = desiredPlugin;
        // On the initial Off swapchain, capture adapter support while DLSS-G is still loaded from slInit;
        // the feature is deliberately unloaded immediately below to remove disabled-present overhead.
        RtDlssFg.INSTANCE.probeAvailabilityOnce();
        boolean prepared = StreamlineRuntime.prepareSwapchain(desiredPlugin);
        pluginForSwapchain = desiredPlugin && prepared;
        if (desiredPlugin && !prepared) {
            CausticaMod.LOGGER.warn("DLSS-G swapchain proxy could not be enabled; replacement remains native");
        }
        return pluginForSwapchain;
    }

    /** Called only after Minecraft has successfully created and enumerated the replacement swapchain. */
    public void configured(GpuSurface.Configuration configuration, int actualWidth, int actualHeight,
            int nativeFormat, int nativeColorSpace, int buffers) {
        configuring = false;
        configured = true;
        // The successfully created physical swapchain is authoritative. Streamline receives the visible
        // render target separately through its extent-only backbuffer tag.
        width = actualWidth;
        height = actualHeight;
        format = nativeFormat;
        imageCount = buffers;
        generation++;
        hdrRequestForSwapchain = RtHdr.stagedRequested();
        RtHdr.commitSwapchainSelection(nativeFormat, nativeColorSpace, generation);
        hdrEffective = RtHdr.effective();
        hdrColorSpace = nativeColorSpace;
        hdrFallbackReason = RtHdr.fallbackReason();
        RtComposite.INSTANCE.requestTemporalReset();
        nativeSwapchain = StreamlineRuntime.nativeSwapchainTrace();
        RtDlssFg.INSTANCE.onSwapchainConfigured(width, height, format, imageCount, vsyncRequested,
                physicalFifo, pluginForSwapchain, generation);
        CausticaMod.LOGGER.debug(
                "Streamline swapchain generation {}: {}x{}, format={}, applicationImages={}, plugin={}, requestedPresentMode={}, normalizedPresentMode={}, vsyncRequested={}, mailboxPresentationSelected={}, nativePresentMode={} (value={}), requestedNativeMinImages={}, proxyVisibleImages={}, nativeCreateResult={}, nativeProxyDispatch={}, nativeSwapchain={}",
                generation, width, height, format, imageCount, pluginForSwapchain, requestedPresentMode, presentMode, vsyncRequested,
                mailboxPresentationSelected, nativeSwapchain.presentMode(), nativeSwapchain.presentModeValue(),
                nativeSwapchain.minImageCount(), nativeSwapchain.imageCount(), nativeSwapchain.createResult(),
                nativeSwapchain.proxyDispatch(), nativeSwapchain.handleHex());
        if (mailboxPresentationSelected && nativeSwapchain.presentModeKnown()
                && !"MAILBOX".equals(nativeSwapchain.presentMode())) {
            CausticaMod.LOGGER.error(
                    "Frame Generation MAILBOX presentation proof failed: requested MAILBOX but native proxy observed {} (value={})",
                    nativeSwapchain.presentMode(), nativeSwapchain.presentModeValue());
        }
    }

    public void configureFailed() {
        if (!configuring && !configured) {
            return;
        }
        configuring = false;
        configured = false;
        hdrEffective = false;
        hdrColorSpace = RtHdr.SDR_COLOR_SPACE;
        hdrFallbackReason = "swapchain configure failed";
        RtHdr.failSwapchainConfiguration("swapchain configure failed");
        RtDlssFg.INSTANCE.onSwapchainConfigurationFailed();
    }

    public void closing() {
        configured = false;
        configuring = false;
        RtDlssFg.INSTANCE.suspendForSwapchainChange();
    }

    public boolean configured() {
        return configured;
    }

    public boolean configuring() {
        return configuring;
    }

    public boolean hdrRequested() {
        return hdrRequestForSwapchain;
    }

    public boolean hdrEffective() {
        return hdrEffective;
    }

    public int hdrColorSpace() {
        return hdrColorSpace;
    }

    public String hdrFallbackReason() {
        return hdrFallbackReason;
    }

    public long generation() {
        return generation;
    }

    public boolean vsyncRequested() {
        return vsyncRequested;
    }

    public boolean mailboxPresentationSelected() {
        return mailboxPresentationSelected;
    }

    public boolean mailboxSupported() {
        return mailboxSupported;
    }

    public String presentMode() {
        return presentMode == null ? "unknown" : presentMode.name();
    }

    public String requestedPresentMode() {
        return requestedPresentMode == null ? "unknown" : requestedPresentMode.name();
    }

    public String normalizedPresentMode() {
        return presentMode();
    }

    public String nativePresentMode() {
        return nativeSwapchain.presentMode();
    }

    public int nativePresentModeValue() {
        return nativeSwapchain.presentModeValue();
    }

    public boolean nativePresentModeKnown() {
        return nativeSwapchain.presentModeKnown();
    }

    /** Requested minimum in the intercepted native create info; not a physical-image enumeration. */
    public int requestedNativeMinImageCount() {
        return nativeSwapchain.minImageCount();
    }

    /** Image count returned through the Streamline proxy to the application. */
    public int proxyVisibleImageCount() {
        return nativeSwapchain.imageCount();
    }

    public int applicationImageCount() {
        return imageCount;
    }

    public int nativeCreateResult() {
        return nativeSwapchain.createResult();
    }

    public boolean nativeProxyDispatch() {
        return nativeSwapchain.proxyDispatch();
    }

    public String nativeSwapchainHandle() {
        return nativeSwapchain.handleHex();
    }

    private static boolean isVsyncRequested() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft != null && minecraft.options != null && minecraft.options.enableVsync().get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isVsyncConfiguration(GpuSurface.Configuration configuration) {
        return configuration.presentMode() == GpuSurface.PresentMode.FIFO
                || configuration.presentMode() == GpuSurface.PresentMode.FIFO_RELAXED;
    }
}
