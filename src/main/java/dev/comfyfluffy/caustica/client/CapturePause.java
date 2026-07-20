package dev.comfyfluffy.caustica.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

/** Supplies a hidden vanilla pause request while a local capture owns the scene. */
public final class CapturePause {
    private CapturePause() {
    }

    /** True while a renderer capture must observe one immutable scene across all submitted frames. */
    public static boolean sceneFreezeRequested() {
        return UltraScreenshot.INSTANCE.active() || OfflineGroundTruth.INSTANCE.active();
    }

    /**
     * Keep the idle and multiplayer paths to one cheap state check. Minecraft's normal pause pipeline
     * then freezes both client and integrated-server simulation without opening a screen or replacing
     * either tick-rate manager. A LAN-published world must continue running like multiplayer.
     */
    public static boolean shouldPause(Minecraft minecraft) {
        if (!sceneFreezeRequested() && !OfflineGroundTruth.INSTANCE.engaged()) {
            return false;
        }
        if (!minecraft.hasSingleplayerServer()) {
            return false;
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        return server != null && !server.isPublished();
    }
}
