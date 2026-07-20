package dev.comfyfluffy.caustica.rt;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Runtime truth for the optional, separately licensed NVIDIA SHaRC shader objects. */
public final class RtSharcSupport {
    private static final Properties METADATA = loadMetadata();
    private static volatile String runtimeFailure;

    private RtSharcSupport() {}

    public static boolean packaged() {
        return Boolean.parseBoolean(METADATA.getProperty("artifacts", "false"));
    }

    public static boolean explicitlyExcluded() {
        return Boolean.parseBoolean(METADATA.getProperty("explicitOptOut", "false"));
    }

    public static String version() {
        return METADATA.getProperty("version", "not packaged");
    }

    public static boolean available() {
        return packaged() && RtRuntimeStatus.vulkan() && RtDeviceBringup.sharcInt64AtomicsEnabled()
                && runtimeFailure == null;
    }

    public static String status() {
        if (!packaged()) return explicitlyExcluded()
                ? "SHaRC was explicitly excluded from this build"
                : "SHaRC shader objects are missing from this non-production build";
        if (!RtRuntimeStatus.vulkan()) return RtRuntimeStatus.unavailableReason();
        if (!RtDeviceBringup.sharcInt64AtomicsEnabled()) return "GPU lacks shaderBufferInt64Atomics";
        if (runtimeFailure != null) return runtimeFailure;
        return "available (NVIDIA SHaRC " + version() + ")";
    }

    public static void fail(String reason) {
        runtimeFailure = reason;
    }

    public static void clearRuntimeFailure() {
        runtimeFailure = null;
    }

    private static Properties loadMetadata() {
        Properties properties = new Properties();
        try (InputStream in = RtSharcSupport.class.getResourceAsStream("/caustica/sharc.properties")) {
            if (in != null) properties.load(in);
        } catch (IOException ignored) {
            // Missing/invalid metadata means unavailable; the baseline renderer remains valid.
        }
        return properties;
    }
}
