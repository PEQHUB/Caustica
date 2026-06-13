package dev.upscaler.client;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import dev.upscaler.UpscalerMod;
import dev.upscaler.mixin.CommandEncoderAccessor;
import dev.upscaler.mixin.GpuDeviceAccessor;
import dev.upscaler.ngx.NgxLibrary;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageCopy;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkInstance;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * DLSS Super Resolution backend over the local NGX shim.
 *
 * <p>This intentionally follows {@link FsrPipeline}'s proven render seam: render
 * the world at low resolution, generate camera-only depth reprojection MVs, run
 * the native temporal upscaler into a storage-capable raw image, then copy the
 * result back into the native main target before hand/HUD rendering continues.
 */
public final class DlssPipeline {
	public static final DlssPipeline INSTANCE = new DlssPipeline();

	private static final String SHIM_DLL = "ngxshim.dll";
	private static final String DLSS_DLL = "nvngx_dlss.dll";

	private static final int QUALITY_MAX_QUALITY = 2; // NVSDK_NGX_PerfQuality_Value_MaxQuality
	private static final int FEATURE_FLAG_MV_LOW_RES = 1 << 1;
	private static final int FEATURE_FLAG_DEPTH_INVERTED = 1 << 3;
	private static final int FEATURE_FLAG_AUTO_EXPOSURE = 1 << 6;

	private final boolean enabledByProperty = !"false".equalsIgnoreCase(System.getProperty("upscaler.dlss", "true"));
	private final int qualityMode = Integer.getInteger("upscaler.dlss.quality", QUALITY_MAX_QUALITY);
	private final float mvScaleX = Float.parseFloat(System.getProperty("upscaler.mvScaleX", "0.5"));
	private final float mvScaleY = Float.parseFloat(System.getProperty("upscaler.mvScaleY", "0.5"));

	private boolean failed;
	private boolean initialized;
	private boolean loggedActive;
	private boolean loggedMissingNvngx;

	private NgxLibrary lib;
	private MemorySegment feature = MemorySegment.NULL;
	private int featureRenderWidth = -1;
	private int featureRenderHeight = -1;
	private int featureDisplayWidth = -1;
	private int featureDisplayHeight = -1;

	private GpuTexture mvTexture;
	private GpuTextureView mvTextureView;
	private GpuBuffer mvParamsBuffer;

	private long outputImage;
	private long outputImageView;
	private long outputAllocation;
	private boolean outputNeedsLayoutInit;

	private long lastFrameNanos;
	private boolean resetNextDispatch;

	private final Matrix4f prevViewProj = new Matrix4f();
	private final Matrix4f reprojectMatrix = new Matrix4f();
	private double prevCamX;
	private double prevCamY;
	private double prevCamZ;
	private boolean hasPrevFrame;
	private boolean reprojectValid;

	private static final BindGroupLayout MV_BIND_GROUP =
			BindGroupLayout.builder()
					.withSampler("InDepth")
					.withUniform("MvParams", UniformType.UNIFORM_BUFFER)
					.build();

