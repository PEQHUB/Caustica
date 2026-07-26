package dev.comfyfluffy.caustica.rt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RtCompositeDlssgOwnershipContractTest {
    private static final Path SOURCE = Path.of("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");

    @Test
    void captureAndSubmitUseOneExplicitPoolIndex() throws Exception {
        String source = Files.readString(SOURCE);
        assertFalse(source.contains("activeInputSlot"));
        assertTrue(source.contains("int slotIndex = RtDlssFg.INSTANCE.beginFrameInputCapture()"));
        assertTrue(source.contains("fgInputSlot(ctx, slotIndex)"));
        assertTrue(source.contains("submitFrame(slotIndex"));
        assertFalse(source.contains("new RtImage[]"));
    }
}
