package dev.comfyfluffy.caustica.rt.pipeline;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keeps first-frame output creation independent of client-tick world prewarming. */
final class RtOutputResourceOrderContractTest {
    @Test
    void blueNoiseExistsBeforeTheDisplayPipelineBindsIt() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java"));
        int ensureOutput = source.indexOf("private void ensureOutput(");
        int createBlueNoise = source.indexOf("blueNoiseSequence = RtBlueNoiseSequence.create(ctx);", ensureOutput);
        int bindBlueNoise = source.indexOf("blueNoiseSequence.buffer()", ensureOutput);

        assertTrue(ensureOutput >= 0);
        assertTrue(createBlueNoise > ensureOutput);
        assertTrue(bindBlueNoise > createBlueNoise);
    }
}
