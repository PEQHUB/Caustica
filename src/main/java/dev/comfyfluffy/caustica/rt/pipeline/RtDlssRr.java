package dev.comfyfluffy.caustica.rt.pipeline;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import dev.comfyfluffy.caustica.CausticaConfig;
import dev.comfyfluffy.caustica.CausticaMod;
import dev.comfyfluffy.caustica.mixin.GpuDeviceAccessor;
import dev.comfyfluffy.caustica.rt.accel.RtImage;
import dev.comfyfluffy.caustica.streamline.StreamlineAbi;
import dev.comfyfluffy.caustica.streamline.StreamlineLibrary;
import dev.comfyfluffy.caustica.streamline.StreamlineRuntime;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.vulkan.VK10;

/** Streamline-owned DLSS Ray Reconstruction backend for the path-traced renderer. */
public final class RtDlssRr {
    public static final RtDlssRr INSTANCE = new RtDlssRr();

    // Streamline allows one common-constants packet per frame token and viewport. RR consumes raw
    // render-space inputs while DLSSG viewport 0 consumes final-present-space inputs, so RR owns viewport 1.
    private static final int VIEWPORT = 1;
    private static final int RESULT_OK = 0;
    private static final int CORE_RESOURCE_COUNT = 10;
    private static final int DIFFUSE_PATH_RESOURCE_COUNT = 11;
    private static final int TRANSPARENCY_LAYER_RESOURCE_COUNT = 13;
    private static final int DIFFUSE_PATH_TRANSPARENCY_LAYER_RESOURCE_COUNT = 14;
    private static final int LIFECYCLE_VALID_UNTIL_EVALUATE = 2;

    private static final int BUFFER_DEPTH = 0;
    private static final int BUFFER_MOTION_VECTORS = 1;
    private static final int BUFFER_SCALING_INPUT_COLOR = 3;
    private static final int BUFFER_SCALING_OUTPUT_COLOR = 4;
    private static final int BUFFER_ALBEDO = 7;
    private static final int BUFFER_SPECULAR_ALBEDO = 8;
    private static final int BUFFER_SPECULAR_MOTION_VECTORS = 10;
    private static final int BUFFER_DISOCCLUSION_MASK = 11;
    private static final int BUFFER_NORMAL_ROUGHNESS = 14;
    static final int BUFFER_PARTICLE_HINT = 26;
    private static final int BUFFER_BIAS_CURRENT_COLOR_HINT = 29;
    static final int BUFFER_COLOR_BEFORE_TRANSPARENCY = 40;
    private static final int BUFFER_DIFFUSE_RAY_DIRECTION_HIT_DISTANCE = 46;
    static final int BUFFER_TRANSPARENCY_LAYER = 51;
    static final int BUFFER_TRANSPARENCY_LAYER_OPACITY = 52;
    static final int TRANSPARENCY_LAYER_FORMAT = VK10.VK_FORMAT_R16G16B16A16_SFLOAT;
    static final int TRANSPARENCY_LAYER_OPACITY_FORMAT = VK10.VK_FORMAT_R16G16B16A16_SFLOAT;

    private StreamlineLibrary library;
    private boolean initialized;
    private boolean failed;
    private boolean loggedAvailability;
    private boolean configured;
    private boolean resourcesCreated;
    private boolean loggedEvaluation;
    private boolean resetHistory;
    private int lastOptionsResult = Integer.MIN_VALUE;
    private int lastEvaluateResult = Integer.MIN_VALUE;
    private long javaOptionsCalls;
    private long javaEvaluateCalls;
    private int lastResourceCount;
    private boolean fallbackActive;
    private long fallbackFrames;
    private String fallbackReason = "Not evaluated";
    private final Matrix4f worldToCameraView = new Matrix4f();
    private final Matrix4f cameraViewToWorld = new Matrix4f();

    private int featureRenderWidth = -1;
    private int featureRenderHeight = -1;
    private int featureDisplayWidth = -1;
    private int featureDisplayHeight = -1;
    private int featureQuality = Integer.MIN_VALUE;
    private int featurePreset = Integer.MIN_VALUE;
    private int effectiveQuality = quality();
    private int effectivePreset = renderPreset();
    private DlssdResolutionPlan resolutionPlan;
    private final Map<PlanKey, DlssdResolutionPlan> resolutionPlans = new HashMap<>();

