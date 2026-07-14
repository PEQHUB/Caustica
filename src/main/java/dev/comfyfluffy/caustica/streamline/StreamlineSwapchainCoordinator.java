package dev.comfyfluffy.caustica.streamline;

import com.mojang.blaze3d.systems.GpuSurface;
import dev.comfyfluffy.caustica.CausticaConfig;
import dev.comfyfluffy.caustica.CausticaMod;
import dev.comfyfluffy.caustica.rt.pipeline.RtDlssFg;
import net.minecraft.client.Minecraft;

/** Coordinates DLSS-G plugin ownership with Minecraft's existing surface reconfiguration transaction. */
public final class StreamlineSwapchainCoordinator {
    public static final StreamlineSwapchainCoordinator INSTANCE = new StreamlineSwapchainCoordinator();

    private boolean configuring;
    private boolean configured;
    private boolean pluginForSwapchain;
    private boolean vsync;
    private int width;
    private int height;
    private int format;
    private int imageCount;
    private long generation;
    private boolean reconfigureRequested;

    private StreamlineSwapchainCoordinator() {
    }

    /** Request Minecraft's normal, render-thread surface recreation rather than replacing its lifecycle. */
    public void requestReconfigure() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
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
        boolean desiredPlugin = CausticaConfig.Rt.Fg.requested() && !isVsyncRequested();
        if (desiredPlugin != pluginForSwapchain) {
            requestReconfigure();
        }
    }

    /** Called at configure HEAD, before Minecraft waits and destroys its old swapchain. */
    public void configureStarting() {
        configuring = true;
        configured = false;
        reconfigureRequested = false;
        RtDlssFg.INSTANCE.suspendForSwapchainChange();
    }

    /** Called after the old swapchain is destroyed and immediately before replacement creation. */
    public boolean prepareReplacement(GpuSurface.Configuration configuration) {
        vsync = isVsyncConfiguration(configuration);
        boolean desiredPlugin = CausticaConfig.Rt.Fg.requested() && !vsync;
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
            int nativeFormat, int buffers) {
        configuring = false;
        configured = true;
        // The successfully created physical swapchain is authoritative. Streamline receives the visible
        // render target separately through its extent-only backbuffer tag.
        width = actualWidth;
        height = actualHeight;
        format = nativeFormat;
        imageCount = buffers;
        generation++;
        RtDlssFg.INSTANCE.onSwapchainConfigured(width, height, format, imageCount, vsync, pluginForSwapchain,
                generation);
        CausticaMod.LOGGER.info("Streamline swapchain generation {}: {}x{}, format={}, images={}, plugin={}, vsync={}",
                generation, width, height, format, imageCount, pluginForSwapchain, vsync);
    }

    public void configureFailed() {
        configuring = false;
        configured = false;
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

    public long generation() {
        return generation;
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
