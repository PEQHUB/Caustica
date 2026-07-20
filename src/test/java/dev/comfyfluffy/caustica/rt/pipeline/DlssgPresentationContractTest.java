package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DlssgPresentationContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }

    @Test
    void optionsRemainFrameScopedBeforePresentStart() throws Exception {
        String fg = source("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtDlssFg.java");
        int beforePresent = fg.indexOf("public void beforePresent()");
        int options = fg.indexOf("applyOptionsForPresent();", beforePresent);
        int marker = fg.indexOf("marker(PCL_PRESENT_START);", beforePresent);
        assertTrue(beforePresent >= 0 && options > beforePresent && marker > options);
    }

    @Test
    void mailboxKeepsLogicalVsyncSeparateFromPhysicalFifo() throws Exception {
        String coordinator = source("src/main/java/dev/comfyfluffy/caustica/streamline/StreamlineSwapchainCoordinator.java");
        assertTrue(coordinator.replace("\r\n", "\n")
                .contains("vsyncRequested,\n                physicalFifo, pluginForSwapchain"));
        assertTrue(coordinator.contains("presentMode = GpuSurface.PresentMode.MAILBOX"));
        assertTrue(coordinator.contains("desiredPlugin = CausticaConfig.Rt.Fg.requested() && !physicalFifo"));
    }

    @Test
    void steadyStateRetirementNeverUsesDeviceIdle() throws Exception {
        String fg = source("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtDlssFg.java");
        int wait = fg.indexOf("private int waitForInputSlot");
        int fallback = fg.indexOf("private boolean enterBlockingQueueFallback", wait);
        assertTrue(wait >= 0 && fallback > wait);
        assertFalse(fg.substring(wait, fallback).contains("vkDeviceWaitIdle"));
        int drain = fg.indexOf("private void drainInputSlots", fallback);
        assertTrue(drain > fallback);
        assertFalse(fg.substring(fallback, drain).contains("vkDeviceWaitIdle"));
        assertTrue(fg.substring(fallback, drain).contains("dlssgFailed = true"));
    }

    @Test
    void synchronizedIsTheStableDefaultAndParallelIsExplicit() throws Exception {
        String config = source("src/main/java/dev/comfyfluffy/caustica/CausticaConfig.java");
        String fg = source("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtDlssFg.java");
        assertTrue(config.contains("\"frame-generation.queue-parallelism\", \"synchronized\""));
        assertTrue(fg.contains("activeQueuePolicy == QueuePolicy.PARALLEL && !queueFallback"));
        assertEquals("synchronized", RtDlssFg.canonicalQueuePolicy("auto"));
        assertEquals("synchronized", RtDlssFg.canonicalQueuePolicy("safe"));
        assertEquals("parallel", RtDlssFg.canonicalQueuePolicy("no-client-queues"));
        assertEquals("parallel", RtDlssFg.canonicalQueuePolicy("parallel"));
    }

    @Test
    void liveQueueTransitionSuspendsGenerationAndNeverUsesDeviceIdle() throws Exception {
        String fg = source("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtDlssFg.java");
        int transition = fg.indexOf("private boolean prepareQueuePolicyForFrame");
        int drain = fg.indexOf("private boolean retireInputsForLiveTransition", transition);
        int destructiveDrain = fg.indexOf("private void drainInputSlots", drain);
        assertTrue(transition >= 0 && drain > transition && destructiveDrain > drain);
        assertTrue(fg.substring(transition, drain).contains("queuePolicyTransitionFrame = true"));
        assertTrue(fg.substring(transition, drain).contains("forceResetNextSubmission = true"));
        assertFalse(fg.substring(transition, destructiveDrain).contains("vkDeviceWaitIdle"));
    }

    @Test
    void profilerMeasuresThePreviouslyUnaccountedProxyPresent() throws Exception {
        String mixin = source("src/main/java/dev/comfyfluffy/caustica/mixin/VulkanGpuSurfaceMixin.java");
        String stats = source("src/main/java/dev/comfyfluffy/caustica/rt/RtFrameStats.java");
        int scope = mixin.indexOf("stage(\"frame.present\")");
        int present = mixin.indexOf("StreamlineRuntime.vkQueuePresent", scope);
        assertTrue(scope >= 0 && present > scope);
        assertTrue(stats.contains("\"frame.present\""));
        assertTrue(stats.contains("\"fgQueueParallel\""));
    }

    @Test
    void reportKeepsApplicationAndProxyCountsDistinct() throws Exception {
        String report = source("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/StreamlineAcceptanceReport.java");
        assertTrue(report.contains("\\\"applicationImageCount\\\""));
        assertTrue(report.contains("\\\"requestedNativeMinImageCount\\\""));
        assertTrue(report.contains("\\\"proxyVisibleImageCount\\\""));
        assertFalse(report.contains("\\\"physicalProxyImageCount\\\""));
    }
}
