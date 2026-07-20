package dev.comfyfluffy.caustica.mixin;

import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuSurface;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.comfyfluffy.caustica.CausticaConfig;
import dev.comfyfluffy.caustica.CausticaMod;
import dev.comfyfluffy.caustica.rt.RtComposite;
import dev.comfyfluffy.caustica.rt.RtFrameStats;
import dev.comfyfluffy.caustica.rt.RtHdr;
import dev.comfyfluffy.caustica.rt.pipeline.RtDlssFg;
import dev.comfyfluffy.caustica.streamline.StreamlineRuntime;
import dev.comfyfluffy.caustica.streamline.StreamlineSwapchainCoordinator;
import it.unimi.dsi.fastutil.longs.LongList;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.LongBuffer;
import java.nio.IntBuffer;
import java.util.Set;

/**
 * HDR Phase 0 capability logging + PQ swapchain selection.
 *
 * <p>The {@link VulkanGpuSurface} constructor holds both the live {@code VkSurfaceKHR} and the physical
 * device, so we enumerate the surface's formats/color spaces there (once) for diagnostics.
 *
 * <p>When {@code caustica.rt.hdr.pqSwapchain} is on and the surface advertises HDR10_ST2084 (ST.2084/PQ,
 * paired with whatever pixel format the surface offers for it — commonly a 10-bit UNORM, but this is
 * discovered by scanning the surface's advertised formats rather than assumed), we steer Minecraft's
 * swapchain to it: vanilla {@code pickSwapchainSurfaceFormat} only accepts SDR (color space 0, format 37/44)
 * and {@code configure} hardcodes {@code imageColorSpace(0)}. We override the picked format and the
 * color-space arg. Falls back to the vanilla SDR path when the flag is off or PQ is unavailable; default off.
 */
@Mixin(VulkanGpuSurface.class)
public abstract class VulkanGpuSurfaceMixin {
	private static final int VK_COLOR_SPACE_HDR10_ST2084_EXT = 1000104008;

	@Shadow
	@Final
	private VulkanDevice device;

	@Shadow
	@Final
	private long surface;

	@Shadow
	private long swapchain;

	@Shadow
	@Mutable
	@Final
	private int swapchainImageFormat;

	@Shadow
	@Final
	private LongList swapchainImages;

	@Shadow
	private int currentImageIndex;

	@Shadow
	private int swapchainWidth;

	@Shadow
	private int swapchainHeight;

	@Shadow
	@Final
	private long[] acquireSemaphores;

	@Shadow
	@Final
	private Set<GpuSurface.PresentMode> supportedPresentModes;

	@Shadow
	private int currentAcquireSemaphore;

	@Shadow
	private long[] presentSemaphores;

	@Redirect(method = "<init>(Lcom/mojang/blaze3d/vulkan/VulkanDevice;J)V",
			at = @At(value = "INVOKE",
				target = "Lorg/lwjgl/glfw/GLFWVulkan;glfwCreateWindowSurface(Lorg/lwjgl/vulkan/VkInstance;JLorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"))
	private int caustica$createSurfaceThroughStreamline(org.lwjgl.vulkan.VkInstance instance, long window,
			VkAllocationCallbacks allocator, LongBuffer surfaceOut) {
		return StreamlineRuntime.vkCreateWin32Surface(instance, window, allocator, surfaceOut);
	}

	@Redirect(method = "close()V",
			at = @At(value = "INVOKE",
				target = "Lorg/lwjgl/vulkan/KHRSurface;vkDestroySurfaceKHR(Lorg/lwjgl/vulkan/VkInstance;JLorg/lwjgl/vulkan/VkAllocationCallbacks;)V"))
	private void caustica$destroySurfaceThroughStreamline(org.lwjgl.vulkan.VkInstance instance, long surface,
			VkAllocationCallbacks allocator) {
		StreamlineRuntime.vkDestroySurface(instance, surface, allocator);
	}

	@Inject(method = "close()V", at = @At("HEAD"))
	private void caustica$streamlineSurfaceClosing(CallbackInfo ci) {
		StreamlineSwapchainCoordinator.INSTANCE.closing();
		RtHdr.clearSwapchainSelection();
	}

