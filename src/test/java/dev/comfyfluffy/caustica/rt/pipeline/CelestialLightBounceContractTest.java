package dev.comfyfluffy.caustica.rt.pipeline;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CelestialLightBounceContractTest {
    @Test
    void gatesCelestialNeeByScatteringDepthWithoutGatingCelestialVisibility() throws IOException {
        String raygen = Files.readString(Path.of("shaders/world/world.rgen.slang"));
        String miss = Files.readString(Path.of("shaders/world/world.rmiss.slang"));

        assertTrue(raygen.contains("scatteringDepth <= int(pc.celestialMaxBounces)"));
        assertTrue(raygen.contains("showCelestial = true"));
        assertTrue(miss.contains("if (showCelestial && earthAtmosphere)"));
    }
}
