package dev.comfyfluffy.caustica.rt.pipeline;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import dev.comfyfluffy.caustica.CausticaConfig;
import dev.comfyfluffy.caustica.mixin.GpuDeviceAccessor;
import java.util.Locale;

/** One global reconstruction policy shared by every vendor and operating system. */
public final class RtReconstruction {
    public enum Backend { OFF, DLSS_RR, NRD }

    private RtReconstruction() {
    }

    public static Backend backend() {
        String configured = CausticaConfig.Rt.Reconstruction.BACKEND.get();
        return switch (configured) {
            case "off" -> Backend.OFF;
            case "dlss-rr" -> CausticaConfig.Rt.DlssRr.ENABLED.value() ? Backend.DLSS_RR : Backend.OFF;
            case "nrd" -> Backend.NRD;
            default -> automaticBackend();
        };
    }

    private static Backend automaticBackend() {
        if (!(RenderSystem.getDevice() instanceof GpuDeviceAccessor accessor)
                || !(accessor.caustica$getBackend() instanceof VulkanDevice device)) {
            return isLinux() ? Backend.NRD : Backend.OFF;
        }
        int vendor = physicalVendor(device);
        if (isLinux() || vendor == 0x1002 || vendor == 0x8086) {
            return Backend.NRD;
        }
        return vendor == 0x10DE && CausticaConfig.Rt.DlssRr.ENABLED.value() && !RtDlssRr.INSTANCE.failed()
                ? Backend.DLSS_RR : Backend.NRD;
    }

    private static int physicalVendor(VulkanDevice device) {
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkPhysicalDeviceProperties properties =
                    org.lwjgl.vulkan.VkPhysicalDeviceProperties.calloc(stack);
            org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceProperties(
                    device.vkDevice().getPhysicalDevice(), properties);
            return properties.vendorID();
        }
    }

    public static boolean enabled() {
        return switch (backend()) {
            case DLSS_RR -> RtDlssRr.INSTANCE.isOperational();
            case NRD -> RtNrd.INSTANCE.isOperational();
            case OFF -> false;
        };
    }

    public static boolean usesDlss() {
        return backend() == Backend.DLSS_RR;
    }

    public static boolean usesNrd() {
        return backend() == Backend.NRD;
    }

    public static int resourceIdentity() {
        int identity = backend().ordinal();
        if (usesDlss()) return 31 * identity + RtDlssRr.quality();
        if (usesNrd()) {
            identity = 31 * identity + CausticaConfig.Rt.Nrd.DENOISER.get().hashCode();
            identity = 31 * identity + Boolean.hashCode(CausticaConfig.Rt.Nrd.SPHERICAL_HARMONICS.value());
        }
        return identity;
    }

    public static int[] queryRenderSize(int displayWidth, int displayHeight) {
        if (usesDlss()) return RtDlssRr.INSTANCE.queryOptimalRenderSize(displayWidth, displayHeight);
        if (usesNrd()) return new int[] {displayWidth, displayHeight};
        return null;
    }

    public static void requestHistoryReset() {
        RtDlssRr.INSTANCE.requestHistoryReset();
        RtNrd.INSTANCE.requestHistoryReset();
    }

    public static void resetFailureLatches() {
        RtDlssRr.INSTANCE.resetFailureLatch();
        RtNrd.INSTANCE.resetFailureLatch();
    }

    public static void destroy() {
        RtDlssRr.INSTANCE.destroy();
        RtNrd.INSTANCE.destroy();
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }
}
