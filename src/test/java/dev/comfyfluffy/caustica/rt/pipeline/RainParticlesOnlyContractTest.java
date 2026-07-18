package dev.comfyfluffy.caustica.rt.pipeline;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RainParticlesOnlyContractTest {
    @Test
    void rainDoesNotAttenuateRtCelestialsOrTransportedSkyLight() throws Exception {
        String composite = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java"));

        assertTrue(composite.contains("float rainBrightness = 1.0f;"));
        assertFalse(composite.contains("mc.level.getRainLevel(partial)"));
    }
}