    private RtDlssRr() {
    }

    public static boolean enabled() {
        return CausticaConfig.Rt.DlssRr.ENABLED.value();
    }

    public static int quality() {
        return CausticaConfig.Rt.DlssRr.QUALITY.value();
    }

    public static int renderPreset() {
        return normalizePreset(CausticaConfig.Rt.DlssRr.PRESET.value());
    }

    public static int normalizePreset(int preset) {
        return preset == 4 || preset == 5 ? preset : 0;
    }

    public static void selectPreset(int preset) {
        CausticaConfig.Rt.DlssRr.PRESET.set(normalizePreset(preset));
        INSTANCE.requestHistoryReset();
    }

    /** Caustica's persisted quality values 0..5 map exactly to Streamline DLSSMode values 1..6. */
    private static int streamlineMode(int quality) {
        return quality >= 0 && quality <= 5 ? quality + 1 : 1;
    }

    public boolean isReady() {
        return initialized && !failed && configured;
    }

    public boolean isOperational() {
        return enabled() && !failed;
    }

    public int lastOptionsResult() {
        return lastOptionsResult;
    }

    public int lastEvaluateResult() {
        return lastEvaluateResult;
    }

    public long javaOptionsCalls() {
        return javaOptionsCalls;
    }

    public long javaEvaluateCalls() {
        return javaEvaluateCalls;
    }

    public int lastResourceCount() {
        return lastResourceCount;
    }

    public boolean fallbackActive() {
        return fallbackActive;
    }

    public long fallbackFrames() {
        return fallbackFrames;
    }

    public String fallbackReason() {
        return fallbackReason;
    }

    public DlssdResolutionPlan resolutionPlan() {
        return resolutionPlan;
    }

    public String resolutionPath() {
        return resolutionPlan == null ? "Unavailable" : resolutionPlan.describe();
    }

    public boolean failed() {
        return failed;
    }

    public void requestHistoryReset() {
        resetHistory = true;
    }

    public void resetFailureLatch() {
        failed = false;
        requestHistoryReset();
    }

    public static int requiredResourceCount() {
        return requiredResourceCount(CausticaConfig.Rt.DlssRr.DIFFUSE_PATH_GUIDE.value(), false,
                CausticaConfig.Rt.DlssRr.PARTICLE_TEMPORAL_HISTORY.value());
    }

    static int requiredResourceCount(boolean diffusePathGuide) {
        return requiredResourceCount(diffusePathGuide, false);
    }

    static int requiredResourceCount(boolean diffusePathGuide, boolean layeredTransparency) {
        return requiredResourceCount(diffusePathGuide, layeredTransparency, false);
    }

    static int requiredResourceCount(boolean diffusePathGuide, boolean layeredTransparency,
            boolean particleHint) {
        int resourceCount;
        if (layeredTransparency) {
            resourceCount = diffusePathGuide
                    ? DIFFUSE_PATH_TRANSPARENCY_LAYER_RESOURCE_COUNT
                    : TRANSPARENCY_LAYER_RESOURCE_COUNT;
        } else {
            resourceCount = diffusePathGuide ? DIFFUSE_PATH_RESOURCE_COUNT : CORE_RESOURCE_COUNT;
        }
        return resourceCount + (particleHint ? 1 : 0);
    }

    /**
     * Record one Streamline DLSS-RR evaluation into {@code commandBuffer}. RR uses local render-space
     * constants because its inputs have not undergone the final Vulkan Y reversal; DLSSG later publishes
     * one global present-space constants packet for its pre-flipped resources using the same frame token.
     */
    public boolean evaluate(long commandBuffer, RtImage color, RtImage depth, RtImage motion,
            RtImage diffuseAlbedo, RtImage specularAlbedo, RtImage normalRoughness,
            RtImage specularMotion, RtImage disocclusion, RtImage biasCurrentColor,
            RtImage diffuseRayDirectionHitDistance, RtImage output,
            int renderWidth, int renderHeight, int displayWidth, int displayHeight,
            float jitterX, float jitterY, Matrix4fc projection,
            Matrix4fc currentViewProjection, Matrix4fc previousViewProjection, Matrix4fc viewRotation,
            double cameraX, double cameraY, double cameraZ,
            float cameraDeltaX, float cameraDeltaY, float cameraDeltaZ, boolean reset) {
        return evaluate(commandBuffer, color, depth, motion, diffuseAlbedo, specularAlbedo,
                normalRoughness, specularMotion, disocclusion, biasCurrentColor, null,
                diffuseRayDirectionHitDistance, null, null, null, output,
                renderWidth, renderHeight, displayWidth, displayHeight,
                jitterX, jitterY, projection, currentViewProjection, previousViewProjection, viewRotation,
                cameraX, cameraY, cameraZ, cameraDeltaX, cameraDeltaY, cameraDeltaZ, reset);
    }

