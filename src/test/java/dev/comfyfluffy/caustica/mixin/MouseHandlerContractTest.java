package dev.comfyfluffy.caustica.mixin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MouseHandlerContractTest {
    @Test
    void directMotionPathPreservesVanillaAccumulatorAndOtherCallbacks() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/mixin/MouseHandlerMixin.java"));

        assertTrue(source.contains("method = \"setup\""));
        assertTrue(source.contains("InputConstants;setupMouseCallbacks"));
        assertTrue(source.contains("caustica$directCursorCallback"));
        assertTrue(source.contains("onMove(window, xpos, ypos)"));
        assertTrue(source.contains("buttonCallback, scrollCallback, dropCallback"));
        assertTrue(!source.contains("minecraft.execute"));
        assertTrue(!source.contains("GLFW.glfwGetCursorPos"));
    }

    @Test
    void mouseHandlerMixinRemainsClientOnly() throws Exception {
        String mixins = Files.readString(Path.of("src/main/resources/caustica.mixins.json"));

        assertTrue(mixins.contains("\"client\""));
        assertTrue(mixins.contains("\"MouseHandlerMixin\""));
    }
}
