package dev.comfyfluffy.caustica.nrd;

import dev.comfyfluffy.caustica.CausticaMod;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

/** Extracts and loads the platform NRD bridge without touching the Windows-only Streamline runtime. */
public final class NrdRuntime {
    private static NrdLibrary library;
    private static boolean attempted;
    private static String unavailableReason = "Not initialized";
    private static volatile List<String> enabledDeviceExtensions = List.of();

    private NrdRuntime() {
    }

    public static synchronized NrdLibrary library() {
        if (library != null || attempted) {
            return library;
        }
        attempted = true;
        try {
            String platform = platform();
            String filename = platform.startsWith("windows") ? "nrdbridge.dll" : "libnrdbridge.so";
            String resource = "/caustica/natives/" + platform + "/" + filename;
            Path destination = gameDirectory().resolve("caustica-nrd").resolve("4.17.3").resolve(filename);
            try (InputStream stream = NrdRuntime.class.getResourceAsStream(resource)) {
                if (stream == null) {
                    Path development = Path.of("build", "native", "nrd_bridge", "release", filename);
                    if (!Files.isRegularFile(development)) {
                        throw new IOException("NRD native is not packaged for " + platform);
                    }
                    Files.createDirectories(destination.getParent());
                    Files.copy(development, destination, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.createDirectories(destination.getParent());
                    Path temporary = Files.createTempFile(destination.getParent(), filename + ".", ".tmp");
                    try {
                        Files.copy(stream, temporary, StandardCopyOption.REPLACE_EXISTING);
                        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
                    } finally {
                        Files.deleteIfExists(temporary);
                    }
                }
            }
            library = NrdLibrary.load(destination.toAbsolutePath());
            unavailableReason = "";
            CausticaMod.LOGGER.info("Loaded {} from {}", library.version(), destination.toAbsolutePath());
        } catch (Throwable throwable) {
            unavailableReason = throwable.getMessage() == null
                    ? throwable.getClass().getSimpleName() : throwable.getMessage();
            CausticaMod.LOGGER.warn("NRD is unavailable: {}", unavailableReason);
        }
        return library;
    }

    public static boolean packaged() {
        return NrdRuntime.class.getResource("/caustica/natives/" + platform() + "/"
                + (platform().startsWith("windows") ? "nrdbridge.dll" : "libnrdbridge.so")) != null;
    }

    public static String unavailableReason() {
        return unavailableReason;
    }

    /** Records the exact extension names submitted to Minecraft's Vulkan device creation. */
    public static void recordEnabledDeviceExtensions(Collection<String> extensions) {
        enabledDeviceExtensions = List.copyOf(extensions);
    }

    public static List<String> enabledDeviceExtensions() {
        return enabledDeviceExtensions;
    }

    static String platform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!(arch.equals("amd64") || arch.equals("x86_64"))) {
            throw new IllegalStateException("NRD currently packages x86-64 only, not " + arch);
        }
        if (os.contains("win")) return "windows-x64";
        if (os.contains("linux")) return "linux-x64";
        throw new IllegalStateException("NRD is not packaged for " + os);
    }

    private static Path gameDirectory() {
        try {
            return FabricLoader.getInstance().getGameDir();
        } catch (Throwable ignored) {
            return Path.of("run");
        }
    }
}
