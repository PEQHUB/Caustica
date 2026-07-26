package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DlssgInputOwnershipContractTest {
    private static final Path ROOT = Path.of("src/main/java/dev/comfyfluffy/caustica/rt");

    @Test
    void oldRingAndBlockingFrameOwnershipAreGone() throws Exception {
        String fg = Files.readString(ROOT.resolve("pipeline/RtDlssFg.java"));
        assertFalse(fg.contains("DlssgInputSlotRing"));
        assertFalse(fg.contains("private final DlssgInputSlotRing"));
        assertFalse(fg.contains("waitForInputSlot"));
        assertFalse(fg.contains("vkWaitTimeline"));
        assertFalse(Files.exists(ROOT.resolve("pipeline/DlssgInputSlotRing.java")));
    }

    @Test
    void poolExposesAllOwnershipStates() throws Exception {
        String pool = Files.readString(ROOT.resolve("pipeline/DlssgInputPool.java"));
        assertTrue(pool.contains("CAPTURING"));
        assertTrue(pool.contains("RETIREMENT_PENDING"));
        assertTrue(pool.contains("QUARANTINED"));
        assertTrue(pool.contains("releaseAllAfterDeviceIdle"));
    }
}
