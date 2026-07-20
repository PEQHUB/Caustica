package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Prevents clear interfaces from consuming the indirect-light bounce budget and returning black. */
final class OpticalPathBudgetContractTest {
    @Test
    void glassAndWaterUseABoundedIndependentInterfaceBudget() throws Exception {
        String shader = Files.readString(Path.of("shaders/world/world.rgen.slang"));

        assertTrue(shader.contains("static const int MAX_OPTICAL_INTERFACE_DEPTH = 16;"));
        assertTrue(shader.contains("int scatteringDepth = 0;"));
        assertTrue(shader.contains("int opticalInterfaceDepth = 0;"));
        assertTrue(shader.contains("maxBounces + maxMirrorDepth + MAX_OPTICAL_INTERFACE_DEPTH + 1"));
        assertTrue(shader.contains("if (opticalInterfaceDepth >= MAX_OPTICAL_INTERFACE_DEPTH)"));
        assertTrue(shader.contains("++opticalInterfaceDepth;"));
        assertTrue(shader.contains("if (scatteringDepth >= maxBounces)"));
        assertTrue(shader.contains("++scatteringDepth;"));
        assertTrue(shader.contains("causticaTrySharcQuery(scatteringDepth"));
        assertFalse(shader.contains("if (primaryOnly || bounce >= maxBounces)"));
        assertFalse(shader.contains("if (bounce >= maxBounces && !(captureGuides && bounce == 0))"));
    }
}
