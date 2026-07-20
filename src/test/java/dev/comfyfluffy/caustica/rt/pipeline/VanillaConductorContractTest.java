package dev.comfyfluffy.caustica.rt.pipeline;

import dev.comfyfluffy.caustica.rt.material.RtLabPbr;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class VanillaConductorContractTest {
    @Test
    void semanticConductorsReuseLabPbrMeasuredF0() throws Exception {
        String shader = Files.readString(Path.of("shaders/world/world.rchit.slang"));
        assertMetal(shader, 230, "0.5312288", "0.5123572", "0.4958285");
        assertMetal(shader, 231, "0.9442300", "0.7761021", "0.3734020");
        assertMetal(shader, 234, "0.9259522", "0.7209016", "0.5041542");
        assertTrue(shader.contains("return lerp(float3(0.04), conductor, clamp(metalness"));
    }

    private static void assertMetal(String shader, int labPbrId,
                                    String expectedR, String expectedG, String expectedB) {
        RtLabPbr.Specular spec = RtLabPbr.decodeSpec(0.0f, labPbrId / 255.0f, 0.0f, 1.0f,
                0.1f, 0.2f, 0.3f);
        assertTrue(Math.abs(spec.f0r() - Float.parseFloat(expectedR)) < 2.0e-6f);
        assertTrue(Math.abs(spec.f0g() - Float.parseFloat(expectedG)) < 2.0e-6f);
        assertTrue(Math.abs(spec.f0b() - Float.parseFloat(expectedB)) < 2.0e-6f);
        assertTrue(shader.contains("float3(" + expectedR + ", " + expectedG + ", " + expectedB + ")"));
    }
}
