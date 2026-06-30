package dev.upscaler.rt.pipeline;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;

import dev.upscaler.UpscalerConfig;
import dev.upscaler.UpscalerMod;
import dev.upscaler.mixin.GpuDeviceAccessor;
import dev.upscaler.ngx.NgxLibrary;
import dev.upscaler.ngx.NgxRuntime;

import org.lwjgl.vulkan.VK10;

import java.lang.foreign.MemorySegment;

/**
 * DLSS Frame Generation (DLSSG) backend. Shares the NGX instance with DLSS-RR via {@link NgxRuntime};
 * owns only the DLSSG feature handle. This turn provides availability detection and the feature
 * create/destroy lifecycle — the per-frame {@code evaluate} + the multi-present loop that consumes it land
 * with the present-path refactor. Gated by {@code upscaler.rt.fg} (default off) and hardware/driver support.
 */
public final class RtDlssFg {
    public static final RtDlssFg INSTANCE = new RtDlssFg();

    public static boolean enabled() {
        return UpscalerConfig.Rt.Fg.ENABLED.value();
    }

    private NgxLibrary lib;
    private MemorySegment feature = MemorySegment.NULL;
    private boolean initialized;
    private boolean failed;
    private boolean probed;
    private boolean available;
    private int multiFrameCountMax;

    private int featureWidth = -1;
    private int featureHeight = -1;
    private int featureRenderWidth = -1;
    private int featureRenderHeight = -1;
    private int featureBackbufferFormat = Integer.MIN_VALUE;

    private RtDlssFg() {
    }

    public boolean isAvailable() {
        return available;
    }

    /** Driver-reported maximum multi-frame-generation count (1 = 2x only); 0 until probed. */
    public int multiFrameCountMax() {
        return multiFrameCountMax;
    }

    /** Requested generated-frame count clamped to the driver maximum (>=1 once available). */
    public int effectiveMultiFrameCount() {
        int requested = UpscalerConfig.Rt.Fg.MULTI_FRAME_COUNT.value();
        return multiFrameCountMax > 0 ? Math.clamp(requested, 1, multiFrameCountMax) : requested;
    }

    public boolean isReady() {
        return initialized && !failed && !isNull(feature);
    }

    /**
     * Probe DLSSG availability once (after NGX is up) and log the result + MFG cap. Safe to call every tick
     * when FG is enabled; no-op after the first successful probe. Needs no command buffer (capability query).
     */
    public void probeAvailabilityOnce() {
        if (probed || failed) {
            return;
        }
        if (!(((GpuDeviceAccessor) RenderSystem.getDevice()).upscaler$getBackend() instanceof VulkanDevice device)) {
            return;
        }
        NgxLibrary l = NgxRuntime.INSTANCE.acquire(device);
        if (l == null) {
            return; // NGX not up yet; try again next tick
        }
        probed = true;
        lib = l;
        if (!l.hasDlssg()) {
            UpscalerMod.LOGGER.warn("DLSS-FG: loaded ngxshim.dll has no DLSSG ABI — rebuild the shim "
                    + "(cmake --build native/ngx_shim/build --config Release)");
            return;
        }
        available = l.dlssgAvailable();
        multiFrameCountMax = l.dlssgMultiFrameCountMax();
        UpscalerMod.LOGGER.info("DLSS Frame Generation available: {} (multi-frame max {})", available, multiFrameCountMax);
    }

    /**
     * Ensure a DLSSG feature exists for the given backbuffer/render size + native backbuffer format, creating
     * it into the supplied recording command buffer. Returns false (and disables itself) on failure.
     */
    public boolean ensureFeature(long cmd, int width, int height, int renderWidth, int renderHeight, int backbufferFormat) {
        if (!enabled() || failed) {
            return false;
        }
        if (!(((GpuDeviceAccessor) RenderSystem.getDevice()).upscaler$getBackend() instanceof VulkanDevice device)) {
            return false;
        }
        try {
            if (lib == null) {
                lib = NgxRuntime.INSTANCE.acquire(device);
            }
            if (lib == null || !lib.hasDlssg()) {
                throw new IllegalStateException("NGX/DLSSG unavailable; cannot create FG feature");
            }
            if (!probed) {
                probeAvailabilityOnce();
            }
            if (!available) {
                throw new IllegalStateException("DLSS Frame Generation is not available on this system");
            }
            if (featureWidth != width || featureHeight != height
                    || featureRenderWidth != renderWidth || featureRenderHeight != renderHeight
                    || featureBackbufferFormat != backbufferFormat || isNull(feature)) {
                releaseFeature(device);
                feature = lib.createDlssg(cmd, width, height, renderWidth, renderHeight, backbufferFormat);
                if (isNull(feature)) {
                    throw new IllegalStateException("ngxshim_create_dlssg failed: last=0x"
                            + Integer.toHexString(lib.lastResult()));
                }
                featureWidth = width;
                featureHeight = height;
                featureRenderWidth = renderWidth;
                featureRenderHeight = renderHeight;
                featureBackbufferFormat = backbufferFormat;
                initialized = true;
                UpscalerMod.LOGGER.info("DLSS-FG feature created: {}x{} (render {}x{}, backbuffer format {})",
                        width, height, renderWidth, renderHeight, backbufferFormat);
            }
            return true;
        } catch (Throwable t) {
            failed = true;
            UpscalerMod.LOGGER.error("DLSS-FG setup failed; frame generation disabled", t);
            return false;
        }
    }

    /** Release the FG feature. NGX itself is shut down by {@link NgxRuntime} at device teardown. */
    public void destroy() {
        if (((GpuDeviceAccessor) RenderSystem.getDevice()).upscaler$getBackend() instanceof VulkanDevice device) {
            releaseFeature(device);
        }
        initialized = false;
        lib = null;
    }

    private void releaseFeature(VulkanDevice device) {
        if (lib != null && !isNull(feature)) {
            VK10.vkDeviceWaitIdle(device.vkDevice());
            lib.release(feature);
        }
        feature = MemorySegment.NULL;
        featureWidth = -1;
        featureHeight = -1;
        featureRenderWidth = -1;
        featureRenderHeight = -1;
        featureBackbufferFormat = Integer.MIN_VALUE;
    }

    private static boolean isNull(MemorySegment segment) {
        return segment == null || segment.equals(MemorySegment.NULL);
    }
}