	private static final RenderPipeline MV_PIPELINE =
			RenderPipeline.builder()
					.withLocation(Identifier.fromNamespaceAndPath("upscaler", "pipeline/dlss_mv"))
					.withBindGroupLayout(MV_BIND_GROUP)
					.withVertexShader("core/screenquad")
					.withFragmentShader(Identifier.fromNamespaceAndPath("upscaler", "mv"))
					.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RG16_FLOAT, 0xFFFFFFFF))
					.withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
					.withCull(false)
					.build();

	private DlssPipeline() {
	}

	public void captureFrame(Matrix4f projection, Matrix4f viewRotation,
	                         net.minecraft.world.phys.Vec3 cameraPos) {
		Matrix4f curViewProj = new Matrix4f(projection).mul(viewRotation);
		if (this.hasPrevFrame) {
			float dx = (float) (cameraPos.x - this.prevCamX);
			float dy = (float) (cameraPos.y - this.prevCamY);
			float dz = (float) (cameraPos.z - this.prevCamZ);
			this.reprojectMatrix.set(this.prevViewProj)
					.mul(new Matrix4f().translation(dx, dy, dz))
					.mul(new Matrix4f(curViewProj).invert());
			this.reprojectValid = true;
		} else {
			this.reprojectValid = false;
		}

		this.prevViewProj.set(curViewProj);
		this.prevCamX = cameraPos.x;
		this.prevCamY = cameraPos.y;
		this.prevCamZ = cameraPos.z;
		this.hasPrevFrame = true;
	}

	public boolean dispatch(GpuTexture lowResColor, GpuTextureView lowResColorView,
	                        GpuTexture lowResDepth, GpuTextureView lowResDepthView,
	                        int renderWidth, int renderHeight,
	                        GpuTexture nativeColor, int displayWidth, int displayHeight) {
		if (!this.enabledByProperty || this.failed) {
			return false;
		}
		if (!(((GpuDeviceAccessor) RenderSystem.getDevice()).upscaler$getBackend() instanceof VulkanDevice device)) {
			return false;
		}

		try {
			ensureInitialized(device);
			ensureResources(device, renderWidth, renderHeight, displayWidth, displayHeight);
			boolean mvValid = renderMotionVectors(lowResDepthView);
			recordDispatch(device, lowResColor, lowResColorView, lowResDepth, lowResDepthView,
					renderWidth, renderHeight, nativeColor, displayWidth, displayHeight, mvValid);
			if (!this.loggedActive) {
				this.loggedActive = true;
				UpscalerMod.LOGGER.info("DLSS upscaling active: {}x{} -> {}x{} (quality mode {})",
						renderWidth, renderHeight, displayWidth, displayHeight, this.qualityMode);
			}
			return true;
		} catch (Throwable t) {
			try {
				destroyFrameResources(device);
			} catch (Throwable cleanupError) {
				t.addSuppressed(cleanupError);
			}
			this.failed = true;
			UpscalerMod.LOGGER.error("DLSS dispatch failed; falling back to the next upscaler", t);
			return false;
		}
	}

	private void ensureInitialized(VulkanDevice device) {
		if (this.initialized) {
			return;
		}

		Path shim = locate(SHIM_DLL);
		if (shim == null) {
			throw new IllegalStateException(SHIM_DLL + " not found (run dir natives/ or -Dupscaler.ngx.path)");
		}
		Path nativesDir = shim.getParent();
		if (!this.loggedMissingNvngx && nativesDir != null && !Files.isRegularFile(nativesDir.resolve(DLSS_DLL))) {
			this.loggedMissingNvngx = true;
			UpscalerMod.LOGGER.warn("{} was not found next to {}; NGX feature creation may fail", DLSS_DLL, SHIM_DLL);
		}

		this.lib = NgxLibrary.load(shim);

		Path dataPath = FabricLoader.getInstance().getGameDir().resolve("upscaler-ngx");
		try {
			Files.createDirectories(dataPath);
		} catch (Exception e) {
			UpscalerMod.LOGGER.warn("Could not create NGX data path {}", dataPath, e);
		}

		VkInstance instance = device.vkDevice().getPhysicalDevice().getInstance();
		try (Arena arena = Arena.ofConfined()) {
			long gdpa;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				gdpa = VK10.vkGetInstanceProcAddr(instance, stack.ASCII("vkGetDeviceProcAddr"));
			}

			int rc = this.lib.init(0L, wideString(arena, dataPath.toString()),
					instance.address(), device.vkDevice().getPhysicalDevice().address(), device.vkDevice().address(),
					0L, gdpa, wideString(arena, nativesDir == null ? "" : nativesDir.toString()));
			if (ngxFailed(rc)) {
				throw new IllegalStateException("ngxshim_init failed: 0x" + Integer.toHexString(rc)
						+ " last=0x" + Integer.toHexString(this.lib.lastResult()));
			}
			if (!this.lib.dlssAvailable()) {
				throw new IllegalStateException("DLSS is not available on this system");
			}
		}

		this.initialized = true;
	}

	private void ensureResources(VulkanDevice device, int renderWidth, int renderHeight, int displayWidth, int displayHeight) {
		if (this.featureRenderWidth == renderWidth
				&& this.featureRenderHeight == renderHeight
				&& this.featureDisplayWidth == displayWidth
				&& this.featureDisplayHeight == displayHeight
				&& !isNull(this.feature)) {
			return;
		}

		destroyFrameResources(device);

		var blazeDevice = RenderSystem.getDevice();
		this.mvTexture = blazeDevice.createTexture(() -> "Upscaler DLSS MV",
				GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
				GpuFormat.RG16_FLOAT, renderWidth, renderHeight, 1, 1);
		this.mvTextureView = blazeDevice.createTextureView(this.mvTexture);
		blazeDevice.createCommandEncoder().clearColorTexture(this.mvTexture, new org.joml.Vector4f(0.0f));

		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkImageCreateInfo imageCi = VkImageCreateInfo.calloc(stack).sType$Default()
					.imageType(VK10.VK_IMAGE_TYPE_2D)
					.format(VK10.VK_FORMAT_R8G8B8A8_UNORM)
					.mipLevels(1)
					.arrayLayers(1)
					.samples(VK10.VK_SAMPLE_COUNT_1_BIT)
					.tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
					.usage(VK10.VK_IMAGE_USAGE_STORAGE_BIT | VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
					.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
					.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
			imageCi.extent().set(displayWidth, displayHeight, 1);

			VmaAllocationCreateInfo allocCi = VmaAllocationCreateInfo.calloc(stack)
					.usage(Vma.VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);
			LongBuffer pImage = stack.mallocLong(1);
			PointerBuffer pAlloc = stack.mallocPointer(1);
			int result = Vma.vmaCreateImage(device.vma(), imageCi, allocCi, pImage, pAlloc, null);
			if (result != VK10.VK_SUCCESS) {
				throw new IllegalStateException("vmaCreateImage(DLSS output) failed: " + result);
			}
			this.outputImage = pImage.get(0);
			this.outputAllocation = pAlloc.get(0);

			VkImageViewCreateInfo viewCi = VkImageViewCreateInfo.calloc(stack).sType$Default()
					.image(this.outputImage)
					.viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
					.format(VK10.VK_FORMAT_R8G8B8A8_UNORM);
			viewCi.subresourceRange()
					.aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
					.baseMipLevel(0)
					.levelCount(1)
					.baseArrayLayer(0)
					.layerCount(1);
			LongBuffer pView = stack.mallocLong(1);
			result = VK10.vkCreateImageView(device.vkDevice(), viewCi, null, pView);
			if (result != VK10.VK_SUCCESS) {
				throw new IllegalStateException("vkCreateImageView(DLSS output) failed: " + result);
			}
			this.outputImageView = pView.get(0);
		}

		this.featureRenderWidth = renderWidth;
		this.featureRenderHeight = renderHeight;
		this.featureDisplayWidth = displayWidth;
		this.featureDisplayHeight = displayHeight;
		this.outputNeedsLayoutInit = true;
		this.resetNextDispatch = true;
	}

	private boolean renderMotionVectors(GpuTextureView lowResDepthView) {
		var encoder = RenderSystem.getDevice().createCommandEncoder();
		if (!this.reprojectValid) {
			encoder.clearColorTexture(this.mvTexture, new org.joml.Vector4f(0.0f));
			return false;
		}

		if (this.mvParamsBuffer == null) {
			this.mvParamsBuffer = RenderSystem.getDevice().createBuffer(() -> "Upscaler DLSS MV params",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 64);
		}
		ByteBuffer matrixData = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());
		this.reprojectMatrix.get(matrixData);
		encoder.writeToBuffer(this.mvParamsBuffer.slice(), matrixData);

		try (var pass = encoder.createRenderPass(() -> "Upscaler DLSS MV", this.mvTextureView, Optional.empty())) {
			pass.setPipeline(MV_PIPELINE);
			pass.bindTexture("InDepth", lowResDepthView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.setUniform("MvParams", this.mvParamsBuffer);
			pass.draw(3, 1, 0, 0);
		}
		return true;
	}

	private void recordDispatch(VulkanDevice device, GpuTexture lowResColor, GpuTextureView lowResColorView,
	                            GpuTexture lowResDepth, GpuTextureView lowResDepthView,
	                            int renderWidth, int renderHeight,
	                            GpuTexture nativeColor, int displayWidth, int displayHeight, boolean mvValid) {
		var encoder = (VulkanCommandEncoder) ((CommandEncoderAccessor) RenderSystem.getDevice().createCommandEncoder()).upscaler$getBackend();
		VkCommandBuffer cmd = encoder.allocateAndBeginTransientCommandBuffer();

		long now = System.nanoTime();
		float frameTimeMs = this.lastFrameNanos == 0 ? 16.6f
				: Math.clamp((now - this.lastFrameNanos) / 1_000_000.0f, 0.1f, 200.0f);
		this.lastFrameNanos = now;

		try (MemoryStack stack = MemoryStack.stackPush()) {
			if (this.outputNeedsLayoutInit) {
				this.outputNeedsLayoutInit = false;
				VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack).sType$Default();
				barrier.oldLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
				barrier.newLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
				barrier.srcAccessMask(0);
				barrier.dstAccessMask(VK10.VK_ACCESS_MEMORY_READ_BIT | VK10.VK_ACCESS_MEMORY_WRITE_BIT);
				barrier.srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED);
				barrier.dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED);
				barrier.image(this.outputImage);
				barrier.subresourceRange().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
				VK12.vkCmdPipelineBarrier(cmd, VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, barrier);
			}

			if (isNull(this.feature)) {
				int flags = FEATURE_FLAG_MV_LOW_RES | FEATURE_FLAG_DEPTH_INVERTED | FEATURE_FLAG_AUTO_EXPOSURE;
				this.feature = this.lib.createDlss(cmd.address(), renderWidth, renderHeight, displayWidth, displayHeight,
						this.qualityMode, flags);
				if (isNull(this.feature)) {
					throw new IllegalStateException("ngxshim_create_dlss failed: last=0x"
							+ Integer.toHexString(this.lib.lastResult()));
				}
			}

			VulkanCommandEncoder.memoryBarrier(cmd, stack);

			int rc = this.lib.evaluate(cmd.address(), this.feature,
					vkImageView(lowResColorView), vkImage(lowResColor), VK10.VK_FORMAT_R8G8B8A8_UNORM,
					vkImageView(lowResDepthView), vkImage(lowResDepth), VK10.VK_FORMAT_D32_SFLOAT,
					vkImageView(this.mvTextureView), vkImage(this.mvTexture), VK10.VK_FORMAT_R16G16_SFLOAT,
					this.outputImageView, this.outputImage, VK10.VK_FORMAT_R8G8B8A8_UNORM,
					renderWidth, renderHeight, displayWidth, displayHeight,
					FsrPipeline.INSTANCE.jitterPixelsX(), FsrPipeline.INSTANCE.jitterPixelsY(),
					mvValid ? renderWidth * this.mvScaleX : 1.0f,
					mvValid ? renderHeight * this.mvScaleY : 1.0f,
					this.resetNextDispatch ? 1 : 0, frameTimeMs);
			if (ngxFailed(rc)) {
				throw new IllegalStateException("ngxshim_evaluate failed: 0x" + Integer.toHexString(rc)
						+ " last=0x" + Integer.toHexString(this.lib.lastResult()));
			}
			this.resetNextDispatch = false;

			VulkanCommandEncoder.memoryBarrier(cmd, stack);

			VkImageCopy.Buffer region = VkImageCopy.calloc(1, stack);
			region.srcSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0).layerCount(1);
			region.dstSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0).layerCount(1);
			region.extent().set(displayWidth, displayHeight, 1);
			VK10.vkCmdCopyImage(cmd, this.outputImage, VK10.VK_IMAGE_LAYOUT_GENERAL,
					vkImage(nativeColor), VK10.VK_IMAGE_LAYOUT_GENERAL, region);

			VulkanCommandEncoder.memoryBarrier(cmd, stack);
		}

		int endResult = VK10.vkEndCommandBuffer(cmd);
		if (endResult != VK10.VK_SUCCESS) {
			throw new IllegalStateException("vkEndCommandBuffer(DLSS) failed: " + endResult);
		}
		encoder.execute(cmd);
	}

	private void destroyFrameResources(VulkanDevice device) {
		boolean hadAny = !isNull(this.feature) || this.outputImage != 0 || this.mvTexture != null;
		if (!hadAny) {
			return;
		}
		VK10.vkDeviceWaitIdle(device.vkDevice());

		if (!isNull(this.feature)) {
			this.lib.release(this.feature);
			this.feature = MemorySegment.NULL;
		}
		if (this.mvTextureView != null) {
			this.mvTextureView.close();
			this.mvTextureView = null;
		}
		if (this.mvTexture != null) {
			this.mvTexture.close();
			this.mvTexture = null;
		}
		if (this.mvParamsBuffer != null) {
			this.mvParamsBuffer.close();
			this.mvParamsBuffer = null;
		}
		if (this.outputImageView != 0) {
			VK10.vkDestroyImageView(device.vkDevice(), this.outputImageView, null);
			this.outputImageView = 0;
		}
		if (this.outputImage != 0) {
			Vma.vmaDestroyImage(device.vma(), this.outputImage, this.outputAllocation);
			this.outputImage = 0;
			this.outputAllocation = 0;
		}

		this.hasPrevFrame = false;
		this.reprojectValid = false;
		this.featureRenderWidth = -1;
		this.featureRenderHeight = -1;
		this.featureDisplayWidth = -1;
		this.featureDisplayHeight = -1;
	}

	private static long vkImage(GpuTexture texture) {
		Long sodiumHandle = SodiumCompat.vkImage(texture);
		if (sodiumHandle != null) {
			return sodiumHandle;
		}
		if (texture instanceof VulkanGpuTexture vulkanTexture) {
			return vulkanTexture.vkImage();
		}
		throw new UnsupportedOperationException("Texture is not backed by Vulkan");
	}

	private static long vkImageView(GpuTextureView textureView) {
		Long sodiumHandle = SodiumCompat.vkImageView(textureView);
		if (sodiumHandle != null) {
			return sodiumHandle;
		}
		if (textureView instanceof VulkanGpuTextureView vulkanTextureView) {
			return vulkanTextureView.vkImageView();
		}
		throw new UnsupportedOperationException("Texture view is not backed by Vulkan");
	}

	private static boolean isNull(MemorySegment segment) {
		return segment == null || segment.equals(MemorySegment.NULL);
	}

	/** NVSDK_NGX_Result: failure when top 12 bits == 0xBAD. */
	private static boolean ngxFailed(int result) {
		return (result & 0xFFF00000) == 0xBAD00000;
	}

	private static MemorySegment wideString(Arena arena, String s) {
		byte[] utf16 = s.getBytes(StandardCharsets.UTF_16LE);
		MemorySegment seg = arena.allocate(utf16.length + 2L);
		MemorySegment.copy(utf16, 0, seg, ValueLayout.JAVA_BYTE, 0, utf16.length);
		seg.set(ValueLayout.JAVA_BYTE, utf16.length, (byte) 0);
		seg.set(ValueLayout.JAVA_BYTE, utf16.length + 1, (byte) 0);
		return seg;
	}

	private static Path locate(String name) {
		String override = System.getProperty("upscaler.ngx.path");
		if (override != null && name.equals(SHIM_DLL)) {
			Path p = Path.of(override);
			return Files.isRegularFile(p) ? p : null;
		}
		Path runDir = FabricLoader.getInstance().getGameDir();
		Path[] candidates = {runDir.resolve("natives").resolve(name), runDir.resolve(name)};
		for (Path candidate : candidates) {
			if (Files.isRegularFile(candidate)) {
				return candidate;
			}
		}
		return null;
	}
}
