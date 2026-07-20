package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(config.contains("ADVANCED_OPTICAL_TRANSPORT"));
        assertTrue(config.contains("nrd.spherical-harmonics"));
        assertTrue(config.contains("nrd.relax-atrous-iterations"));
        assertTrue(menu.contains("addBundle(\"reconstruction.nrdMode\""));
        assertTrue(menu.contains("addBundle(\"reconstruction.nrdSpatial\""));
        assertTrue(menu.contains("caustica.options.rt.nrd.splitScreen"));
        assertTrue(policy.contains("isLinux() || vendor == 0x1002 || vendor == 0x8086"));
        assertTrue(policy.contains("Backend identity must not change halfway through a frame"));
        assertTrue(menu.contains("CausticaConfig.Rt.Reconstruction.ADVANCED_OPTICAL_TRANSPORT"));
    }

    @Test
    void nativeShaderAndCrossPlatformPackagingContractsRemainPinned() throws IOException {
        String cmake = source("native/nrd_bridge/CMakeLists.txt");
        String bridge = source("native/nrd_bridge/nrd_bridge.cpp");
        String backend = source("src/main/java/dev/comfyfluffy/caustica/mixin/VulkanBackendMixin.java");
        String runtime = source("src/main/java/dev/comfyfluffy/caustica/nrd/NrdRuntime.java");
        String library = source("src/main/java/dev/comfyfluffy/caustica/nrd/NrdLibrary.java");
        String extensionFilter = source("native/nrd_bridge/nrd_extension_filter.h");
        String shader = source("shaders/world/world.rgen.slang");
        String workflow = source(".github/workflows/production-candidate.yml");
        assertTrue(cmake.contains("792eff196afdd350fd9c3f862119017ccb438a0e"));
        assertTrue(cmake.contains("NRI_ENABLE_AMDAGS OFF"));
        assertTrue(bridge.contains("static_assert(sizeof(BridgeCommonSettings) == 308)"));
        assertTrue(bridge.contains("REBLUR_DIFFUSE_SPECULAR_SH"));
        assertTrue(bridge.contains("RELAX_DIFFUSE_SPECULAR_SH"));
        assertTrue(bridge.contains("IN_SPEC_RADIANCE_HITDIST"));
        assertTrue(backend.contains("VK_EXT_extended_dynamic_state"));
        assertTrue(backend.contains("VkPhysicalDeviceExtendedDynamicStateFeaturesEXT.EXTENDEDDYNAMICSTATE"));
        assertTrue(backend.contains("NrdRuntime.recordEnabledDeviceExtensions(augmented)"));
        assertTrue(runtime.contains("List.copyOf(extensions)"));
        assertTrue(library.contains("pointers.setAtIndex(ValueLayout.ADDRESS"));
        assertTrue(bridge.contains("deviceDesc.vkExtensions.deviceExtensions = nrdDeviceExtensions.data()"));
        assertTrue(bridge.contains("!nrdbridge::isRayTracingOnlyDeviceExtension(extension)"));
        assertTrue(extensionFilter.contains("VK_KHR_ray_tracing_pipeline"));
        assertTrue(extensionFilter.contains("VK_KHR_ray_tracing_maintenance1"));
        assertTrue(extensionFilter.contains("VK_EXT_opacity_micromap"));
        assertTrue(bridge.contains("deviceDesc.callbackInterface.AbortExecution = nriAbortExecution"));
        assertTrue(bridge.contains("let RecreateVK return its failure code"));
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
        assertFalse(build.contains("world_nrd_base_hq.rgen.spv"));
        assertTrue(build.contains("world_offline_base.rgen.spv"));
        assertTrue(bringup.contains("NONE(\"none\", null, \"world_base.rgen.spv\")"));
        assertTrue(raygen.contains("TraceRay(topLevelAS, RAY_FLAG_NONE"));
        assertTrue(raygen.contains("MISS_SHADOW"));
    }

    @Test
    void subNativeNrdUpscaleIsFeatureGatedAndUserControllable() throws IOException {
        String config = source("src/main/java/dev/comfyfluffy/caustica/CausticaConfig.java");
        String menu = source("src/main/java/dev/comfyfluffy/caustica/client/CausticaSettingsScreen.java");
        String composite = source("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");
        String upscale = source("shaders/display/nrd_spatial_upscale.comp");
        assertTrue(config.contains("nrd.upscale-filter\", \"edge-adaptive\""));
        assertTrue(config.contains("nrd.upscale-sharpness\", 0.0f, 0.0f, 1.0f"));
        assertTrue(menu.contains("List.of(\"edge-adaptive\", \"linear\", \"nearest\")"));
        assertTrue(menu.contains("caustica.options.rt.nrd.upscaleSharpness"));
        assertTrue(composite.contains("nrdResolvedOutput != rrOutput"));
        assertTrue(composite.contains("RtNrdSpatialUpscalePipeline.create(ctx)"));
        assertTrue(composite.contains("if (nrdSpatialUpscalePipeline != null)"));
        assertTrue(upscale.contains("vec4 cubicWeights(float t)"));
        assertTrue(upscale.contains("adaptiveSharpness"));
        assertTrue(upscale.contains("clamp(cubic, localMin, localMax)"));
    }

    @Test
    void skyBypassesUnwrittenNrdOutputAndInvalidRadianceCannotEscapeResolve() throws IOException {
        String nrd = source("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtNrd.java");
        String resolvePipeline = source("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtNrdResolvePipeline.java");
        String resolve = source("shaders/display/nrd_resolve.comp");
        String composite = source("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");
        String raygen = source("shaders/world/world.rgen.slang");
        assertTrue(nrd.contains("DENOISING_RANGE = 999_000.0f"));
        assertTrue(nrd.contains("putFloat(288, DENOISING_RANGE)"));
        assertTrue(resolvePipeline.contains("VkDescriptorSetLayoutBinding.calloc(11"));
        assertTrue(resolvePipeline.contains("RtNrd.DENOISING_RANGE"));
        assertTrue(resolvePipeline.contains("boolean forceRaw"));
        assertTrue(resolve.contains("layout(binding = 9, set = 0, r32f)"));
        assertTrue(resolve.contains("layout(binding = 10, set = 0, rgba16f)"));
        assertTrue(resolve.contains("if (pc.forceRaw != 0u)"));
        assertTrue(resolve.contains("if (!(viewZ < pc.denoisingRange))"));
        assertTrue(resolve.contains("? sanitizedNoisy(pixel) : clamp(resolved"));
        assertTrue(composite.contains("nrdResolvedOutput.view, gNrdViewZ.view, output.view"));
        assertTrue(composite.contains("CausticaConfig.Rt.Nrd.SPHERICAL_HARMONICS.value(), true"));
        assertTrue(raygen.contains("void setPrimaryMissGuides(float3 rayDirection)"));
        assertTrue(raygen.contains("gv_normal = normalize(-rayDirection)"));
        assertTrue(raygen.contains("gv_nrdPrimaryMiss = true"));
        assertTrue(raygen.contains("gv_nrdPrimaryMiss ? NRD_SKY_VIEWZ_SENTINEL : nrdViewZ"));
        assertTrue(raygen.contains("Match NRD's sanitize=true frontend contract"));

        // A radial infinity proxy is not a view-Z mask: at a grazing view it projects inside the range.
        float radialSkySentinel = 1_000_000.0f;
        float grazingViewZ = radialSkySentinel * 0.1f;
        assertTrue(grazingViewZ < 999_000.0f);
        assertTrue(3.0e38f > 999_000.0f);
    }

    @Test
    void relaxShUsesNrdBackendYCoCgContract() throws IOException {
        String resolve = source("shaders/display/nrd_resolve.comp");
        assertTrue(resolve.contains("Both REBLUR_SH and RELAX_SH expose NRD's backend SG/SH layout"));
        assertFalse(resolve.contains("RELAX SH0 retains linear RGB"));
        assertFalse(resolve.contains("else if (pc.family == 0u)"));

        // With no directional term, a pure-red YCoCg SH0 resolves to pure red scaled by 1/pi.
        float invPi = 0.3183098861837907f;
        float y0 = 0.25f;
        float y = y0 * invPi;
        float chromaScale = (y + 1.0e-6f) / (y0 + 1.0e-6f);
        float co = 0.5f * chromaScale;
        float cg = -0.25f * chromaScale;
        float t = y - cg;
        assertEquals(invPi, t + co, 2.0e-5f);
        assertEquals(0.0f, y + cg, 2.0e-5f);
        assertEquals(0.0f, t - co, 2.0e-5f);
    }

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of(relative));
    }
}
