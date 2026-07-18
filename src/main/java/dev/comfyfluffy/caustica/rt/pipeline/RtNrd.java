package dev.comfyfluffy.caustica.rt.pipeline;

import dev.comfyfluffy.caustica.CausticaConfig;
import dev.comfyfluffy.caustica.CausticaMod;
import dev.comfyfluffy.caustica.nrd.NrdLibrary;
import dev.comfyfluffy.caustica.nrd.NrdRuntime;
import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.accel.RtImage;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.vulkan.VK10;

/** Cross-platform NRD Vulkan reconstruction backend. */
public final class RtNrd {
    public static final RtNrd INSTANCE = new RtNrd();
    /** View-space distances at or beyond this value are sky and intentionally bypass NRD. */
    public static final float DENOISING_RANGE = 999_000.0f;
    private static final int RESOURCE_SIZE = 16;
    private static final int COMMON_SIZE = 308;
    private static final int SETTINGS_SIZE = 152;

    private NrdLibrary library;
    private boolean initialized;
    private boolean failed;
    private boolean resetHistory = true;
    private int width = -1;
    private int height = -1;
    private int family = -1;
    private boolean sh;
    private long previousFrameNanos;
    private float previousJitterX;
    private float previousJitterY;
    private final Matrix4f previousProjection = new Matrix4f();
    private boolean previousProjectionValid;
    private int lastSettingsIdentity;

    private RtNrd() {
    }

    public boolean isOperational() {
        return RtReconstruction.usesNrd() && !failed;
    }

    public void requestHistoryReset() {
        resetHistory = true;
    }

    public void resetFailureLatch() {
        failed = false;
        requestHistoryReset();
    }

    public boolean ensureFeature(RtContext context, int renderWidth, int renderHeight) {
        if (!isOperational()) return false;
        int requestedFamily = "relax".equals(CausticaConfig.Rt.Nrd.DENOISER.get()) ? 1 : 0;
        boolean requestedSh = CausticaConfig.Rt.Nrd.SPHERICAL_HARMONICS.value();
        if (initialized && width == renderWidth && height == renderHeight
                && family == requestedFamily && sh == requestedSh) {
            return true;
        }
        destroy();
        try {
            library = NrdRuntime.library();
            if (library == null) throw new IllegalStateException(NrdRuntime.unavailableReason());
            long device = context.vk().address();
            long physical = context.vk().getPhysicalDevice().address();
            long instance = context.vk().getPhysicalDevice().getInstance().address();
            int result = library.create(instance, physical, device, context.graphicsQueueFamilyIndex(),
                    renderWidth, renderHeight, requestedFamily, requestedSh,
                    NrdRuntime.enabledDeviceExtensions());
            if (result != 0) throw new IllegalStateException(library.lastError());
            initialized = true;
            width = renderWidth;
            height = renderHeight;
            family = requestedFamily;
            sh = requestedSh;
            resetHistory = true;
            CausticaMod.LOGGER.info("NRD configured: {} {} at {}x{}", requestedFamily == 0 ? "REBLUR" : "RELAX",
                    requestedSh ? "SH" : "radiance", renderWidth, renderHeight);
            return true;
        } catch (Throwable throwable) {
            failed = true;
            CausticaMod.LOGGER.error("NRD setup failed; using native noisy fallback", throwable);
            return false;
        }
    }

