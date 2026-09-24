package dev.comfyfluffy.caustica.renderer.presentation;

import dev.comfyfluffy.caustica.api.vulkan.GpuImage;
import dev.comfyfluffy.caustica.api.vulkan.GpuImageDescriptorKind;
import dev.comfyfluffy.caustica.engine.vulkan.runtime.VulkanDeviceContext;
import dev.comfyfluffy.caustica.engine.vulkan.runtime.RtDebugLabels;
import dev.comfyfluffy.caustica.renderer.presentation.gen.DisplayPushData;
import dev.comfyfluffy.caustica.vulkan.ResourceLifetime;
import dev.comfyfluffy.caustica.vulkan.ShaderObjectCompute;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps the display-res scene-linear ACEScg image to sRGB SDR and optional PQ/BT.2020 HDR.
 *
 * <p>The HDR switch is a specialization constant, so each setting is its own shader object carrying
 * only the code it runs. Variants are created on first use and live until {@link #destroy()},
 * because a recorded frame may still reference a variant the settings have since moved away from.
 */
public final class RtDisplayPipeline {
    private static final String SHADER = "/caustica/shaders/pipelines/display/main.comp.spv";
    private final VulkanDeviceContext context;
    private final Map<Variant, ShaderObjectCompute> variants = new HashMap<>();

    /** Specialization of the display shader; component order is the shader's SpecId order. */
    record Variant(boolean hdrEnabled) {
        int[] specializationConstants() {
            return new int[] {hdrEnabled ? 1 : 0};
        }
    }

    private RtDisplayPipeline(VulkanDeviceContext context) {
        this.context = context;
    }

    public static RtDisplayPipeline create(VulkanDeviceContext context) {
        return new RtDisplayPipeline(context);
    }

    public void dispatch(VkCommandBuffer command, GpuImage output, GpuImage scene, GpuImage exposure,
                         GpuImage hdrOutput, RtToneLut toneLut, RtToneLut hdrToneLut,
                         RtToneLut lookLut, boolean hdrEnabled, float gamma, float hdrPeakNits,
                         boolean lookEnabled) {
        ShaderObjectCompute shader = variants.computeIfAbsent(new Variant(hdrEnabled), variant ->
                ShaderObjectCompute.load(context, RtDisplayPipeline.class, SHADER,
                        variant.specializationConstants()));
        try (MemoryStack stack = MemoryStack.stackPush();
             var ignored = RtDebugLabels.scope(context, command, "display compute")) {
            ByteBuffer push = stack.malloc(DisplayPushData.BYTE_SIZE);
            new DisplayPushData(storage(output), storage(scene), storage(exposure), storage(hdrOutput),
                    toneLut.sampledIndex().value(), toneLut.samplerIndex().value(),
                    hdrToneLut.sampledIndex().value(), hdrToneLut.samplerIndex().value(),
                    lookLut.sampledIndex().value(), lookLut.samplerIndex().value(),
                    toneLut.size, gamma, hdrPeakNits,
                    lookEnabled ? 1 : 0, lookLut.size).write(push);
            shader.dispatch(command, push, (output.width() + 15) / 16, (output.height() + 15) / 16, 1);
        }
    }

    private static int storage(GpuImage image) {
        return image.descriptor(GpuImageDescriptorKind.STORAGE).index().value();
    }

    public void destroy() {
        new ResourceLifetime(variants.values().stream()
                .map(shader -> (Runnable) shader::close).toArray(Runnable[]::new)).close();
        variants.clear();
    }
}
