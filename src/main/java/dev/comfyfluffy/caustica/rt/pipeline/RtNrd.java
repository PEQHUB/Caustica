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
    private static final int RESOURCE_SIZE = 16;
    private static final int COMMON_SIZE = 308;
    private static final int SETTINGS_SIZE = 68;

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
                    renderWidth, renderHeight, requestedFamily, requestedSh);
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
            RtImage viewZ, RtImage signal, RtImage sh1, RtImage output, RtImage sh1Output,
            Matrix4fc projection, Matrix4fc currentViewProjection, Matrix4fc previousViewProjection,
            float cameraDeltaX, float cameraDeltaY, float cameraDeltaZ,
            float jitterX, float jitterY, int frameIndex, boolean reset) {
        if (!ensureFeature(context, signal.width, signal.height) || commandBuffer == 0L) return false;
        try (Arena arena = Arena.ofConfined()) {
            int resourceCount = sh ? 7 : 5;
            MemorySegment resources = arena.allocate((long) resourceCount * RESOURCE_SIZE, 8);
            writeResource(resources, 0, motion, 0);
            writeResource(resources, 1, normalRoughness, 1);
            writeResource(resources, 2, viewZ, 2);
            if (sh) {
                writeResource(resources, 3, signal, 5);
                writeResource(resources, 4, sh1, 6);
                writeResource(resources, 5, output, 7);
                writeResource(resources, 6, sh1Output, 8);
            } else {
                writeResource(resources, 3, signal, 3);
                writeResource(resources, 4, output, 4);
            }

            long now = System.nanoTime();
            float deltaMs = previousFrameNanos == 0L ? 16.6667f : Math.min(1000.0f, (now - previousFrameNanos) / 1_000_000.0f);
            previousFrameNanos = now;
            MemorySegment common = arena.allocate(COMMON_SIZE, 4);
            ByteBuffer bytes = common.asByteBuffer().order(ByteOrder.nativeOrder());
            putMatrix(bytes, 0, projection);
            putMatrix(bytes, 64, projection);
            Matrix4f inverseProjection = new Matrix4f(projection).invert();
            Matrix4f currentView = new Matrix4f(inverseProjection).mul(currentViewProjection);
            Matrix4f previousViewForCurrentOrigin = new Matrix4f(inverseProjection)
                    .mul(previousViewProjection).translate(cameraDeltaX, cameraDeltaY, cameraDeltaZ);
            putMatrix(bytes, 128, currentView);
            putMatrix(bytes, 192, previousViewForCurrentOrigin);
            bytes.putFloat(256, 1.0f / width).putFloat(260, 1.0f / height).putFloat(264, 0.0f);
            bytes.putFloat(268, jitterX / width).putFloat(272, jitterY / height);
            bytes.putFloat(276, previousJitterX / width).putFloat(280, previousJitterY / height);
            bytes.putFloat(284, deltaMs).putFloat(288, 1_000_000.0f);
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

            int result = library.evaluate(commandBuffer, resources, resourceCount, common, settings);
            if (result != 0) throw new IllegalStateException(library.lastError());
            resetHistory = false;
            previousJitterX = jitterX;
            previousJitterY = jitterY;
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
}
