package dev.comfyfluffy.caustica.mixin;

import com.mojang.blaze3d.vulkan.VulkanDevice;
import dev.comfyfluffy.caustica.streamline.StreamlineRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shuts Streamline down after Minecraft closes its surface and before the Vulkan device is destroyed. */
@Mixin(VulkanDevice.class)
public abstract class VulkanDeviceMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void caustica$shutdownStreamlineBeforeDevice(CallbackInfo ci) {
        // Streamline is the sole DLSS-RR/DLSSG owner and releases both plugins before Vulkan destroys
        // the shared device. No second direct-NGX lifetime may be initialized alongside it.
        StreamlineRuntime.shutdown();
    }
}