    public boolean evaluate(long commandBuffer, RtImage color, RtImage depth, RtImage motion,
            RtImage diffuseAlbedo, RtImage specularAlbedo, RtImage normalRoughness,
            RtImage specularMotion, RtImage disocclusion, RtImage biasCurrentColor,
            RtImage particleHint, RtImage diffuseRayDirectionHitDistance, RtImage colorBeforeTransparency,
            RtImage transparencyLayer,
            RtImage transparencyLayerOpacity, RtImage output,
            int renderWidth, int renderHeight, int displayWidth, int displayHeight,
            float jitterX, float jitterY, Matrix4fc projection,
            Matrix4fc currentViewProjection, Matrix4fc previousViewProjection, Matrix4fc viewRotation,
            double cameraX, double cameraY, double cameraZ,
            float cameraDeltaX, float cameraDeltaY, float cameraDeltaZ, boolean reset) {
        if (!isReady() || commandBuffer == 0L) {
            return false;
        }
        long frameToken = RtDlssFg.INSTANCE.frameTokenForFeature();
        if (frameToken == 0L) {
            return false;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment options = StreamlineAbi.allocate(arena, StreamlineAbi.DLSSD_OPTIONS_SIZE);
            writeOptions(options, streamlineMode(effectiveQuality), displayWidth, displayHeight,
                    effectivePreset, viewRotation);
            javaOptionsCalls++;
            lastOptionsResult = library.setDlssdOptions(VIEWPORT, options);
            check(lastOptionsResult, "slDLSSDSetOptions");

            MemorySegment constants = StreamlineAbi.allocate(arena, StreamlineAbi.CONSTANTS_SIZE);
            RtDlssFg.INSTANCE.writeRenderSpaceConstants(constants, projection, currentViewProjection,
                    previousViewProjection, viewRotation, jitterX, jitterY, renderWidth, renderHeight,
                    cameraX, cameraY, cameraZ, cameraDeltaX, cameraDeltaY, cameraDeltaZ,
                    reset || resetHistory);

            boolean diffusePathGuide = CausticaConfig.Rt.DlssRr.DIFFUSE_PATH_GUIDE.value();
            boolean anyTransparencyResource = colorBeforeTransparency != null
                    || transparencyLayer != null || transparencyLayerOpacity != null;
            boolean completeTransparencyResources = colorBeforeTransparency != null
                    && transparencyLayer != null && transparencyLayerOpacity != null;
            if (anyTransparencyResource && !completeTransparencyResources) {
                throw new IllegalArgumentException(
                        "Streamline DLSS-RR color-before, transparency layer, and opacity must be supplied together");
            }
            boolean layeredTransparency = completeTransparencyResources;
            boolean particleHistory = particleHint != null;
            int resourceCount = requiredResourceCount(diffusePathGuide, layeredTransparency, particleHistory);
            MemorySegment resources = StreamlineAbi.allocate(arena,
                    StreamlineAbi.RESOURCE_DESC_SIZE * resourceCount);
            writeResource(resources, 0, color, VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                    renderWidth, renderHeight, BUFFER_SCALING_INPUT_COLOR);
            writeResource(resources, 1, depth, VK10.VK_FORMAT_R32_SFLOAT,
                    renderWidth, renderHeight, BUFFER_DEPTH);
            writeResource(resources, 2, motion, VK10.VK_FORMAT_R16G16_SFLOAT,
                    renderWidth, renderHeight, BUFFER_MOTION_VECTORS);
            writeResource(resources, 3, diffuseAlbedo, VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                    renderWidth, renderHeight, BUFFER_ALBEDO);
            writeResource(resources, 4, specularAlbedo, VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                    renderWidth, renderHeight, BUFFER_SPECULAR_ALBEDO);
            writeResource(resources, 5, normalRoughness, VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                    renderWidth, renderHeight, BUFFER_NORMAL_ROUGHNESS);
            writeResource(resources, 6, specularMotion, VK10.VK_FORMAT_R16G16_SFLOAT,
                    renderWidth, renderHeight, BUFFER_SPECULAR_MOTION_VECTORS);
            writeResource(resources, 7, disocclusion, VK10.VK_FORMAT_R16_SFLOAT,
                    renderWidth, renderHeight, BUFFER_DISOCCLUSION_MASK);
            writeResource(resources, 8, biasCurrentColor, VK10.VK_FORMAT_R16_SFLOAT,
                    renderWidth, renderHeight, BUFFER_BIAS_CURRENT_COLOR_HINT);
            int optionalResource = 9;
            if (particleHistory) {
                writeResource(resources, optionalResource++, particleHint, VK10.VK_FORMAT_R16_SFLOAT,
                        renderWidth, renderHeight, BUFFER_PARTICLE_HINT);
            }
            writeResource(resources, optionalResource++, output, VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                    displayWidth, displayHeight, BUFFER_SCALING_OUTPUT_COLOR);
            if (diffusePathGuide) {
                writeResource(resources, optionalResource++, diffuseRayDirectionHitDistance,
                        VK10.VK_FORMAT_R16G16B16A16_SFLOAT, renderWidth, renderHeight,
                        BUFFER_DIFFUSE_RAY_DIRECTION_HIT_DISTANCE);
            }
            if (layeredTransparency) {
                // ScalingInputColor above remains the final noisy color. This separate snapshot is the
                // exact base before the premultiplied optical overlay, as required by DLSS-RR.
                writeResource(resources, optionalResource++, colorBeforeTransparency,
                        VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                        renderWidth, renderHeight, BUFFER_COLOR_BEFORE_TRANSPARENCY);
                writeResource(resources, optionalResource++, transparencyLayer,
                        TRANSPARENCY_LAYER_FORMAT, renderWidth, renderHeight,
                        BUFFER_TRANSPARENCY_LAYER);
                writeResource(resources, optionalResource, transparencyLayerOpacity,
                        TRANSPARENCY_LAYER_OPACITY_FORMAT, renderWidth, renderHeight,
                        BUFFER_TRANSPARENCY_LAYER_OPACITY);
            }

            resourcesCreated = true;
            lastResourceCount = resourceCount;
            javaEvaluateCalls++;
            lastEvaluateResult = library.evaluateDlssd(frameToken, VIEWPORT, resources, resourceCount,
                    constants, commandBuffer);
            check(lastEvaluateResult, "slEvaluateFeature(DLSS-RR)");
            resetHistory = false;
            fallbackActive = false;
            fallbackReason = "None";
            if (!loggedEvaluation) {
                loggedEvaluation = true;
                CausticaMod.LOGGER.info(
                        "Streamline DLSS-RR evaluation active: {}x{} -> {}x{} (quality {}, preset {}, frame token 0x{})",
                        renderWidth, renderHeight, displayWidth, displayHeight, effectiveQuality, effectivePreset,
                        Long.toHexString(frameToken));
            }
            return true;
        } catch (Throwable throwable) {
            failed = true;
            fallbackActive = true;
            fallbackReason = throwable.getMessage() == null ? throwable.getClass().getSimpleName()
                    : throwable.getMessage();
            CausticaMod.LOGGER.error("Streamline DLSS-RR evaluate failed; RT composite continues without it",
                    throwable);
            return false;
        }
    }

