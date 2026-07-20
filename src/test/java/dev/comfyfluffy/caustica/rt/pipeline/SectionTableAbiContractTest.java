package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SectionTableAbiContractTest {
    @Test
    void cpuAndShaderKeepTheAtomicSectionTableAtThirtyTwoBytes() throws Exception {
        String table = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtSectionTable.java"));
        String shader = Files.readString(Path.of("shaders/world/world_common.slang"));

        assertTrue(table.contains("private static final int SECTION_ENTRY_BYTES = 32;"));
        assertTrue(!table.contains("publishedTime"));
        assertTrue(!shader.contains("publishedTime"));
    }
}
