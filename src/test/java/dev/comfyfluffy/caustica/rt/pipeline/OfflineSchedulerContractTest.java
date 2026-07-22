package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class OfflineSchedulerContractTest {
    @Test
    void schedulerAndIndirectRaygenShareTheInvocationLayout() throws Exception {
        String shader = Files.readString(Path.of("shaders/world/offline_schedule.comp.slang"));
        String raygen = Files.readString(Path.of("shaders/world/world.rgen.slang"));
        String pipeline = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtOfflineSchedulePipeline.java"));
        assertTrue(shader.contains("uint indirectWidth;"));
        assertTrue(shader.contains("work[slot].tileIndex"));
        assertTrue(shader.contains("InterlockedAdd(state[0].indirectWidth, 64u)"));
        assertTrue(raygen.contains("if (linearIndex == 0u)"));
        assertTrue(raygen.contains("(linearIndex - 1u) >> 6u"));
        assertTrue(pipeline.contains("VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT"));
        assertTrue(pipeline.contains("vkCmdFillBuffer"));
        assertTrue(pipeline.contains("offline_schedule.comp.spv"));
    }
}
