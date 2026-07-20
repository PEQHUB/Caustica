package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class TerrainGeometryValidationContractTest {
    @Test
    void malformedTerrainCannotReachVulkanBlasBuild() throws Exception {
        String builder = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtSectionBuilder.java"));

        assertTrue(builder.contains("bucketIndexCount != packed.indices().length"));
        assertTrue(builder.contains("index < 0 || index >= vertCount"));
    }
}
