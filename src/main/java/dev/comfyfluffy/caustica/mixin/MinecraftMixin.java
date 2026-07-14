package dev.comfyfluffy.caustica.mixin;

import dev.comfyfluffy.caustica.rt.RtUiOverlay;
import dev.comfyfluffy.caustica.rt.pipeline.RtDlssFg;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Places Streamline Reflex sleep and PCL simulation markers around Minecraft's per-frame work. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "close", at = @At("HEAD"))
	private void caustica$destroyUiOverlayBeforeRendererShutdown(CallbackInfo ci) {
		RtUiOverlay.destroy();
	}

	@Inject(method = "runTick", at = @At("HEAD"))
	private void caustica$reflexSleepAndSimStart(boolean advanceGameTime, CallbackInfo ci) {
		RtDlssFg.INSTANCE.beginSimulationFrame();
	}

	@Inject(method = "runTick",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;renderFrame(Z)V"))
	private void caustica$reflexSimEnd(boolean advanceGameTime, CallbackInfo ci) {
		RtDlssFg.INSTANCE.marker(RtDlssFg.PCL_SIMULATION_END);
	}
}
