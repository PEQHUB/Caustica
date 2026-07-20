package dev.comfyfluffy.caustica.rt.pipeline;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerrainTelemetryContractTest {
    @Test
    void everyTerrainCounterUsedByTheSchedulerIsRegisteredAndBridgeLatencyIsHonest() throws Exception {
        String terrain = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtTerrain.java"));
        String stats = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/RtFrameStats.java"));
        String bridge = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/client/CausticaDebugBridge.java"));

        for (String counter : new String[] {"terrainMaterialEpochRejects", "terrainOutstandingTasks",
                "terrainCancelledTasks", "terrainDiscardedBuilds", "terrainBuildLatencyNanos"}) {
            assertTrue(terrain.contains('"' + counter + '"'));
            assertTrue(stats.contains('"' + counter + '"'));
        }
        assertTrue(bridge.contains("terrainBuildLatencyKind"));
        assertTrue(bridge.contains("host-submit-to-timeline-completion"));
        assertTrue(bridge.contains("entityBlasGpuNanos"));
    }
}
