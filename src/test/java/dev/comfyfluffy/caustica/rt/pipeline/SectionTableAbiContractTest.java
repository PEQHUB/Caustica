package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SectionTableAbiContractTest {
    @Test
    void cpuAndShaderKeepTheSectionTableAtFortyEightBytes() throws Exception {
        String table = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtSectionTable.java"));
        String shader = Files.readString(Path.of("shaders/world/world_common.slang"));

        assertTrue(table.contains("private static final int SECTION_ENTRY_BYTES = 48;"));
        assertTrue(table.contains("memPutFloat(base + 32, geom.publishedTime)"));
        assertTrue(table.contains("memSet(base + 36, 0, 12)"));
        assertTrue(shader.contains("public float publishedTime;"));
        assertTrue(shader.contains("public float reserved0;"));
        assertTrue(shader.contains("public float reserved1;"));
        assertTrue(shader.contains("public float reserved2;"));
        assertTrue(!shader.contains("public float3 reserved;"));
    }
}
