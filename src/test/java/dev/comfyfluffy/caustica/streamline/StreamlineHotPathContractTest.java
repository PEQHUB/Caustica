package dev.comfyfluffy.caustica.streamline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StreamlineHotPathContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }

    @Test
    void bridgeUsesTypedFixedArityInvocationOnly() throws Exception {
        String library = source("src/main/java/dev/comfyfluffy/caustica/streamline/StreamlineLibrary.java");
        assertFalse(library.contains("Object..."));
        assertFalse(library.contains("invokeWithArguments"));
        assertTrue(library.contains("invokeExact"));
    }

    @Test
    void frameGenerationUsesPersistentScratchAndNoSteadyStateConfinedArena() throws Exception {
        String fg = source("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtDlssFg.java");
        assertFalse(fg.contains("Arena.ofConfined"));
        int liveTransition = fg.indexOf("private boolean retireInputsForLiveTransition");
        int teardown = fg.indexOf("private void drainInputSlots", liveTransition);
        assertTrue(liveTransition >= 0 && teardown > liveTransition);
        assertFalse(fg.substring(liveTransition, teardown).contains("waitForInputSlot"));
    }

    @Test
    void reportsAreQueuedOffTheRenderThread() throws Exception {
        String report = source("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/StreamlineAcceptanceReport.java");
        assertTrue(report.contains("REPORT_EXECUTOR.execute"));
        assertTrue(report.contains("writeQueued"));
    }
}
