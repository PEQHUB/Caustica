package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class TerrainBlasPublicationContractTest {
    @Test
    void completedTerrainBuildPublishesWithoutUnsafeCompactionPhase() throws Exception {
        String terrain = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtTerrain.java"))
                .replace("\r\n", "\n");

        assertTrue(terrain.contains("prepared.releaseBuildInputs();\n"
                + "                    completeTask(task, prepared, build, null);"));
        assertTrue(!terrain.contains("submitTerrainCompaction(ctx, task, prepared, build);"));
    }
}
