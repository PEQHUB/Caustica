package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class TlasRingBuildContractTest {
    @Test
    void frameTlasRingDoesNotRefitWithoutCompletionOwnedSlots() throws Exception {
        String accel = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/accel/RtAccel.java"));
        String executor = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/RtGpuExecutor.java"));
        String composite = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java"));
        String frameStats = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/RtFrameStats.java"));

        assertTrue(accel.contains("tlas.label + \" build\""));
        assertTrue(!accel.contains("tlas.update"));
        assertTrue(!accel.contains("updatesSinceBuild"));
        assertTrue(accel.contains("waitForGraphicsValue(slot.lastGraphicsUse)"));
        assertTrue(accel.contains("slot.lastGraphicsUse = graphicsUse"));
        assertTrue(executor.indexOf("latestGraphicsUseValue.accumulateAndGet(graphicsValue, Math::max)")
                < executor.indexOf("public void endGraphicsTerrainUse"));
        assertTrue(composite.indexOf("beginGraphicsTerrainUse(encoder)")
                < composite.indexOf("RtAccel.prepareTlas"));
        assertTrue(composite.indexOf("RtAccel.markTlasUsed(frameTlas, graphicsUse)")
                < composite.indexOf("encoder.execute(cmd)"));
        assertTrue(frameStats.contains("\"frame.prepareTlas.wait\""));
        assertTrue(frameStats.contains("\"frame.prepareTlas.packBase\""));
        assertTrue(frameStats.contains("\"frame.prepareTlas.packDynamic\""));
        assertTrue(frameStats.contains("\"frame.prepareTlas.flush\""));
        assertTrue(accel.contains("endStage(\"frame.prepareTlas.wait\""));
        assertTrue(accel.contains("endStage(\"frame.prepareTlas.packBase\""));
        assertTrue(accel.contains("endStage(\"frame.prepareTlas.packDynamic\""));
        assertTrue(accel.contains("endStage(\"frame.prepareTlas.flush\""));
    }
}