    public boolean evaluate(RtContext context, long commandBuffer, RtImage motion, RtImage normalRoughness,
            RtImage viewZ, RtImage diffuseSignal, RtImage diffuseSh1,
            RtImage specularSignal, RtImage specularSh1,
            RtImage diffuseOutput, RtImage diffuseSh1Output,
            RtImage specularOutput, RtImage specularSh1Output,
            Matrix4fc projection, Matrix4fc currentViewProjection, Matrix4fc previousViewProjection,
            float cameraDeltaX, float cameraDeltaY, float cameraDeltaZ,
            float jitterX, float jitterY, int frameIndex, boolean reset) {
        if (!ensureFeature(context, diffuseSignal.width, diffuseSignal.height) || commandBuffer == 0L) return false;
        int settingsIdentity = settingsIdentity();
        if (lastSettingsIdentity != 0 && lastSettingsIdentity != settingsIdentity) resetHistory = true;
        lastSettingsIdentity = settingsIdentity;
        try (Arena arena = Arena.ofConfined()) {
            int resourceCount = sh ? 11 : 7;
            MemorySegment resources = arena.allocate((long) resourceCount * RESOURCE_SIZE, 8);
            writeResource(resources, 0, motion, 0);
            writeResource(resources, 1, normalRoughness, 1);
            writeResource(resources, 2, viewZ, 2);
            if (sh) {
                writeResource(resources, 3, diffuseSignal, 5);
                writeResource(resources, 4, diffuseSh1, 6);
                writeResource(resources, 5, diffuseOutput, 7);
                writeResource(resources, 6, diffuseSh1Output, 8);
                writeResource(resources, 7, specularSignal, 11);
                writeResource(resources, 8, specularSh1, 12);
                writeResource(resources, 9, specularOutput, 13);
                writeResource(resources, 10, specularSh1Output, 14);
            } else {
                writeResource(resources, 3, diffuseSignal, 3);
                writeResource(resources, 4, diffuseOutput, 4);
                writeResource(resources, 5, specularSignal, 9);
                writeResource(resources, 6, specularOutput, 10);
            }

            long now = System.nanoTime();
            float deltaMs = previousFrameNanos == 0L ? 16.6667f : Math.min(1000.0f, (now - previousFrameNanos) / 1_000_000.0f);
            previousFrameNanos = now;
            MemorySegment common = arena.allocate(COMMON_SIZE, 4);
            ByteBuffer bytes = common.asByteBuffer().order(ByteOrder.nativeOrder());
            putMatrix(bytes, 0, projection);
            Matrix4fc projectionPrev = previousProjectionValid && !(reset || resetHistory)
                    ? previousProjection : projection;
            putMatrix(bytes, 64, projectionPrev);
            Matrix4f inverseProjection = new Matrix4f(projection).invert();
            Matrix4f currentView = new Matrix4f(inverseProjection).mul(currentViewProjection);
            Matrix4f previousViewForCurrentOrigin = new Matrix4f(projectionPrev).invert()
                    .mul(previousViewProjection).translate(cameraDeltaX, cameraDeltaY, cameraDeltaZ);
            putMatrix(bytes, 128, currentView);
            putMatrix(bytes, 192, previousViewForCurrentOrigin);
            bytes.putFloat(256, 1.0f / width).putFloat(260, 1.0f / height).putFloat(264, 0.0f);
            bytes.putFloat(268, jitterX).putFloat(272, jitterY);
            bytes.putFloat(276, previousJitterX).putFloat(280, previousJitterY);
            bytes.putFloat(284, deltaMs).putFloat(288, DENOISING_RANGE);
            bytes.putFloat(292, CausticaConfig.Rt.Nrd.DISOCCLUSION_THRESHOLD.value());
            bytes.putFloat(296, CausticaConfig.Rt.Nrd.SPLIT_SCREEN.value());
            bytes.putInt(300, frameIndex).putInt(304, reset || resetHistory ? 1 : 0);

            MemorySegment settings = arena.allocate(SETTINGS_SIZE, 4);
            ByteBuffer tune = settings.asByteBuffer().order(ByteOrder.nativeOrder());
            tune.putInt(0, CausticaConfig.Rt.Nrd.MAX_ACCUMULATED_FRAMES.value());
            tune.putInt(4, CausticaConfig.Rt.Nrd.MAX_FAST_ACCUMULATED_FRAMES.value());
            tune.putInt(8, CausticaConfig.Rt.Nrd.HISTORY_FIX_FRAMES.value());
            tune.putInt(12, CausticaConfig.Rt.Nrd.HISTORY_FIX_STRIDE.value());
            tune.putInt(16, CausticaConfig.Rt.Nrd.RELAX_ATROUS_ITERATIONS.value());
            tune.putInt(20, CausticaConfig.Rt.Nrd.ANTI_FIREFLY.value() ? 1 : 0);
            tune.putInt(24, CausticaConfig.Rt.Nrd.ANTILAG.value() ? 1 : 0);
            tune.putFloat(28, CausticaConfig.Rt.Nrd.PREPASS_BLUR_RADIUS.value());
            tune.putFloat(32, CausticaConfig.Rt.Nrd.MIN_BLUR_RADIUS.value());
            tune.putFloat(36, CausticaConfig.Rt.Nrd.MAX_BLUR_RADIUS.value());
            tune.putFloat(40, CausticaConfig.Rt.Nrd.LOBE_ANGLE_FRACTION.value());
            tune.putFloat(44, CausticaConfig.Rt.Nrd.ROUGHNESS_FRACTION.value());
            tune.putFloat(48, CausticaConfig.Rt.Nrd.PLANE_DISTANCE_SENSITIVITY.value());
            tune.putFloat(52, CausticaConfig.Rt.Nrd.HIT_DISTANCE_A.value());
            tune.putFloat(56, CausticaConfig.Rt.Nrd.HIT_DISTANCE_B.value());
            tune.putFloat(60, CausticaConfig.Rt.Nrd.HIT_DISTANCE_C.value());
            tune.putFloat(64, CausticaConfig.Rt.Nrd.ANTILAG_SIGMA.value());
            tune.putInt(68, CausticaConfig.Rt.Nrd.MAX_STABILIZED_FRAMES.value());
            tune.putInt(72, CausticaConfig.Rt.Nrd.RESPONSIVE_MIN_FRAMES.value());
            tune.putInt(76, CausticaConfig.Rt.Nrd.RELAX_ROUGHNESS_EDGE_STOPPING.value() ? 1 : 0);
            tune.putFloat(80, CausticaConfig.Rt.Nrd.SPECULAR_PREPASS_BLUR_RADIUS.value());
            tune.putFloat(84, CausticaConfig.Rt.Nrd.FAST_HISTORY_CLAMP_SIGMA.value());
            tune.putFloat(88, CausticaConfig.Rt.Nrd.MIN_HIT_DISTANCE_WEIGHT.value());
            tune.putFloat(92, CausticaConfig.Rt.Nrd.FIREFLY_SUPPRESSOR_SCALE.value());
            tune.putFloat(96, CausticaConfig.Rt.Nrd.RESPONSIVE_ROUGHNESS_THRESHOLD.value());
            tune.putFloat(100, CausticaConfig.Rt.Nrd.CONVERGENCE_SCALE.value());
            tune.putFloat(104, CausticaConfig.Rt.Nrd.CONVERGENCE_BASE.value());
            tune.putFloat(108, CausticaConfig.Rt.Nrd.CONVERGENCE_HISTORY_FRACTION.value());
            tune.putFloat(112, CausticaConfig.Rt.Nrd.RELAX_HISTORY_NORMAL_POWER.value());
            tune.putFloat(116, CausticaConfig.Rt.Nrd.RELAX_DIFFUSE_PHI_LUMINANCE.value());
            tune.putFloat(120, CausticaConfig.Rt.Nrd.RELAX_SPECULAR_PHI_LUMINANCE.value());
            tune.putFloat(124, CausticaConfig.Rt.Nrd.RELAX_DEPTH_THRESHOLD.value());
            tune.putFloat(128, CausticaConfig.Rt.Nrd.RELAX_SPECULAR_VARIANCE_BOOST.value());
            tune.putFloat(132, CausticaConfig.Rt.Nrd.RELAX_SPECULAR_LOBE_SLACK.value());
            tune.putFloat(136, CausticaConfig.Rt.Nrd.ANTILAG_SENSITIVITY.value());
            tune.putFloat(140, CausticaConfig.Rt.Nrd.RELAX_ANTILAG_ACCELERATION.value());
            tune.putFloat(144, CausticaConfig.Rt.Nrd.RELAX_ANTILAG_TEMPORAL_SIGMA.value());
            tune.putFloat(148, CausticaConfig.Rt.Nrd.RELAX_ANTILAG_RESET.value());

            int result = library.evaluate(commandBuffer, resources, resourceCount, common, settings);
            if (result != 0) throw new IllegalStateException(library.lastError());
            resetHistory = false;
            previousJitterX = jitterX;
            previousJitterY = jitterY;
            previousProjection.set(projection);
            previousProjectionValid = true;
            return true;
        } catch (Throwable throwable) {
            failed = true;
            CausticaMod.LOGGER.error("NRD evaluate failed; using native noisy fallback", throwable);
            return false;
        }
    }