    /** Records the renderer's actual post-RR fallback decision for acceptance telemetry. */
    public void recordFallback(boolean rrRequestedForFrame, boolean evaluationCompleted) {
        if (resolutionPlan != null && !resolutionPlan.usesDlssd()) {
            fallbackActive = true;
            fallbackFrames++;
            fallbackReason = resolutionPlan.reason();
        } else if (!rrRequestedForFrame) {
            fallbackActive = false;
            fallbackReason = "DLSSD disabled or debug view selected";
        } else if (evaluationCompleted) {
            fallbackActive = false;
            fallbackReason = "None";
        } else {
            fallbackActive = true;
            fallbackFrames++;
            if (!failed) {
                fallbackReason = "DLSSD evaluation did not complete";
            }
        }
        StreamlineAcceptanceReport.publish();
    }

    /** Query the Streamline DLSS-RR plugin for the exact input size for the selected quality mode. */
    public int[] queryOptimalRenderSize(int displayWidth, int displayHeight) {
        return querySupportedRenderSize(displayWidth, displayHeight, -1, -1);
    }

    /**
     * Query the plugin's current size contract without changing the requested trace resolution.
     * The advertised min/max range describes supported dynamic-resolution subrects, but it must not
     * silently rewrite an explicit input-resolution request. Caustica submits the exact subrect even
     * outside that range (allowing ratios above 3x); an actual NGX evaluation error remains recoverable
     * through the existing spatial fallback instead of preemptively disabling DLSSD.
     */
    public int[] querySupportedRenderSize(int displayWidth, int displayHeight,
            int requestedWidth, int requestedHeight) {
        DlssdResolutionPlan plan = queryResolutionPlan(displayWidth, displayHeight,
                requestedWidth > 0 ? requestedWidth : displayWidth,
                requestedHeight > 0 ? requestedHeight : displayHeight,
                quality(), renderPreset());
        return plan == null ? null : new int[] {plan.traceWidth(), plan.traceHeight(),
                plan.traceAllocationWidth(), plan.traceAllocationHeight()};
    }

