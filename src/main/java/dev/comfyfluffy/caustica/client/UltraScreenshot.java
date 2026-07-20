package dev.comfyfluffy.caustica.client;

import dev.comfyfluffy.caustica.CausticaConfig;
import dev.comfyfluffy.caustica.CausticaMod;
import dev.comfyfluffy.caustica.rt.RtComposite;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * One-shot high-quality screenshot capture. The user's persistent settings are never written: DLAA and
 * eight SPP are temporary runtime overrides, restored immediately after the 32nd fresh RT frame. An
 * unshared single-player world uses Minecraft's native pause state while those frames are rendered.
 */
public final class UltraScreenshot {
    public static final UltraScreenshot INSTANCE = new UltraScreenshot();
    public static final KeyMapping KEY = new KeyMapping(
            "key.caustica.ultra_screenshot", GLFW.GLFW_KEY_F4, CausticaKeyMappings.CATEGORY);

    private static final int DLAA_QUALITY = 5;
    private static final int SCREENSHOT_SPP = 8;
    private static final int ACCUMULATION_FRAMES = 32;
    private static final int MAX_WAIT_TICKS = 20 * 20;

    private boolean active;
    private int frames;
    private int waitTicks;
    private int previousSpp;
    private int previousQuality;

    private UltraScreenshot() {
    }

    public boolean active() {
        return active;
    }

    /** Start a capture, or cancel and restore when F4 is pressed a second time. */
    public void toggle(Minecraft minecraft) {
        if (active) {
            restore(minecraft, Component.translatable("caustica.status.ultraScreenshot.cancelled"));
            return;
        }
        if (minecraft.level == null || minecraft.player == null) {
            notify(minecraft, Component.translatable("caustica.status.ultraScreenshot.requiresWorld"));
            return;
        }
        if (!CausticaConfig.Rt.ENABLED.value() || !CausticaConfig.Rt.DlssRr.ENABLED.value()) {
            notify(minecraft, Component.translatable("caustica.status.ultraScreenshot.requiresDlssRr"));
            return;
        }
        if (OfflineGroundTruth.INSTANCE.engaged()) {
            notify(minecraft, Component.translatable("caustica.status.ultraScreenshot.offlineActive"));
            return;
        }

        previousSpp = CausticaConfig.Rt.Composite.SPP.value();
        previousQuality = CausticaConfig.Rt.DlssRr.QUALITY.value();
        frames = 0;
        waitTicks = 0;
        active = true;

        CausticaConfig.Rt.Composite.SPP.set(SCREENSHOT_SPP);
        CausticaConfig.Rt.DlssRr.QUALITY.set(DLAA_QUALITY);
        RtComposite.INSTANCE.requestTemporalReset();
        notify(minecraft, Component.translatable("caustica.status.ultraScreenshot.started",
                SCREENSHOT_SPP, ACCUMULATION_FRAMES));
    }

    /** Abort safely if the world disappears while the shared capture-pause hook owns scene freezing. */
    public void tick(Minecraft minecraft) {
        if (!active) {
            return;
        }
        if (minecraft.level == null || minecraft.player == null) {
            restore(minecraft, Component.translatable("caustica.status.ultraScreenshot.worldClosed"));
            return;
        }
        if (++waitTicks > MAX_WAIT_TICKS) {
            restore(minecraft, Component.translatable("caustica.status.ultraScreenshot.timedOut"));
            return;
        }
    }

    /** Called at GameRenderer.render TAIL, after the final world and UI image exists. */
    public void frameRendered(Minecraft minecraft) {
        if (!active || !RtComposite.INSTANCE.producedFreshDlssRrFrame()) {
            return;
        }
        frames++;
        if (frames < ACCUMULATION_FRAMES) {
            return;
        }

        try {
            Screenshot.grab(minecraft, false);
        } catch (Throwable t) {
            CausticaMod.LOGGER.error("Ultra screenshot capture failed", t);
            notify(minecraft, Component.translatable("caustica.status.ultraScreenshot.failed"));
        } finally {
            restore(minecraft, null);
        }
    }

    private void restore(Minecraft minecraft, Component message) {
        if (!active) {
            return;
        }
        active = false;
        CausticaConfig.Rt.Composite.SPP.set(previousSpp);
        CausticaConfig.Rt.DlssRr.QUALITY.set(previousQuality);
        RtComposite.INSTANCE.requestTemporalReset();
        frames = 0;
        waitTicks = 0;
        if (message != null) {
            notify(minecraft, message);
        }
    }

    private static void notify(Minecraft minecraft, Component message) {
        if (minecraft.player != null) {
            minecraft.player.sendOverlayMessage(message);
        }
    }
}
