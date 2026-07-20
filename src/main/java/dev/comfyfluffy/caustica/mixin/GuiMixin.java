package dev.comfyfluffy.caustica.mixin;

import dev.comfyfluffy.caustica.client.CapturePause;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets capture modes use the same hidden pause state as vanilla's single-player pause screen. */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "isPausing", at = @At("HEAD"), cancellable = true)
    private void caustica$pauseLocalCapture(CallbackInfoReturnable<Boolean> cir) {
        if (CapturePause.shouldPause(Minecraft.getInstance())) {
            cir.setReturnValue(true);
        }
    }
}