    public DlssdResolutionPlan queryResolutionPlan(int displayWidth, int displayHeight,
            int requestedWidth, int requestedHeight, int configuredQuality, int configuredPreset) {
        if (!enabled() || failed) {
            return null;
        }
        try {
            if (!(((GpuDeviceAccessor) RenderSystem.getDevice()).caustica$getBackend()
                    instanceof VulkanDevice device)) {
                throw new IllegalStateException("Vulkan backend unavailable for DLSSD optimal-size query");
            }
            ensureInitialized(device);
            int normalizedPreset = normalizePreset(configuredPreset);
            PlanKey key = new PlanKey(device.vkDevice().getPhysicalDevice().address(),
                    System.identityHashCode(library), displayWidth, displayHeight,
                    requestedWidth, requestedHeight, configuredQuality, normalizedPreset);
            DlssdResolutionPlan plan = resolutionPlans.get(key);
            if (plan == null) {
                plan = DlssdResolutionPlanner.plan(requestedWidth, requestedHeight,
                        displayWidth, displayHeight, configuredQuality, this::querySettings);
                resolutionPlans.put(key, plan);
                CausticaMod.LOGGER.info(
                        "Streamline DLSS-RR resolution plan: {} (quality {}, range {}x{}..{}x{}, allocation {}x{})",
                        plan.describe(), plan.quality(), plan.renderWidthMin(), plan.renderHeightMin(),
                        plan.renderWidthMax(), plan.renderHeightMax(),
                        plan.traceAllocationWidth(), plan.traceAllocationHeight());
            }
            resolutionPlan = plan;
            effectiveQuality = plan.quality();
            effectivePreset = normalizedPreset;
            fallbackActive = !plan.usesDlssd();
            fallbackReason = plan.usesDlssd() ? "None" : plan.reason();
            return plan;
        } catch (Throwable throwable) {
            failed = true;
            fallbackActive = true;
            fallbackReason = throwable.getMessage() == null ? throwable.getClass().getSimpleName()
                    : throwable.getMessage();
            CausticaMod.LOGGER.error(
                    "Streamline DLSS-RR optimal-size query failed; using native-resolution fallback",
                    throwable);
            return null;
        }
    }

