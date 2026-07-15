package dev.comfyfluffy.caustica.rt.material;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RtParallelAtlasTest {
    @Test
    void acceptsLargestRgbaAtlasThatFitsNativeImageByteBuffer() {
        assertTrue(RtParallelAtlas.supportsFullAtlas(32767, 16384));
    }

    @Test
    void rejectsTwoGibibyteAtlasBeforeNativeImageAllocation() {
        assertFalse(RtParallelAtlas.supportsFullAtlas(32768, 16384));
    }

    @Test
    void rejectsInvalidDimensions() {
        assertFalse(RtParallelAtlas.supportsFullAtlas(0, 16384));
        assertFalse(RtParallelAtlas.supportsFullAtlas(16384, -1));
    }
}
