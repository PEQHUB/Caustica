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

        assertTrue(accel.contains("tlas.label + \" build\""));
        assertTrue(!accel.contains("tlas.update"));
        assertTrue(!accel.contains("updatesSinceBuild"));
    }
}
