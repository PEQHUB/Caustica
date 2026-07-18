package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EntityDirectCaptureContractTest {
    @Test
    void directCaptureUsesValidatedTopologyInsteadOfDevelopmentClassNames() throws Exception {
        String emitter = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/entity/RtCuboidEmitter.java"));
        String collector = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/entity/RtEntityCollector.java"));

        assertFalse(emitter.contains("getName().startsWith(\"net.minecraft.\")"),
                "Production remapping must not disable direct capture");
        assertTrue(emitter.contains("cube.getClass() != ModelPart.Cube.class"),
                "Every direct cube must retain exact-type validation");
        assertTrue(emitter.contains("template.matches(model.root())"),
                "Cached templates must be checked against the complete live tree");
        assertTrue(collector.contains("model.renderToBuffer(poseStack, capture"),
                "Unsupported topology must retain the visual-equivalent fallback");
    }
}
