package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class NrdIntegrationContractTest {
    @Test
    void globalPolicyAndLiveMenuRemainConnected() throws IOException {
        String config = source("src/main/java/dev/comfyfluffy/caustica/CausticaConfig.java");
        String menu = source("src/main/java/dev/comfyfluffy/caustica/client/CausticaSettingsScreen.java");
        String policy = source("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtReconstruction.java");
        assertTrue(config.contains("reconstruction.backend\", \"auto\""));
        assertTrue(config.contains("nrd.spherical-harmonics"));
        assertTrue(config.contains("nrd.relax-atrous-iterations"));
        assertTrue(menu.contains("addBundle(\"NRD mode & upscale\""));
        assertTrue(menu.contains("caustica.options.rt.nrd.splitScreen"));
        assertTrue(policy.contains("isLinux() || vendor == 0x1002 || vendor == 0x8086"));
        assertTrue(policy.contains("Backend identity must not change halfway through a frame"));
    }

    @Test
    void nativeShaderAndCrossPlatformPackagingContractsRemainPinned() throws IOException {
        String cmake = source("native/nrd_bridge/CMakeLists.txt");
        String bridge = source("native/nrd_bridge/nrd_bridge.cpp");
        String shader = source("shaders/world/world.rgen.slang");
        String workflow = source(".github/workflows/production-candidate.yml");
        assertTrue(cmake.contains("792eff196afdd350fd9c3f862119017ccb438a0e"));
        assertTrue(cmake.contains("NRI_ENABLE_AMDAGS OFF"));
        assertTrue(bridge.contains("static_assert(sizeof(BridgeCommonSettings) == 308)"));
        assertTrue(bridge.contains("REBLUR_DIFFUSE_SPECULAR_SH"));
        assertTrue(bridge.contains("RELAX_DIFFUSE_SPECULAR_SH"));
        assertTrue(bridge.contains("IN_SPEC_RADIANCE_HITDIST"));
        assertTrue(shader.contains("FRAME_FLAG_NRD_SH"));
        assertTrue(shader.contains("gNrdViewZ"));
        assertTrue(shader.contains("diffuseSignal = nrdDiffuseRadiance / diffuseDemodulation"));
        assertTrue(shader.contains("specularSignal = nrdSpecularRadiance / specularDemodulation"));
        assertTrue(workflow.contains("name: nrd-linux-x64"));
        assertTrue(workflow.contains("NRD_LINUX_X64"));
    }

    @Test
    void amdBaselineDoesNotRequireShaderInvocationReorder() throws IOException {
        String build = source("build.gradle");
        String bringup = source("src/main/java/dev/comfyfluffy/caustica/rt/RtDeviceBringup.java");
        String raygen = source("shaders/world/world.rgen.slang");
        assertTrue(build.contains("world_base.rgen.spv"));
        assertTrue(build.contains("world_nrd_base.rgen.spv"));
        assertTrue(build.contains("world_offline_base.rgen.spv"));
        assertTrue(bringup.contains("NONE(\"none\", null, \"world_base.rgen.spv\")"));
        assertTrue(raygen.contains("TraceRay(topLevelAS, RAY_FLAG_NONE"));
        assertTrue(raygen.contains("MISS_SHADOW"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of(relative));
    }
}
