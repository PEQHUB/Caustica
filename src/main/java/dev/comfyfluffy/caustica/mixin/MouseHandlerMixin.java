package dev.comfyfluffy.caustica.mixin;

import dev.comfyfluffy.caustica.client.OfflineGroundTruth;
import dev.comfyfluffy.caustica.client.UltraScreenshot;
import dev.comfyfluffy.caustica.rt.pipeline.RtDlssFg;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFWCursorPosCallbackI;
import org.lwjgl.glfw.GLFWDropCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.glfw.GLFWScrollCallbackI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps mouse motion on Minecraft's existing accumulator without same-thread task dispatch per report. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Unique
    private GLFWCursorPosCallbackI caustica$directCursorCallback;

    @Shadow
    protected abstract void onMove(long window, double xpos, double ypos);

    @Redirect(
            method = "setup",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/InputConstants;setupMouseCallbacks(Lcom/mojang/blaze3d/platform/Window;Lorg/lwjgl/glfw/GLFWCursorPosCallbackI;Lorg/lwjgl/glfw/GLFWMouseButtonCallbackI;Lorg/lwjgl/glfw/GLFWScrollCallbackI;Lorg/lwjgl/glfw/GLFWDropCallbackI;)V"))
    private void caustica$installMouseCallbacks(
            Window window,
            GLFWCursorPosCallbackI vanillaCursorCallback,
            GLFWMouseButtonCallbackI buttonCallback,
            GLFWScrollCallbackI scrollCallback,
            GLFWDropCallbackI dropCallback) {
        if (caustica$directCursorCallback == null) {
            caustica$directCursorCallback = this::caustica$onCursorMove;
        }
        InputConstants.setupMouseCallbacks(
                window, caustica$directCursorCallback, buttonCallback, scrollCallback, dropCallback);
    }

    @Unique
    private void caustica$onCursorMove(long window, double xpos, double ypos) {
        // GLFW dispatches this callback from the thread polling events. Minecraft polls GLFW on its client thread.
        onMove(window, xpos, ypos);
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void caustica$freezeUltraScreenshotCamera(double frameTime, CallbackInfo ci) {
        if (UltraScreenshot.INSTANCE.active() || OfflineGroundTruth.INSTANCE.engaged()) {
            ci.cancel();
        }
    }

    @Inject(method = "onButton", at = @At("HEAD"))
    private void caustica$reflexTriggerFlash(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (action == 1 && button.button() == 0
                && RtDlssFg.INSTANCE.flashIndicatorDriverControlled()) {
            RtDlssFg.INSTANCE.triggerFlash();
        }
    }
}
