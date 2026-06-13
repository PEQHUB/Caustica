package dev.upscaler.mixin;

import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import dev.upscaler.UpscalerMod;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Standalone fallback for the Vulkan device-negotiation hook: adds the device
 * extensions the FFX runtime needs to the extension list vanilla enables at
 * vkCreateDevice time.
 *
 * <p>When Sodium is present this defers entirely to Sodium's
 * {@code DeviceExtensionRegistry} (fed via {@link dev.upscaler.client.SodiumCompat}),
 * which owns device negotiation — this is the "Phase 0" layering. Both paths
 * dedupe and Sodium returns a Set, so even if both ran the result would be
 * correct; deferring just keeps a single owner.
 *
 * <p>FFX resolves vkGetImageMemoryRequirements2KHR etc. through
 * vkGetDeviceProcAddr using the KHR-suffixed extension names; per Vulkan spec
 * that returns NULL unless the corresponding extension was enabled — even
 * though the functionality is core since 1.1 — and FFX then calls the NULL
 * pointer (verified: crash at amd_fidelityfx_vk.dll+0x1e5b0 building
 * VkMemoryRequirements2). Enabling the alias extensions is a behavioral no-op
 * for the rest of the engine.
 */
@Mixin(VulkanBackend.class)
public abstract class VulkanBackendMixin {
	private static final List<String> UPSCALER_WANTED_EXTENSIONS = List.of(
			"VK_KHR_get_memory_requirements2",
			"VK_KHR_dedicated_allocation");

	private static final boolean SODIUM_OWNS_NEGOTIATION =
			FabricLoader.getInstance().isModLoaded("sodium");

	@ModifyArgs(
			method = "createDevice(JLcom/mojang/blaze3d/shaders/ShaderSource;Lcom/mojang/blaze3d/shaders/GpuDebugOptions;Ljava/lang/Runnable;)Lcom/mojang/blaze3d/systems/GpuDevice;",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vulkan/VulkanBackend;createDevice(Ljava/util/Collection;Lcom/mojang/blaze3d/vulkan/VulkanPhysicalDevice;Ljava/util/Set;)Lorg/lwjgl/vulkan/VkDevice;"))
	private void upscaler$addDeviceExtensions(Args args) {
		if (SODIUM_OWNS_NEGOTIATION) {
			return; // Sodium's DeviceExtensionRegistry handles these
		}

		Collection<String> requested = args.get(0);
		VulkanPhysicalDevice physicalDevice = args.get(1);

		var augmented = new ArrayList<>(requested);
		for (String extension : UPSCALER_WANTED_EXTENSIONS) {
			if (augmented.contains(extension)) {
				continue;
			}
			if (physicalDevice.hasDeviceExtension(extension)) {
				augmented.add(extension);
				UpscalerMod.LOGGER.info("Enabling device extension {} for the upscaler runtime", extension);
			} else {
				UpscalerMod.LOGGER.warn("Device extension {} not supported by {} — upscaling will be unavailable",
						extension, physicalDevice.deviceName());
			}
		}
		args.set(0, augmented);
	}
}
