package dev.comfyfluffy.caustica.rt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Source contracts for the live HDR swapchain transaction and its Streamline handoff. */
final class LiveHdrSwapchainContractTest {
    @Test
    void configureReselectsAndCommitsTheActualPair() throws IOException {
        String surface = source("src/main/java/dev/comfyfluffy/caustica/mixin/VulkanGpuSurfaceMixin.java");
        String hdr = source("src/main/java/dev/comfyfluffy/caustica/rt/RtHdr.java");

        assertTrue(surface.contains("@Mutable"));
        assertTrue(surface.contains("imageFormat(I)"));
        assertTrue(surface.contains("vkGetPhysicalDeviceSurfaceFormatsKHR"));
        assertTrue(surface.contains("stageSwapchainSelection"));
        assertTrue(hdr.contains("commitSwapchainSelection"));
        assertTrue(hdr.contains("effectiveHdrSwapchain = effectiveHdrForSelection"));
    }

    @Test
    void failedPqCreationHasOneGuardedSdrRetryAndClearsTruth() throws IOException {
        String surface = source("src/main/java/dev/comfyfluffy/caustica/mixin/VulkanGpuSurfaceMixin.java");
        String hdr = source("src/main/java/dev/comfyfluffy/caustica/rt/RtHdr.java");

        assertTrue(surface.contains("shouldRetrySdr(result, RtHdr.stagedEffective())"));
        assertTrue(surface.contains("stageSdrFallback"));
        assertTrue(surface.contains("@WrapMethod(method = \"configure\")"));
        assertTrue(hdr.contains("VK_ERROR_DEVICE_LOST"));
        assertTrue(hdr.contains("VK_ERROR_SURFACE_LOST_KHR"));
    }

    @Test
    void hdrRequestReconcilesWithDlssgOnTheNextTick() throws IOException {
        String coordinator = source(
                "src/main/java/dev/comfyfluffy/caustica/streamline/StreamlineSwapchainCoordinator.java");

        assertTrue(coordinator.contains("boolean desiredHdr = CausticaConfig.Rt.Hdr.enabled()"));
        assertTrue(coordinator.contains("desiredHdr != hdrRequestForSwapchain"));
        assertTrue(coordinator.contains("if (reconfigureRequested || configuring)"));
        assertTrue(coordinator.contains("suspendForSwapchainChange()"));
        assertTrue(coordinator.contains("onSwapchainConfigured"));
        assertTrue(coordinator.contains("requestTemporalReset()"));
    }

    @Test
    void restartDeferredHdrStateIsGone() throws IOException {
        String config = source("src/main/java/dev/comfyfluffy/caustica/CausticaConfig.java");
        String options = source("src/main/java/dev/comfyfluffy/caustica/mixin/OptionsMixin.java");

        assertFalse(config.contains("ENABLED_AT_STARTUP"));
        assertFalse(config.contains("pendingRestart()"));
        assertFalse(options.contains("alsoRestartForHdr"));
    }

    private static String source(String relative) throws IOException {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path candidate = cursor.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate).replace("\r\n", "\n");
            }
            cursor = cursor.getParent();
        }
        throw new IOException("Could not locate " + relative);
    }
}