    public void destroy() {
        if (library != null && initialized) {
            try {
                library.destroy();
            } catch (Throwable throwable) {
                CausticaMod.LOGGER.warn("Could not destroy NRD cleanly", throwable);
            }
        }
        initialized = false;
        width = height = -1;
        family = -1;
        library = null;
        previousFrameNanos = 0L;
        previousProjectionValid = false;
        lastSettingsIdentity = 0;
    }

    private static void writeResource(MemorySegment resources, int index, RtImage image, int slot) {
        if (image == null || image.image == 0L) throw new IllegalArgumentException("Missing NRD resource " + slot);
        ByteBuffer bytes = resources.asByteBuffer().order(ByteOrder.nativeOrder());
        int offset = index * RESOURCE_SIZE;
        bytes.putLong(offset, image.image).putInt(offset + 8, image.format).putInt(offset + 12, slot);
    }

    private static void putMatrix(ByteBuffer bytes, int offset, Matrix4fc matrix) {
        float[] values = new float[16];
        matrix.get(values);
        for (int i = 0; i < values.length; i++) bytes.putFloat(offset + i * 4, values[i]);
    }

    private static int settingsIdentity() {
        int hash = 1;
        hash = mix(hash, CausticaConfig.Rt.Nrd.MAX_ACCUMULATED_FRAMES.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.MAX_FAST_ACCUMULATED_FRAMES.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.MAX_STABILIZED_FRAMES.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.HISTORY_FIX_FRAMES.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.HISTORY_FIX_STRIDE.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.RELAX_ATROUS_ITERATIONS.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.ANTI_FIREFLY.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.ANTILAG.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.PREPASS_BLUR_RADIUS.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.SPECULAR_PREPASS_BLUR_RADIUS.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.MIN_BLUR_RADIUS.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.MAX_BLUR_RADIUS.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.LOBE_ANGLE_FRACTION.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.ROUGHNESS_FRACTION.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.PLANE_DISTANCE_SENSITIVITY.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.HIT_DISTANCE_A.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.HIT_DISTANCE_B.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.HIT_DISTANCE_C.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.ANTILAG_SIGMA.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.ANTILAG_SENSITIVITY.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.RESPONSIVE_ROUGHNESS_THRESHOLD.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.RESPONSIVE_MIN_FRAMES.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.FAST_HISTORY_CLAMP_SIGMA.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.MIN_HIT_DISTANCE_WEIGHT.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.FIREFLY_SUPPRESSOR_SCALE.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.CONVERGENCE_SCALE.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.CONVERGENCE_BASE.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.CONVERGENCE_HISTORY_FRACTION.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.RELAX_HISTORY_NORMAL_POWER.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.RELAX_DIFFUSE_PHI_LUMINANCE.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.RELAX_SPECULAR_PHI_LUMINANCE.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.RELAX_DEPTH_THRESHOLD.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.RELAX_SPECULAR_VARIANCE_BOOST.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.RELAX_SPECULAR_LOBE_SLACK.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.RELAX_ROUGHNESS_EDGE_STOPPING.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.RELAX_ANTILAG_ACCELERATION.value());
        hash = mix(hash, CausticaConfig.Rt.Nrd.RELAX_ANTILAG_TEMPORAL_SIGMA.value());
        return mix(hash, CausticaConfig.Rt.Nrd.RELAX_ANTILAG_RESET.value());
    }

    private static int mix(int hash, int value) {
        return 31 * hash + value;
    }

    private static int mix(int hash, float value) {
        return mix(hash, Float.floatToIntBits(value));
    }

    private static int mix(int hash, boolean value) {
        return mix(hash, value ? 1 : 0);
    }
}
