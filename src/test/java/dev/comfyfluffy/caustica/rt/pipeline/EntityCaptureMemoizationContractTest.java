package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EntityCaptureMemoizationContractTest {
    @Test
    void repeatedAuthoredInputsUseBitExactCachesAndResetPerCapture() throws Exception {
        String capture = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/entity/RtEntityCapture.java"));

        assertTrue(capture.contains("Float.floatToRawIntBits(nx)"));
        assertTrue(capture.contains("nxBits == cachedNxBits && nyBits == cachedNyBits && nzBits == cachedNzBits"));
        assertTrue(capture.contains("colorCacheValid && cachedColor == color"));
        assertTrue(capture.contains("normalCacheValid = false;"));
        assertTrue(capture.contains("colorCacheValid = false;"));
        assertTrue(capture.contains("if (len <= 1.0e-6f)"),
                "Position-derived geometric normals must retain their uncached path");
    }
}