	@Redirect(method = "destroySwapchain()V",
			at = @At(value = "INVOKE",
				target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkDestroySwapchainKHR(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkAllocationCallbacks;)V"))
	private void caustica$destroySwapchainThroughStreamline(VkDevice device, long swapchain,
			VkAllocationCallbacks allocator) {
		StreamlineRuntime.vkDestroySwapchain(device, swapchain, allocator);
	}

	@Redirect(method = "configure(Lcom/mojang/blaze3d/systems/GpuSurface$Configuration;)V",
			at = @At(value = "INVOKE",
				target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkGetSwapchainImagesKHR(Lorg/lwjgl/vulkan/VkDevice;JLjava/nio/IntBuffer;Ljava/nio/LongBuffer;)I"))
	private int caustica$getSwapchainImagesThroughStreamline(VkDevice device, long swapchain, IntBuffer count,
			LongBuffer images) {
		int result = StreamlineRuntime.vkGetSwapchainImages(device, swapchain, count, images);
		if (result != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
			StreamlineSwapchainCoordinator.INSTANCE.configureFailed();
		}
		return result;
	}

	@Redirect(method = "acquireNextTexture()V",
			at = @At(value = "INVOKE",
				target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkAcquireNextImageKHR(Lorg/lwjgl/vulkan/VkDevice;JJJJLjava/nio/IntBuffer;)I"))
	private int caustica$acquireNextImageThroughStreamline(VkDevice device, long swapchain, long timeout,
			long semaphore, long fence, IntBuffer imageIndex) {
		int result = StreamlineRuntime.vkAcquireNextImage(device, swapchain, timeout, semaphore, fence, imageIndex);
		if (result == org.lwjgl.vulkan.VK10.VK_SUCCESS || result == org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR) {
			RtDlssFg.INSTANCE.onImageAcquired(imageIndex.get(0));
		}
		return result;
	}

	@Unique
	private int caustica$colorSpace = 0;

	@Unique
	private int caustica$sdrFormat;

	@Unique
	private int caustica$sdrColorSpace = RtHdr.SDR_COLOR_SPACE;

	@Inject(method = "<init>(Lcom/mojang/blaze3d/vulkan/VulkanDevice;J)V", at = @At("TAIL"))
	private void caustica$logHdrCapabilities(VulkanDevice device, long windowHandle, CallbackInfo ci) {
		try {
			RtHdr.logSurfaceCapabilities(this.device.vkDevice().getPhysicalDevice(), this.surface, this.swapchainImageFormat);
		} catch (Throwable t) {
			// Diagnostics only — never let HDR logging break surface creation.
		}
	}

	/**
	 * Pick a PQ (HDR10_ST2084) surface format when requested + available, before vanilla's SDR-only selection
	 * runs. Accepts the implemented 10-bit UNORM formats paired with that color space. Sets
	 * {@link #caustica$colorSpace} so {@code configure} can pass the matching color space.
	 */
	@Inject(method = "pickSwapchainSurfaceFormat", at = @At("HEAD"), cancellable = true)
	private void caustica$pickPqFormat(VkSurfaceFormatKHR.Buffer formats, CallbackInfoReturnable<VkSurfaceFormatKHR> cir) {
		this.caustica$colorSpace = RtHdr.resetColorSpaceForSurfaceScan(this.caustica$colorSpace);
		RtHdr.clearSwapchainSelection();
		if (!CausticaConfig.Rt.Hdr.enabled()) {
			return;
		}
		boolean unsupportedPqAdvertised = false;
		for (int i = 0; i < formats.capacity(); i++) {
			VkSurfaceFormatKHR f = formats.get(i);
			if (f.colorSpace() == VK_COLOR_SPACE_HDR10_ST2084_EXT) {
				if (!RtHdr.isSupportedPqFormat(f.format())) {
					unsupportedPqAdvertised = true;
					continue;
				}
				this.caustica$colorSpace = VK_COLOR_SPACE_HDR10_ST2084_EXT;
				RtHdr.stageSwapchainSelection(true, f.format(), f.colorSpace(), 0, RtHdr.SDR_COLOR_SPACE, "");
				CausticaMod.LOGGER.info("HDR: selecting PQ swapchain (format={}, colorSpace=HDR10_ST2084)", f.format());
				cir.setReturnValue(f);
				return;
			}
		}
		CausticaMod.LOGGER.warn(unsupportedPqAdvertised
				? "HDR: HDR10_ST2084 was advertised only with unsupported non-10-bit formats; using SDR"
				: "HDR: PQ swapchain requested but HDR10_ST2084 was not advertised by the surface; "
						+ "using SDR (enable OS/display HDR; on Linux use a native Wayland session with HDR enabled in the compositor)");
	}

	/** Record the format vanilla actually selected on every SDR/fallback return path. */
	@Inject(method = "pickSwapchainSurfaceFormat", at = @At("RETURN"))
	private void caustica$recordSelectedSurfaceFormat(VkSurfaceFormatKHR.Buffer formats,
			CallbackInfoReturnable<VkSurfaceFormatKHR> cir) {
		VkSurfaceFormatKHR selected = cir.getReturnValue();
		if (selected == null) {
			RtHdr.clearSwapchainSelection();
			return;
		}
		this.caustica$colorSpace = selected.colorSpace();
		RtHdr.stageSwapchainSelection(CausticaConfig.Rt.Hdr.enabled(),
				selected.format(), selected.colorSpace(), selected.format(), selected.colorSpace(),
				CausticaConfig.Rt.Hdr.enabled() && !RtHdr.effectiveHdrForSelection(true,
						selected.format(), selected.colorSpace()) ? "HDR10_ST2084 unavailable; using SDR" : "");
	}

	/** Re-select the pair on every configure; Minecraft's field was initialized only in the constructor. */
	@ModifyArg(method = "configure",
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/vulkan/VkSwapchainCreateInfoKHR;imageFormat(I)Lorg/lwjgl/vulkan/VkSwapchainCreateInfoKHR;"),
			index = 0)
	private int caustica$selectConfigureFormat(int original) {
		int sdrFormat = original;
		int sdrColorSpace = RtHdr.SDR_COLOR_SPACE;
		int pqFormat = 0;
		boolean unsupportedPq = false;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer count = stack.callocInt(1);
			int result = org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(
					this.device.vkDevice().getPhysicalDevice(), this.surface, count, null);
			if (result >= 0 && count.get(0) > 0) {
				VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.calloc(count.get(0), stack);
				org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(
						this.device.vkDevice().getPhysicalDevice(), this.surface, count, formats);
				for (int i = 0; i < formats.capacity(); i++) {
					VkSurfaceFormatKHR f = formats.get(i);
					if (f.colorSpace() == RtHdr.SDR_COLOR_SPACE
							&& (f.format() == 37 || f.format() == 44)) {
						if (sdrFormat == original || sdrFormat != 37 && sdrFormat != 44) {
							sdrFormat = f.format();
						}
					}
				}
				for (int i = 0; i < formats.capacity(); i++) {
					VkSurfaceFormatKHR f = formats.get(i);
					if (f.colorSpace() != RtHdr.HDR10_ST2084_COLOR_SPACE) {
						continue;
					}
					if (RtHdr.isSupportedPqFormat(f.format())) {
						pqFormat = f.format();
						break;
					}
					unsupportedPq = true;
				}
			}
		} catch (Throwable t) {
			CausticaMod.LOGGER.warn("HDR: configure-time surface format enumeration failed; retaining format {}: {}",
					original, t.toString());
		}

		boolean request = CausticaConfig.Rt.Hdr.enabled();
		if (request && pqFormat != 0) {
			this.caustica$sdrFormat = sdrFormat;
			this.caustica$sdrColorSpace = sdrColorSpace;
			this.swapchainImageFormat = pqFormat;
			this.caustica$colorSpace = RtHdr.HDR10_ST2084_COLOR_SPACE;
			RtHdr.stageSwapchainSelection(true, pqFormat, RtHdr.HDR10_ST2084_COLOR_SPACE,
					sdrFormat, sdrColorSpace, "");
			return pqFormat;
		}

		String reason = request
				? (unsupportedPq ? "HDR10_ST2084 advertised only with unsupported formats; using SDR"
						: "HDR10_ST2084 unavailable; using SDR")
				: "";
		this.caustica$sdrFormat = sdrFormat;
		this.caustica$sdrColorSpace = sdrColorSpace;
		this.swapchainImageFormat = sdrFormat;
		this.caustica$colorSpace = sdrColorSpace;
		RtHdr.stageSwapchainSelection(request, sdrFormat, sdrColorSpace, sdrFormat, sdrColorSpace, reason);
		return sdrFormat;
	}

	/** Replace the hardcoded {@code imageColorSpace(0)} with the pair selected for this configure. */
	@ModifyArg(method = "configure",
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/vulkan/VkSwapchainCreateInfoKHR;imageColorSpace(I)Lorg/lwjgl/vulkan/VkSwapchainCreateInfoKHR;"),
			index = 0)
	private int caustica$overrideColorSpace(int original) {
		return this.caustica$colorSpace;
	}

	/** Streamline owns the swapchain proxy; Minecraft retains the configure transaction and object lifetime. */
	@Redirect(method = "configure",
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkCreateSwapchainKHR(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkSwapchainCreateInfoKHR;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"))
	private int caustica$createSwapchainThroughStreamline(VkDevice device, VkSwapchainCreateInfoKHR pCreateInfo,
			VkAllocationCallbacks pAllocator, LongBuffer pSwapchain) {
		int result = StreamlineRuntime.vkCreateSwapchain(device, pCreateInfo, pAllocator, pSwapchain);
		if (RtHdr.shouldRetrySdr(result, RtHdr.stagedEffective())) {
			CausticaMod.LOGGER.warn("HDR: PQ swapchain creation failed with {}; retrying once with SDR", result);
			pCreateInfo.imageFormat(RtHdr.stagedSdrFormat()).imageColorSpace(RtHdr.stagedSdrColorSpace());
			this.swapchainImageFormat = RtHdr.stagedSdrFormat();
			this.caustica$colorSpace = RtHdr.stagedSdrColorSpace();
			RtHdr.stageSdrFallback("PQ swapchain creation failed; retried with SDR");
			result = StreamlineRuntime.vkCreateSwapchain(device, pCreateInfo, pAllocator, pSwapchain);
		}
		if (result != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
			StreamlineSwapchainCoordinator.INSTANCE.configureFailed();
		}
		return result;
	}

	@ModifyVariable(method = "configure(Lcom/mojang/blaze3d/systems/GpuSurface$Configuration;)V",
			at = @At("HEAD"), argsOnly = true)
	private GpuSurface.Configuration caustica$normalizeStreamlinePresentMode(GpuSurface.Configuration config) {
		StreamlineSwapchainCoordinator.INSTANCE.configureStarting();
		return StreamlineSwapchainCoordinator.INSTANCE.normalizeConfiguration(config, this.supportedPresentModes);
	}

	@Inject(method = "configure", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/vulkan/VulkanGpuSurface;destroySwapchain()V",
			shift = At.Shift.AFTER))
	private void caustica$prepareStreamlineSwapchain(GpuSurface.Configuration config, CallbackInfo ci) {
		StreamlineSwapchainCoordinator.INSTANCE.prepareReplacement(config);
	}

	/** Publish the successfully created swapchain generation to the Streamline controller. */
	@Inject(method = "configure", at = @At("TAIL"))
	private void caustica$streamlineSwapchainConfigured(GpuSurface.Configuration config, CallbackInfo ci) {
		StreamlineSwapchainCoordinator.INSTANCE.configured(config, this.swapchainWidth, this.swapchainHeight,
				this.swapchainImageFormat,
				this.caustica$colorSpace,
				this.swapchainImages.size());
	}

	/** Minecraft throws after any failed create/enumeration; clear all staged/effective HDR truth transactionally. */
	@WrapMethod(method = "configure")
	private void caustica$configureWithHdrFailureCleanup(GpuSurface.Configuration config, Operation<Void> original) {
		try {
			original.call(config);
		} catch (Throwable t) {
			RtHdr.failSwapchainConfiguration("swapchain configure failed");
			StreamlineSwapchainCoordinator.INSTANCE.configureFailed();
			throw caustica$rethrow(t);
		}
	}

	@Unique
	private static <T extends Throwable> RuntimeException caustica$rethrow(Throwable throwable) throws T {
		throw (T) throwable;
	}

	/** Attach Streamline PCL markers to the same token used by the one real proxy present. */
	@Redirect(method = "present",
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkQueuePresentKHR(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkPresentInfoKHR;)I"))
	private int caustica$presentThroughStreamline(VkQueue queue, VkPresentInfoKHR presentInfo) {
		RtDlssFg.INSTANCE.beforePresent();
		int result;
		if (RtFrameStats.enabled()) {
			RtFrameStats.FRAME.count("fgPresentActive", RtDlssFg.INSTANCE.generationActiveForPresent() ? 1L : 0L);
			RtFrameStats.FRAME.count("fgQueueParallel", RtDlssFg.INSTANCE.parallelQueueActive() ? 1L : 0L);
			try (RtFrameStats.Scope ignored = RtFrameStats.FRAME.stage("frame.present")) {
				result = StreamlineRuntime.vkQueuePresent(queue, presentInfo);
			}
		} else {
			result = StreamlineRuntime.vkQueuePresent(queue, presentInfo);
		}
		RtDlssFg.INSTANCE.afterPresent(result);
		return result;
	}

	/** Present Caustica's HDR10 image, then tag its final depth, motion, hudless color, and UI inputs. */
	@Inject(method = "blitFromTexture", at = @At("HEAD"), cancellable = true)
	private void caustica$presentHdr(CommandEncoderBackend commandEncoder, GpuTextureView textureView, CallbackInfo ci) {
		if (this.currentImageIndex < 0) {
			return;
		}
		RtComposite rt = RtComposite.INSTANCE;
		long swapchainImage = this.swapchainImages.getLong(this.currentImageIndex);
		long acquireSem = this.acquireSemaphores[this.currentAcquireSemaphore];
		long presentSem = this.presentSemaphores[this.currentImageIndex];
		if (rt.isHdrPresentActive()) {
			VulkanCommandEncoder enc = (VulkanCommandEncoder) commandEncoder;
			rt.presentHdr(enc, swapchainImage, this.swapchainWidth, this.swapchainHeight,
					this.swapchainImageFormat, acquireSem, presentSem);
			rt.submitStreamlineFrame(enc, this.swapchainWidth, this.swapchainHeight,
					this.swapchainImageFormat, true);
			ci.cancel();
			return;
		}
		// Non-RT frame (menu, title panorama, loading screen) on a PQ swapchain: vanilla's raw SDR blit would
		// misdisplay (SDR bytes reinterpreted as PQ codes). Convert sRGB -> PQ at paper white instead. Falls
		// through to vanilla SDR if conversion resources aren't ready or the source view is not a Vulkan view.
		if (rt.isPqSdrPresentActive()) {
			long sdrView = caustica$vkImageView(textureView);
			if (sdrView != 0L && rt.presentSdrToPq((VulkanCommandEncoder) commandEncoder, swapchainImage,
					this.swapchainWidth, this.swapchainHeight, sdrView, acquireSem, presentSem)) {
				ci.cancel();
			}
		}
	}

	@Unique
	private static long caustica$vkImageView(GpuTextureView view) {
		return view instanceof com.mojang.blaze3d.vulkan.VulkanGpuTextureView v ? v.vkImageView() : 0L;
	}

	/** Tag the completed SDR frame immediately before Minecraft's normal present. */
	@Inject(method = "blitFromTexture", at = @At("TAIL"))
	private void caustica$submitStreamlineFrame(CommandEncoderBackend commandEncoder, GpuTextureView textureView,
			CallbackInfo ci) {
		if (this.currentImageIndex < 0) {
			return;
		}
		RtComposite.INSTANCE.submitStreamlineFrame((VulkanCommandEncoder) commandEncoder,
				this.swapchainWidth, this.swapchainHeight, this.swapchainImageFormat, false);
	}
}
