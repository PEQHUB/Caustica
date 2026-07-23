package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.comfyfluffy.caustica.rt.RtDeviceBringup;
import dev.comfyfluffy.caustica.rt.SharcRadianceEncoding;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SharcIntegrationContractTest {
    @Test
    void offPathRetainsTheOriginalPipelineAndAbi() throws Exception {
        String composite = read("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");
        String common = read("shaders/world/world_common.slang");
        assertTrue(composite.contains("private RtPipeline worldPipeline;"));
        assertTrue(composite.contains("if (sharcCache != null && !offlineGroundTruth)"));
        assertTrue(composite.contains("active.trace(cmd, renderW, renderH, pushAddr)"));
        assertTrue(common.contains("#define CAUSTICA_SHARC 0"));
        assertTrue(common.contains("public float3 geometricNormal;"));
    }

    @Test
    void runtimeRecordsSparseResolveQueryWithExactBarriers() throws Exception {
        String composite = read("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");
        String cache = read("src/main/java/dev/comfyfluffy/caustica/rt/RtSharcCache.java");
        String raygen = read("shaders/world/world.rgen.slang");
        assertTrue(composite.indexOf("updatePipeline.trace") < composite.indexOf("sharcResolvePipeline.dispatch"));
        assertTrue(composite.indexOf("sharcResolvePipeline.dispatch") < composite.indexOf("queryPipeline.trace"));
        assertTrue(composite.contains("? sharcQueryPipeline : sharcDiffuseQueryPipeline"));
        assertTrue(raygen.contains("#if CAUSTICA_SHARC_GLOSSY"));
        assertTrue(raygen.contains("#if CAUSTICA_SHARC_LIVE_SECONDARY_DIRECT"));
        String build = read("build.gradle");
        assertTrue(build.contains("\"-DCAUSTICA_SHARC_LIVE_SECONDARY_DIRECT=1\""));
        assertTrue(build.contains("\"-O2\", \"-fp-mode\", \"precise\""));
        assertTrue(composite.contains("!CausticaConfig.Rt.Sharc.LIVE_SECONDARY_DIRECT.value()"));
        assertTrue(composite.contains("(renderW + updateTileSize - 1) / updateTileSize"));
        assertTrue(raygen.contains("tile * tileSize"));
        assertTrue(raygen.contains("pc.maxBounces = min(pc.maxBounces, sharcFrame.updateMaxBounces)"));
        assertTrue(raygen.contains("sharcPreviousLobeDiffuse"));
        assertTrue(raygen.contains("payload.hitT >= minSegment"));
        assertTrue(raygen.contains("glossyCone >= minSegment"));
        assertTrue(raygen.contains("causticaSharcDiscardCurrentVertex(sharcState)"));
        assertTrue(cache.contains("VK_BUFFER_USAGE_TRANSFER_DST_BIT"));
        assertTrue(cache.contains("VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR"));
        assertTrue(cache.contains("VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT"));
        String frameStats = read("src/main/java/dev/comfyfluffy/caustica/rt/RtFrameStats.java");
        assertTrue(frameStats.contains("sharcNumericRisks"));
        assertTrue(frameStats.contains("sharcResolvedSaturations"));
        assertFalse(cache.contains("lockAddress"));
    }

    @Test
    void debugBridgeControlsAndReportsLiveSharcState() throws Exception {
        String client = read("src/main/java/dev/comfyfluffy/caustica/client/CausticaClient.java");
        String bridge = read("src/main/java/dev/comfyfluffy/caustica/client/CausticaDebugBridge.java");
        String script = read("tools/caustica-debug-bridge.ps1");
        assertTrue(client.contains("CausticaDebugBridge.tick(client)"));
        assertTrue(bridge.contains("Boolean.getBoolean(ENABLE_PROPERTY)"));
        assertTrue(bridge.contains("if (!enabled()) return;"));
        assertTrue(bridge.contains("setBoolean(command, \"sharc\""));
        assertTrue(bridge.contains("state.setProperty(\"fps\""));
        assertTrue(bridge.contains("state.setProperty(\"sharcActive\""));
        assertTrue(bridge.contains("RtComposite.INSTANCE.sharcStatus()"));
        String composite = read("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");
        assertTrue(composite.contains("Inactive - NRD reconstruction selected"));
        assertTrue(composite.contains("Inactive - offline ground truth uses unbiased baseline"));
        assertTrue(script.contains("[ValidateSet('get','set','wait','benchmark','reset','shutdown')]"));
        assertTrue(bridge.contains("state.setProperty(\"backend\""));
        assertTrue(bridge.contains("state.setProperty(\"artifactSha256\""));
        assertTrue(bridge.contains("state.setProperty(\"renderWidth\""));
        assertTrue(bridge.contains("sequence < PROCESS_START_MILLIS"));
        assertTrue(bridge.contains("client.getWindow().toggleFullScreen()"));
        assertTrue(bridge.contains("command.getProperty(\"causticaCategory\")"));
        assertTrue(bridge.contains("new RtSharcOptionsScreen(client.gui.screen(), client.options)"));
        assertTrue(bridge.contains("Screenshot.grab(client, false)"));
        assertTrue(script.contains("[bool]$OpenCausticaSettings"));
        assertTrue(script.contains("[string]$CausticaCategory"));
        assertTrue(script.contains("[bool]$OpenSharcSettings"));
        assertTrue(script.contains("[bool]$CloseScreen"));
        assertTrue(bridge.contains("command.getProperty(\"closeScreen\""));
        assertTrue(script.contains("[bool]$Screenshot"));
    }

    @Test
    void rootSettingsUseResponsiveCategoryWorkstation() throws Exception {
        String entry = read("src/main/java/dev/comfyfluffy/caustica/client/CausticaOptionsScreen.java");
        String screen = read("src/main/java/dev/comfyfluffy/caustica/client/CausticaSettingsScreen.java");
        String categoryLayout = read("src/main/java/dev/comfyfluffy/caustica/client/ui/CategoryLayout.java");
        String gridLayout = read("src/main/java/dev/comfyfluffy/caustica/client/ui/WidgetGridLayout.java");
        assertTrue(entry.contains("extends CausticaSettingsScreen"));
        assertTrue(screen.contains("SHARC(CategoryGroup.ADVANCED, \"sharc\")"));
        assertTrue(screen.contains("new ScrollableLayout"));
        assertTrue(screen.contains("Math.clamp((contentWidth + GRID_GAP)"));
        assertTrue(screen.contains("restoreSharcParityDefaults"));
        assertFalse(screen.contains("new RtSharcOptionsScreen(this, options)"));
        assertTrue(categoryLayout.contains("Deterministic vertical layout"));
        assertTrue(gridLayout.contains("compact responsive grid"));
    }

    @Test
    void biasedCacheIsDisabledForOfflineAndHasRealGpuTimestamps() throws Exception {
        String composite = read("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");
        String profiler = read("src/main/java/dev/comfyfluffy/caustica/rt/RtTraceGpuProfiler.java");
        assertTrue(composite.contains("syncSharcResources(ctx, !offlineGroundTruth)"));
        assertTrue(profiler.contains("vkCmdWriteTimestamp"));
        assertTrue(read("src/main/java/dev/comfyfluffy/caustica/rt/RtFrameStats.java")
                .contains("\"disocclusionGpuNanos\", \"dlssRrGpuNanos\""));
        assertTrue(profiler.contains("VK_QUERY_RESULT_64_BIT"));
        assertFalse(profiler.contains("VK_QUERY_RESULT_WAIT_BIT"));
    }

    @Test
    void causticaMenuExposesStatusMemoryAndDebugViews() throws Exception {
        String options = read("src/main/java/dev/comfyfluffy/caustica/client/RtVideoOptions.java");
        String screen = read("src/main/java/dev/comfyfluffy/caustica/client/RtSharcOptionsScreen.java");
        String widgets = read("src/main/java/dev/comfyfluffy/caustica/client/ui/CausticaWidgets.java");
        String config = read("src/main/java/dev/comfyfluffy/caustica/CausticaConfig.java");
        String lang = read("src/main/resources/assets/caustica/lang/en_us.json");
        assertTrue(options.contains("public static OptionInstance<?>[] sharcOptions()"));
        assertTrue(options.contains("sharcSceneScale()"));
        assertTrue(options.contains("sharcRadianceScale()"));
        assertTrue(options.contains("sharcAccumulationFrames()"));
        assertTrue(options.contains("sharcStaleFrames()"));
        assertTrue(options.contains("RtComposite.INSTANCE.sharcStatus()"));
        assertTrue(options.contains("DEBUG_VIEW_TONEMAP_COMPARISON, 9, 10, 11, 12, 13, 14, 15, 16"));
        assertTrue(options.contains("sharcUpdateTileSize()"));
        assertTrue(options.contains("sharcMinSegmentRatio()"));
        assertTrue(lang.contains("SHaRC Query Hit / Miss"));
        assertTrue(lang.contains("SHaRC Termination Depth"));
        assertTrue(lang.contains("SHaRC Occupancy"));
        assertTrue(lang.contains("SHaRC Voxel Hash"));
        assertTrue(lang.contains("SHaRC Cached Radiance"));
        assertTrue(lang.contains("SHaRC Query Eligibility"));
        assertTrue(screen.contains("extends Screen"));
        assertTrue(screen.contains("restoreParityDefaults"));
        assertTrue(screen.contains("CausticaConfig.Rt.Sharc.ENABLED.set(true)"));
        assertTrue(widgets.contains("public static final int PANEL = 0x28000000"));
        assertTrue(widgets.contains("public static final int PANEL_2 = 0x44000000"));
        assertTrue(config.contains("\"sharc.enabled\", true"));
        assertTrue(config.contains("\"sharc.cache-exponent\", 24, 16, 28"));
        assertTrue(screen.contains("27, 28)"));
        assertTrue(lang.contains("RTX 5090 Maximum (10 GiB)"));
        assertTrue(config.contains("\"sharc.scene-scale\", 32.0f, 1.0f, 100.0f"));
        assertTrue(config.contains("\"sharc.accumulation-frames\", 128, 1, 1024"));
        assertTrue(config.contains("\"sharc.stale-frames\", 1024, 8, 1024"));
        assertTrue(config.contains("\"sharc.update-tile-size\", 2, 2, 64"));
        assertTrue(config.contains("\"sharc.update-max-bounces\", 8, 1, 8"));
        assertTrue(config.contains("\"sharc.min-segment-ratio\", 0.2f, 0.25f, 4.0f"));
        assertTrue(config.contains("\"sharc.glossy-query\", false"));
        assertTrue(config.contains("\"sharc.live-secondary-direct\", true"));
    }

    @Test
    void viewDependentDirectLightingIsNotStoredInTheDirectionlessCache() throws Exception {
        String raygen = read("shaders/world/world.rgen.slang");
        String bridge = read("shaders/sharc/sharc_bridge.slang");
        assertTrue(raygen.contains("float3 cacheableDirectLighting"));
        assertTrue(raygen.contains("float3 liveDirectLighting"));
        assertTrue(raygen.contains("cacheableDirectLighting, cacheableDirectLighting + liveDirectLighting"));
        assertTrue(raygen.contains("cachedRadiance + liveDirectLighting"));
        assertTrue(bridge.contains("cacheableDirectLighting / materialDemodulation"));
        assertFalse(bridge.contains("propagatedDirectLighting / materialDemodulation"));
    }

    @Test
    void rawPrimaryCacheDebugIsAnIndependentLiveAbcVariant() throws Exception {
        String raygen = read("shaders/world/world.rgen.slang");
        String build = read("build.gradle");
        String composite = read("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");
        String config = read("src/main/java/dev/comfyfluffy/caustica/CausticaConfig.java");
        String bridge = read("src/main/java/dev/comfyfluffy/caustica/client/CausticaDebugBridge.java");

        assertTrue(config.contains("\"sharc.primary-diffuse-reuse\", false"));
        assertTrue(build.contains("-DCAUSTICA_SHARC_PRIMARY_DIFFUSE=1"));
        assertTrue(build.contains("world_sharc_primary.rgen.spv"));
        assertTrue(raygen.contains("bool primaryEligible = bounce == 0 && primaryDiffuseEligible"));
        assertTrue(raygen.contains("metal <= 0.1 && perceptualRough >= 0.5"));
        assertTrue(raygen.contains("cachedRadiance + liveDirectLighting"));
        assertTrue(composite.contains("syncSharcPrimaryQueryPipeline"));
        assertTrue(composite.contains("sharcPrimaryQueryPipeline != null"));
        assertTrue(composite.contains("RtReconstruction.requestHistoryReset()"));
        assertTrue(bridge.contains("setBoolean(command, \"primaryDiffuseReuse\""));
        assertTrue(bridge.contains("state.setProperty(\"sharcPrimaryDiffuseActive\""));
        String lang = read("src/main/resources/assets/caustica/lang/en_us.json");
        assertTrue(lang.contains("Raw Primary Cache Debug"));
        assertTrue(lang.contains("no spatial interpolation or confidence filter"));
    }

    @Test
    void sdkSourceStaysExternalAndArtifactAvailabilityIsExplicit() throws Exception {
        String build = read("build.gradle");
        String support = read("src/main/java/dev/comfyfluffy/caustica/rt/RtSharcSupport.java");
        assertTrue(build.contains("SHARC_SDK"));
        assertTrue(build.contains("sharcHeaderHashes"));
        assertTrue(build.contains("NVIDIA-SHARC-SDK.txt"));
        assertTrue(support.contains("artifacts"));
        assertTrue(support.contains("shaderBufferInt64Atomics"));
        String helper = read("tools/build-fast.ps1");
        assertTrue(helper.contains("[switch]$WithoutSharc"));
        assertTrue(helper.contains("$artifactMode = $Mode -in @('Jar', 'Deploy', 'Full')"));
        assertTrue(helper.contains("$includeSharc = $WithSharc -or ($artifactMode -and -not $WithoutSharc)"));
        assertTrue(helper.contains("-PwithoutSharc=true"));
        assertTrue(build.contains("Production JARs include SHaRC by default"));
        assertTrue(build.contains("explicitOptOut=${sharcOptOut}"));
    }

    @Test
    void radianceEncodingHasCorrectStridesAndSizing() throws Exception {
        assertEquals(16, SharcRadianceEncoding.RGB.accumulationStride());
        assertEquals(16, SharcRadianceEncoding.RGB.resolvedStride());
        assertEquals("", SharcRadianceEncoding.RGB.shaderSuffix());
        assertEquals(32, SharcRadianceEncoding.DIRECTIONAL_SH.accumulationStride());
        assertEquals(24, SharcRadianceEncoding.DIRECTIONAL_SH.resolvedStride());
        assertEquals("_sh", SharcRadianceEncoding.DIRECTIONAL_SH.shaderSuffix());
        // 8 (hash) + 16 + 16 = 40 bytes/entry for RGB
        assertEquals(40L, SharcRadianceEncoding.RGB.cacheBytes(1L));
        assertEquals(40L * (1L << 24), SharcRadianceEncoding.RGB.cacheBytes(1L << 24));
        // 8 (hash) + 32 + 24 = 64 bytes/entry for SH
        assertEquals(64L, SharcRadianceEncoding.DIRECTIONAL_SH.cacheBytes(1L));
        assertEquals(64L * (1L << 24), SharcRadianceEncoding.DIRECTIONAL_SH.cacheBytes(1L << 24));
    }

    @Test
    void radianceEncodingIsWiredThroughConfigCacheAndPipeline() throws Exception {
        String config = read("src/main/java/dev/comfyfluffy/caustica/CausticaConfig.java");
        String cache = read("src/main/java/dev/comfyfluffy/caustica/rt/RtSharcCache.java");
        String resolve = read("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtSharcResolvePipeline.java");
        String bringup = read("src/main/java/dev/comfyfluffy/caustica/rt/RtDeviceBringup.java");
        String composite = read("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");
        // Config exposes the encoding setting
        assertTrue(config.contains("\"sharc.radiance-encoding\", 0, 0, 1"));
        // Cache accepts and stores encoding
        assertTrue(cache.contains("SharcRadianceEncoding encoding"));
        assertTrue(cache.contains("encoding.accumulationStride()"));
        assertTrue(cache.contains("encoding.resolvedStride()"));
        assertTrue(cache.contains("public SharcRadianceEncoding encoding()"));
        // Resolve pipeline loads encoding-specific shader
        assertTrue(resolve.contains("SHADER_RGB"));
        assertTrue(resolve.contains("SHADER_SH"));
        assertTrue(resolve.contains("encoding == SharcRadianceEncoding.DIRECTIONAL_SH"));
        // DeviceBringup has encoding-aware overloads
        assertTrue(bringup.contains("sharcQueryRaygenShader(SharcRadianceEncoding encoding)"));
        assertTrue(bringup.contains("withEncoding(String shader, SharcRadianceEncoding encoding)"));
        // Composite uses encoding for pipeline selection
        assertTrue(composite.contains("requestedSharcEncoding"));
        assertTrue(composite.contains("activeSharcEncoding"));
        assertTrue(composite.contains("requestSharcEncoding(SharcRadianceEncoding encoding)"));
    }

    @Test
    void buildGradleCompilesShEncodingShaderVariants() throws Exception {
        String build = read("build.gradle");
        // SH encoding flag is passed to the compiler
        assertTrue(build.contains("-DSHARC_ENABLE_SH_ENCODING=1"));
        // Core SH raygen variants exist
        assertTrue(build.contains("world_sharc_sh.rgen.spv"));
        assertTrue(build.contains("world_sharc_sh_nv.rgen.spv"));
        assertTrue(build.contains("world_sharc_sh_base.rgen.spv"));
        // Diffuse SH variants
        assertTrue(build.contains("world_sharc_diffuse_sh.rgen.spv"));
        assertTrue(build.contains("world_sharc_diffuse_sh_nv.rgen.spv"));
        // Update SH variants
        assertTrue(build.contains("world_sharc_update_sh.rgen.spv"));
        assertTrue(build.contains("world_sharc_update_sh_nv.rgen.spv"));
        // Resolve SH compute
        assertTrue(build.contains("sharc_resolve_sh.comp.spv"));
        // Shared helper shaders with SH encoding
        assertTrue(build.contains("world_sharc_sh.rchit.spv"));
        assertTrue(build.contains("world_sharc_sh.rahit.spv"));
        assertTrue(build.contains("world_sharc_sh.rmiss.spv"));
        assertTrue(build.contains("world_sharc_sh_guide.rmiss.spv"));
        // Complexity budget checks for SH variants
        assertTrue(build.contains("SHaRC SH query raygen complexity budget exceeded"));
        assertTrue(build.contains("SHaRC SH diffuse-query raygen complexity budget exceeded"));
    }

    @Test
    void resolvePipelineLoadsCorrectShaderPerEncoding() throws Exception {
        String resolve = read("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtSharcResolvePipeline.java");
        assertTrue(resolve.contains("private static final String SHADER_RGB = \"/caustica/rt/sharc_resolve.comp.spv\""));
        assertTrue(resolve.contains("private static final String SHADER_SH = \"/caustica/rt/sharc_resolve_sh.comp.spv\""));
        assertTrue(resolve.contains("encoding == SharcRadianceEncoding.DIRECTIONAL_SH ? SHADER_SH : SHADER_RGB"));
        assertTrue(resolve.contains("loadModule(ctx, stack, shaderResource)"));
    }

    @Test
    void screenExposesEncodingSelectorWithResetWarning() throws Exception {
        String screen = read("src/main/java/dev/comfyfluffy/caustica/client/RtSharcOptionsScreen.java");
        assertTrue(screen.contains("caustica.options.rt.sharcEncoding"));
        assertTrue(screen.contains("SharcRadianceEncoding"));
        assertTrue(screen.contains("requestSharcEncoding(value)"));
        assertTrue(screen.contains("requestSharcEncoding(SharcRadianceEncoding.RGB)"));
        assertTrue(screen.contains("requestSharcReset(\"radiance encoding changed\")"));
        assertTrue(screen.contains("RADIANCE_ENCODING.set(0)"));
    }

    @Test
    void directionalSharcRaygenNamesMatchPackagedBuildOutputs() throws Exception {
        List<String> stems = List.of(
                "world_sharc",
                "world_sharc_diffuse",
                "world_sharc_primary",
                "world_sharc_update",
                "world_sharc_diagnostic",
                "world_sharc_primary_diagnostic",
                "world_sharc_update_diagnostic"
        );
        for (String stem : stems) {
            assertEquals(stem + "_sh.rgen.spv",
                    RtDeviceBringup.withEncoding(stem + ".rgen.spv", SharcRadianceEncoding.DIRECTIONAL_SH));
            assertEquals(stem + "_sh_nv.rgen.spv",
                    RtDeviceBringup.withEncoding(stem + "_nv.rgen.spv", SharcRadianceEncoding.DIRECTIONAL_SH));
            assertEquals(stem + "_sh_base.rgen.spv",
                    RtDeviceBringup.withEncoding(stem + "_base.rgen.spv", SharcRadianceEncoding.DIRECTIONAL_SH));
        }
        // RGB encoding returns input unchanged
        assertEquals("world_sharc.rgen.spv",
                RtDeviceBringup.withEncoding("world_sharc.rgen.spv", SharcRadianceEncoding.RGB));
        assertEquals("world_sharc_nv.rgen.spv",
                RtDeviceBringup.withEncoding("world_sharc_nv.rgen.spv", SharcRadianceEncoding.RGB));
    }

    @Test
    void compositeAppliesPersistedEncodingOnInit() throws Exception {
        String composite = read("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");
        assertTrue(composite.contains("configuredSharcEncoding()"));
        assertTrue(composite.contains("private static SharcRadianceEncoding configuredSharcEncoding()"));
        assertTrue(composite.contains("CausticaConfig.Rt.Sharc.RADIANCE_ENCODING.value()"));
        assertTrue(composite.contains("new AtomicReference<>(configuredSharcEncoding())"));
        assertTrue(composite.contains("activeSharcEncoding = configuredSharcEncoding()"));
    }

    @Test
    void sharcShadersRejectStale16ApiNames() throws Exception {
        String[] staleTokens = {
                "HashMapFindEntry",
                "HashMapInsertEntry",
                "enableAntiFireflyFilter",
                "SHARC_ENABLE_64_BIT_ATOMICS"
        };
        String[] sharcFiles = {
                "shaders/sharc/sharc_bridge.slang",
                "shaders/sharc/sharc_resolve.comp.slang",
                "shaders/world/world.rgen.slang"
        };
        for (String file : sharcFiles) {
            String content = read(file);
            for (String token : staleTokens) {
                assertFalse(content.contains(token),
                        file + " contains stale 1.6 API token: " + token);
            }
        }
        // Reject .hashMapData but not .hashGridParameters
        for (String file : sharcFiles) {
            String content = read(file);
            assertFalse(content.contains(".hashMapData"),
                    file + " contains stale .hashMapData member");
        }
    }

    @Test
    void sharcShadersUse18ApiNames() throws Exception {
        String bridge = read("shaders/sharc/sharc_bridge.slang");
        assertTrue(bridge.contains("HashGridInsertEntry("));
        assertTrue(bridge.contains("hashGridKey"));
        assertTrue(bridge.contains("sharcParameters.hashGridData"));
        assertTrue(bridge.contains("sharcParameters.hashGridParameters"));
        assertTrue(bridge.contains("SharcGetRadianceDirection("));
        assertTrue(bridge.contains("SharcGetRadianceDirectionWeight("));
        assertTrue(bridge.contains("SharcAddVoxelData("));
        assertTrue(bridge.contains("causticaSafeDirection("));
        assertTrue(bridge.contains("causticaSharcDirectionalityWeight("));

        String resolve = read("shaders/sharc/sharc_resolve.comp.slang");
        assertTrue(resolve.contains("responsiveFrameNum"));
        assertTrue(resolve.contains("SharcGetAccumulatedSampleNum("));
        assertTrue(resolve.contains("hashGridData"));

        String rgen = read("shaders/world/world.rgen.slang");
        assertTrue(rgen.contains("outgoingDirectionToPreviousVertex"));
        assertTrue(rgen.contains("causticaSafeDirection("));
        assertTrue(rgen.contains("HashGridFindEntry("));
        assertTrue(rgen.contains("HashGridDebugOccupancy("));
        assertTrue(rgen.contains("SharcSetRadianceDirectionWeight("));
        assertTrue(rgen.contains("hashGridParameters"));
        assertTrue(rgen.contains("hashGridData"));
    }

    @Test
    void bridgeRemovesAntiFireflyFromParameters() throws Exception {
        String bridge = read("shaders/sharc/sharc_bridge.slang");
        assertFalse(bridge.contains("enableAntiFireflyFilter"));
        assertFalse(bridge.contains("antiFirefly"));
        assertTrue(bridge.contains("causticaSharcParameters("));
        // Verify parameter list no longer has the bool
        assertFalse(bridge.contains("bool antiFirefly"));
    }

    @Test
    void sharedSharcBridgeIsStageAgnostic() throws Exception {
        String bridge = read("shaders/sharc/sharc_bridge.slang");
        assertFalse(bridge.contains("payload."),
                "Shared bridge must not reference ray payload");
        assertTrue(bridge.contains("SharcHitData causticaMakeSharcHit("));
        assertFalse(bridge.contains("SharcHitData causticaSharcHit("));

        String world = read("shaders/world/world.rgen.slang");
        assertFalse(world.contains("SharcHitData causticaSharcHit("));
        assertTrue(world.contains("causticaMakeSharcHit(hitPos, payload.geometricNormal,"));
    }

    @Test
    void bridgeExplicitlyDisablesUnportedFeatures() throws Exception {
        String bridge = read("shaders/sharc/sharc_bridge.slang");
        assertTrue(bridge.contains("#define SHARC_ENABLE_CACHE_RESAMPLING 0"),
                "Cache resampling must be explicitly disabled for initial 1.8 migration");
        assertTrue(bridge.contains("#define SHARC_ENABLE_RESPONSIVE_LIGHTING 0"),
                "Responsive lighting must be explicitly disabled for initial 1.8 migration");
    }

    @Test
    void productionManifestCountsOnlySharcResources() throws Exception {
        String build = read("build.gradle");
        assertTrue(build.contains("expectedSharcRuntimeResources"),
                "Production verification should use a dedicated SHARC resource set");
        assertFalse(build.contains("def sharcCount = required.count"),
                "Do not count SHARC modules in the global production resource list");
        assertTrue(build.contains("expectedSharcRuntimeResources.size() != 52"));
        assertTrue(build.contains("unexpectedSharcResources"));
        assertTrue(build.contains("sharc_atomic_probe.comp.spv"),
                "The verifier should explicitly reject the build-only probe from the JAR");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
