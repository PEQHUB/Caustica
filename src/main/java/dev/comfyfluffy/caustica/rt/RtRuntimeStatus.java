package dev.comfyfluffy.caustica.rt;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import dev.comfyfluffy.caustica.CausticaMod;
import dev.comfyfluffy.caustica.mixin.GpuDeviceAccessor;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Read-only runtime identity used by menus, logs, and the script bridge. */
public final class RtRuntimeStatus {
    private static final String ARTIFACT_SHA256 = computeArtifactSha256();

    private RtRuntimeStatus() {}

    public static String backend() {
        try {
            Object backend = ((GpuDeviceAccessor) RenderSystem.getDevice()).caustica$getBackend();
            return backend instanceof VulkanDevice ? "vulkan" : "opengl";
        } catch (Throwable ignored) {
            return "unavailable";
        }
    }

    public static boolean vulkan() {
        return "vulkan".equals(backend());
    }

    public static boolean rtContextReady() {
        return RtContext.currentOrNull() != null;
    }

    public static String unavailableReason() {
        if (!vulkan()) return "Caustica requires Vulkan; current backend is " + backend();
        if (!RtDeviceBringup.rtRequested()) return "Vulkan device did not request Caustica RT features";
        if (!rtContextReady()) return "Vulkan RT context is not ready";
        if (RtComposite.INSTANCE.hasFailed()) return "RT composite failure latch is set";
        return "ready";
    }

    public static String artifactSha256() {
        return ARTIFACT_SHA256;
    }

    private static String computeArtifactSha256() {
        try {
            URI location = CausticaMod.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(location);
            if (!Files.isRegularFile(path)) return "development-directory";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] block = new byte[1024 * 1024];
                int count;
                while ((count = input.read(block)) >= 0) digest.update(block, 0, count);
            }
            return HexFormat.of().withUpperCase().formatHex(digest.digest());
        } catch (Throwable t) {
            return "unavailable:" + t.getClass().getSimpleName();
        }
    }
}
