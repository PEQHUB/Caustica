package dev.comfyfluffy.caustica.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientShutdownContractTest {
    @Test
    void terrainDrainsBeforeWorkerPoolCanDiscardQueuedTasks() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/client/CausticaClient.java"));
        int shutdownMethod = source.indexOf("private static void shutdownRt()");
        int terrainShutdown = source.indexOf("RtTerrain.shutdown(ctx)", shutdownMethod);
        int workerShutdown = source.indexOf("RtWorkerPool.INSTANCE::shutdown", terrainShutdown);

        assertTrue(shutdownMethod >= 0);
        assertTrue(terrainShutdown > shutdownMethod);
        assertTrue(workerShutdown > terrainShutdown,
                "worker shutdownNow must not discard terrain tasks before their terminal callbacks");
    }

    @Test
    void materialRegistryDiesBeforeItsVmaAllocator() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/client/CausticaClient.java"));
        int shutdownMethod = source.indexOf("private static void shutdownRt()");
        int registryDestroy = source.indexOf("RtMaterialRegistry.INSTANCE::destroy", shutdownMethod);
        int contextDestroy = source.indexOf("ctx::destroy", registryDestroy);

        assertTrue(registryDestroy > shutdownMethod);
        assertTrue(contextDestroy > registryDestroy,
                "VMA-backed material buffers must be destroyed before RtContext destroys its allocator");
    }

    @Test
    void teardownIsBestEffortAndFailsClosed() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/client/CausticaClient.java"));
        int shutdownMethod = source.indexOf("private static void shutdownRt()");
        int failClosed = source.indexOf("rtInitDone = false", shutdownMethod);
        int firstCleanup = source.indexOf("failures.run(", shutdownMethod);

        assertTrue(failClosed > shutdownMethod && failClosed < firstCleanup);
        assertTrue(source.contains("catch (Throwable failure)"));
        assertTrue(source.contains("first.addSuppressed(failure)"));
    }
}
