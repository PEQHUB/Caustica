package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReflexIndependentOfFrameGenerationContractTest {
    @Test
    void reflexConfigurationAndRuntimeDoNotDependOnFrameGeneration() throws IOException {
        String fg = source("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtDlssFg.java");
        String screen = source("src/main/java/dev/comfyfluffy/caustica/client/CausticaSettingsScreen.java");

        assertTrue(fg.contains("requested() || CausticaConfig.Rt.Reflex.ENABLED.value()"),
                "Reflex On must remain effective when DLSS-G is not requested");
        assertTrue(fg.contains("applyReflexOptions(library, arena);"));
        assertTrue(fg.contains("if (reflexSupported) {") && fg.contains("library.reflexSleep(frameToken)"),
                "Reflex sleep must run from the unconditional frame-token path");

        int reflexControl = screen.indexOf(
                "controls.add(new Dropdown<>(180, Component.translatable(\"caustica.options.rt.fg.reflex\")");
        int nextControl = screen.indexOf("caustica.options.rt.fg.queueParallelism", reflexControl);
        String reflexBlock = screen.substring(reflexControl, nextControl);
        assertFalse(reflexBlock.contains("activeWhen"),
                "The Reflex control must stay interactive while DLSS-G is Off");
    }

    private static String source(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
