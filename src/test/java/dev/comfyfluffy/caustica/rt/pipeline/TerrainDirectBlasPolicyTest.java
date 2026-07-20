package dev.comfyfluffy.caustica.rt.pipeline;

import dev.comfyfluffy.caustica.rt.accel.RtAccel;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerrainDirectBlasPolicyTest {
    @Test
    void directModeDisablesCompactionQueriesWhileCompactModeRetainsThem() {
        assertFalse(RtAccel.TerrainBlasPolicy.DIRECT.usesCompactionQueries());
        assertTrue(RtAccel.TerrainBlasPolicy.COMPACT.usesCompactionQueries());
    }

    @Test
    void terrainBuilderSelectsDirectModeAndQueryCommandsArePolicyGated() throws Exception {
        String builder = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtSectionBuilder.java"));
        String accel = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/accel/RtAccel.java"));
        assertTrue(builder.contains("RtAccel.TerrainBlasPolicy.DIRECT"));
        assertTrue(accel.contains("if (policy.usesCompactionQueries())"));
        assertTrue(accel.contains("if (b.terrainPolicy.usesCompactionQueries())"));
    }
}