    private DlssdResolutionPlanner.Settings querySettings(int candidate, int outputWidth, int outputHeight) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment renderWidth = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment renderHeight = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment sharpness = arena.allocate(ValueLayout.JAVA_FLOAT);
            MemorySegment renderWidthMin = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment renderHeightMin = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment renderWidthMax = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment renderHeightMax = arena.allocate(ValueLayout.JAVA_INT);
            int result = library.getDlssdOptimalSettings(streamlineMode(candidate),
                    outputWidth, outputHeight, renderWidth, renderHeight, sharpness,
                    renderWidthMin, renderHeightMin, renderWidthMax, renderHeightMax);
            if (result != RESULT_OK) return null;
            return new DlssdResolutionPlanner.Settings(
                    renderWidth.get(ValueLayout.JAVA_INT, 0), renderHeight.get(ValueLayout.JAVA_INT, 0),
                    renderWidthMin.get(ValueLayout.JAVA_INT, 0), renderHeightMin.get(ValueLayout.JAVA_INT, 0),
                    renderWidthMax.get(ValueLayout.JAVA_INT, 0), renderHeightMax.get(ValueLayout.JAVA_INT, 0));
        }
    }

    /** Configure the lazy Streamline feature instance for the requested dimensions and quality. */
    public boolean ensureFeature(long commandBuffer, int renderWidth, int renderHeight,
            int displayWidth, int displayHeight) {
        if (!enabled() || failed || commandBuffer == 0L) {
            return false;
        }
        if (!(((GpuDeviceAccessor) RenderSystem.getDevice()).caustica$getBackend()
                instanceof VulkanDevice device)) {
            return false;
        }
        try {
            ensureInitialized(device);
            int quality = effectiveQuality;
            int preset = effectivePreset;
            if (featureDisplayWidth != displayWidth || featureDisplayHeight != displayHeight
                    || featureQuality != quality || featurePreset != preset) {
                releaseResources(device);
                featureRenderWidth = renderWidth;
                featureRenderHeight = renderHeight;
                featureDisplayWidth = displayWidth;
                featureDisplayHeight = displayHeight;
                featureQuality = quality;
                featurePreset = preset;
                configured = true;
                resetHistory = true;
                CausticaMod.LOGGER.info("Streamline DLSS-RR configured: {}x{} -> {}x{} (quality {}, preset {})",
                        renderWidth, renderHeight, displayWidth, displayHeight, quality, preset);
            }
            return true;
        } catch (Throwable throwable) {
            failed = true;
            CausticaMod.LOGGER.error("Streamline DLSS-RR setup failed; RT composite continues without it",
                    throwable);
            return false;
        }
    }

    private void ensureInitialized(VulkanDevice device) {
        if (initialized) {
            return;
        }
        if (!StreamlineRuntime.initializeForVulkan() || StreamlineRuntime.library() == null) {
            throw new IllegalStateException("Streamline runtime unavailable; DLSS-RR cannot initialize");
        }
        library = StreamlineRuntime.library();
        int supportResult = StreamlineRuntime.supportsFeature(StreamlineRuntime.FEATURE_DLSS_RR,
                device.vkDevice().getPhysicalDevice().address());
        boolean available = supportResult == RESULT_OK;
        if (!loggedAvailability) {
            loggedAvailability = true;
            CausticaMod.LOGGER.info("Streamline DLSS Ray Reconstruction available: {}{}", available,
                    available ? "" : " (" + StreamlineRuntime.lastError() + ")");
        }
        if (!available) {
            throw new IllegalStateException("Streamline DLSS Ray Reconstruction is unavailable: "
                    + StreamlineRuntime.lastError());
        }
        initialized = true;
    }

    /** Release only the Streamline RR viewport resources; Streamline owns the shared NGX lifetime. */
    public void destroy() {
        if (((GpuDeviceAccessor) RenderSystem.getDevice()).caustica$getBackend()
                instanceof VulkanDevice device) {
            releaseResources(device);
        }
        initialized = false;
        configured = false;
        resourcesCreated = false;
        loggedEvaluation = false;
        resetHistory = false;
        library = null;
        resolutionPlan = null;
        resolutionPlans.clear();
    }

    private void releaseResources(VulkanDevice device) {
        if (resourcesCreated && library != null && StreamlineRuntime.initialized()) {
            check(StreamlineRuntime.vkDeviceWaitIdle(device.vkDevice(), "DLSSD resource release", false),
                    "vkDeviceWaitIdle(DLSSD resource release)");
            int result = library.freeDlssdResources(VIEWPORT);
            if (result != RESULT_OK) {
                CausticaMod.LOGGER.warn("Could not release Streamline DLSS-RR resources: {}",
                        StreamlineRuntime.lastError());
            }
        }
        resourcesCreated = false;
        configured = false;
        featureRenderWidth = -1;
        featureRenderHeight = -1;
        featureDisplayWidth = -1;
        featureDisplayHeight = -1;
        featureQuality = Integer.MIN_VALUE;
        featurePreset = Integer.MIN_VALUE;
    }

    /**
     * Releases the feature only when its creation identity will change. Input subrect changes inside
     * the advertised range keep the signed Streamline feature alive and merely retag current images.
     */
    public void prepareForOutputChange(int displayWidth, int displayHeight, boolean featureNeeded) {
        if (!initialized || library == null) return;
        boolean incompatible = !featureNeeded
                || featureDisplayWidth != displayWidth || featureDisplayHeight != displayHeight
                || featureQuality != effectiveQuality || featurePreset != effectivePreset;
        if (!incompatible) return;
        if (((GpuDeviceAccessor) RenderSystem.getDevice()).caustica$getBackend()
                instanceof VulkanDevice device) {
            try {
                releaseResources(device);
            } catch (Throwable throwable) {
                failed = true;
                fallbackActive = true;
                fallbackReason = "DLSSD feature release failed: " + throwable.getMessage();
                CausticaMod.LOGGER.warn("Could not release incompatible DLSSD feature cleanly", throwable);
            }
        }
    }

    private record PlanKey(long adapter, int runtimeIdentity, int outputWidth, int outputHeight,
                           int requestedWidth, int requestedHeight, int quality, int preset) {
    }

    private void check(int result, String operation) {
        if (result != RESULT_OK) {
            throw new IllegalStateException(operation + " failed: " + StreamlineRuntime.lastError());
        }
    }

    private void writeOptions(MemorySegment segment, int mode, int outputWidth, int outputHeight,
            int preset, Matrix4fc viewRotation) {
        ByteBuffer bytes = StreamlineAbi.bytes(segment);
        bytes.putInt(0, mode);
        bytes.putInt(4, outputWidth);
        bytes.putInt(8, outputHeight);
        bytes.putInt(12, preset);
        worldToCameraView.set(viewRotation);
        cameraViewToWorld.set(viewRotation).invert();
        StreamlineAbi.writeRowVectorMatrix(bytes, 16, worldToCameraView);
        StreamlineAbi.writeRowVectorMatrix(bytes, 80, cameraViewToWorld);
    }

    private static void writeResource(MemorySegment resources, int index, RtImage image, int format,
            int width, int height, int bufferType) {
        if (image == null || image.image == 0L || image.view == 0L
                || image.width < width || image.height < height || image.format != format) {
            throw new IllegalArgumentException("Incomplete Streamline DLSS-RR resource " + index
                    + " for " + width + "x" + height);
        }
        writeResource(resources, index, image.image, image.view, format,
                image.width, image.height, width, height, bufferType, image.usage);
    }

    static void writeResource(MemorySegment resources, int index, long image, long view, int format,
            int width, int height, int bufferType, int usage) {
        writeResource(resources, index, image, view, format,
                width, height, width, height, bufferType, usage);
    }

    static void writeResource(MemorySegment resources, int index, long image, long view, int format,
            int allocationWidth, int allocationHeight, int extentWidth, int extentHeight,
            int bufferType, int usage) {
        ByteBuffer bytes = StreamlineAbi.bytes(resources);
        int base = index * StreamlineAbi.RESOURCE_DESC_SIZE;
        bytes.putLong(base, image);
        bytes.putLong(base + 8, view);
        // Streamline's Vulkan manual-hooking contract identifies textures with VkImage + VkImageView.
        // VkDeviceMemory is intentionally null; exposing allocator backing ownership is unsupported.
        bytes.putLong(base + 16, 0L);
        bytes.putInt(base + 24, VK10.VK_IMAGE_LAYOUT_GENERAL);
        bytes.putInt(base + 28, allocationWidth);
        bytes.putInt(base + 32, allocationHeight);
        bytes.putInt(base + 36, format);
        bytes.putInt(base + 40, 1);
        bytes.putInt(base + 44, 1);
        bytes.putInt(base + 48, 0);
        bytes.putInt(base + 52, usage);
        bytes.putInt(base + 56, bufferType);
        bytes.putInt(base + 60, LIFECYCLE_VALID_UNTIL_EVALUATE);
        bytes.put(base + 64, (byte) 1);
        bytes.putInt(base + 68, extentWidth);
        bytes.putInt(base + 72, extentHeight);
    }
}
