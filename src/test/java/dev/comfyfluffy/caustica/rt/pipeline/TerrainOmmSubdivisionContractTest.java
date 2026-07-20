package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class TerrainOmmSubdivisionContractTest {
    @Test
    void configuredSubdivisionIsNotSilentlyRefined() throws Exception {
        String omm = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtTerrainOmm.java"));

        assertTrue(omm.contains("return Math.min(ommSubdivision(), max);"));
        assertTrue(!omm.contains("ommSubdivision() + 1"));
    }
}
