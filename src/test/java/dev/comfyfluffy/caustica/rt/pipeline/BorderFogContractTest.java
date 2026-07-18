package dev.comfyfluffy.caustica.rt.pipeline;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the finite-world boundary split between camera presentation and path transport. */
final class BorderFogContractTest {
    @Test
    void primaryMissesAndDistantPrimaryHitsUseFogWithoutChangingTransportMisses() throws Exception {
        String common = read("shaders/world/world_common.slang");
        String raygen = read("shaders/world/world.rgen.slang");
        String miss = read("shaders/world/world.rmiss.slang");
        String composite = read("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");

        assertTrue(common.contains("PAYLOAD_PRIMARY_RAY = 1u << 11u"));
        assertTrue(common.contains("public float4 borderFogColor"));
        assertTrue(common.contains("public float4 borderFogParams"));
        assertTrue(raygen.contains("primaryVisibilityRay = false;"));
        assertTrue(raygen.contains("smoothstep(fogStart, fogEnd, primaryHitDistance)"));
        assertTrue(miss.contains("if (primaryRay && !aboveHorizon)"));
        assertTrue(miss.contains("else if (earthAtmosphere && !aboveHorizon)"));
        assertTrue(composite.contains("EnvironmentAttributes.FOG_COLOR"));
        assertTrue(composite.contains("mc.options.getEffectiveRenderDistance()"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path)).replace("\r\n", "\n");
    }
}
