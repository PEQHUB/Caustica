package dev.comfyfluffy.caustica.streamline;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards process-fatal rollback and device-wide host-synchronization invariants. */
final class StreamlineFailureSafetyContractTest {
    @Test
    void failedShutdownPoisonsFallbackAndInitialization() throws Exception {
        String runtime = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/streamline/StreamlineRuntime.java"));

        assertTrue(runtime.contains("INSTANCE.rollbackUnsafe = true"));
        assertTrue(runtime.contains("if (INSTANCE.rollbackUnsafe)"));
        assertTrue(runtime.contains("if (rollbackUnsafe)"));
    }

    @Test
    void deviceIdleAndAsyncSubmissionShareOneHostLock() throws Exception {
        String runtime = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/streamline/StreamlineRuntime.java"));
        String context = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/RtContext.java"));

        assertTrue(runtime.contains("synchronized (VULKAN_DEVICE_QUEUE_HOST_LOCK)"));
        assertTrue(context.contains("return StreamlineRuntime.vulkanDeviceQueueHostLock()"));
    }
}
