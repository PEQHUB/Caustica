package dev.comfyfluffy.caustica.rt.pipeline;

import static dev.comfyfluffy.caustica.rt.RtContext.check;

import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.RtDebugLabels;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

/** Cross-vendor HDR bicubic/contrast-adaptive upscaler used only for sub-native NRD modes. */
public final class RtNrdSpatialUpscalePipeline {
    private static final String SHADER = "/caustica/rt/nrd_spatial_upscale.comp.spv";
    private final RtContext context;
    private final long descriptorSetLayout;
    private final long descriptorPool;
    private final long descriptorSet;
    private final long pipelineLayout;
    private final long pipeline;

    private RtNrdSpatialUpscalePipeline(RtContext context, long descriptorSetLayout, long descriptorPool,
            long descriptorSet, long pipelineLayout, long pipeline) {
        this.context = context;
        this.descriptorSetLayout = descriptorSetLayout;
        this.descriptorPool = descriptorPool;
        this.descriptorSet = descriptorSet;
        this.pipelineLayout = pipelineLayout;
        this.pipeline = pipeline;
    }

    public static RtNrdSpatialUpscalePipeline create(RtContext context) {
        VkDevice device = context.vk();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer out = stack.mallocLong(1);
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(2, stack);
            for (int i = 0; i < 2; ++i) bindings.get(i).binding(i)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(1)
                    .stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);
            check(VK10.vkCreateDescriptorSetLayout(device,
                    VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default().pBindings(bindings), null, out),
                    "vkCreateDescriptorSetLayout(NRD spatial upscale)");
            long setLayout = out.get(0);
            VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1, stack)
                    .type(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(2);
            check(VK10.vkCreateDescriptorPool(device,
                    VkDescriptorPoolCreateInfo.calloc(stack).sType$Default().maxSets(1).pPoolSizes(poolSize),
                    null, out), "vkCreateDescriptorPool(NRD spatial upscale)");
            long pool = out.get(0);
            check(VK10.vkAllocateDescriptorSets(device,
                    VkDescriptorSetAllocateInfo.calloc(stack).sType$Default().descriptorPool(pool)
                            .pSetLayouts(stack.longs(setLayout)), out),
                    "vkAllocateDescriptorSets(NRD spatial upscale)");
            long set = out.get(0);
            VkPushConstantRange.Buffer pushRange = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(20);
            check(VK10.vkCreatePipelineLayout(device,
                    VkPipelineLayoutCreateInfo.calloc(stack).sType$Default().pSetLayouts(stack.longs(setLayout))
                            .pPushConstantRanges(pushRange), null, out),
                    "vkCreatePipelineLayout(NRD spatial upscale)");
            long layout = out.get(0);
            long module = loadModule(device, stack);
            VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack).sType$Default()
                    .stage(VK10.VK_SHADER_STAGE_COMPUTE_BIT).module(module).pName(stack.UTF8("main"));
            VkComputePipelineCreateInfo.Buffer info = VkComputePipelineCreateInfo.calloc(1, stack);
            info.get(0).sType$Default().stage(stage).layout(layout);
            check(VK10.vkCreateComputePipelines(device, 0L, info, null, out),
                    "vkCreateComputePipelines(NRD spatial upscale)");
            long pipeline = out.get(0);
            VK10.vkDestroyShaderModule(device, module, null);
            RtDebugLabels.name(context, VK10.VK_OBJECT_TYPE_PIPELINE, pipeline, "NRD spatial upscale");
            return new RtNrdSpatialUpscalePipeline(context, setLayout, pool, set, layout, pipeline);
        }
    }

    public void setImages(long input, long output) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorImageInfo.Buffer images = VkDescriptorImageInfo.calloc(2, stack);
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(2, stack);
            long[] views = {input, output};
            for (int i = 0; i < 2; ++i) {
                images.get(i).imageView(views[i]).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
                writes.get(i).sType$Default().dstSet(descriptorSet).dstBinding(i).descriptorCount(1)
                        .descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                        .pImageInfo(VkDescriptorImageInfo.create(images.address(i), 1));
            }
            VK10.vkUpdateDescriptorSets(context.vk(), writes, null);
        }
    }

    public void dispatch(VkCommandBuffer commandBuffer, int inputWidth, int inputHeight,
            int outputWidth, int outputHeight, float sharpness) {
        try (MemoryStack stack = MemoryStack.stackPush();
                RtDebugLabels.Scope ignored = RtDebugLabels.scope(
                        context, commandBuffer, "edge-adaptive spatial upscale")) {
            VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            VK10.vkCmdBindDescriptorSets(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_COMPUTE,
                    pipelineLayout, 0, stack.longs(descriptorSet), null);
            ByteBuffer push = stack.malloc(20).putInt(0, inputWidth).putInt(4, inputHeight)
                    .putInt(8, outputWidth).putInt(12, outputHeight).putFloat(16, sharpness);
            VK10.vkCmdPushConstants(commandBuffer, pipelineLayout, VK10.VK_SHADER_STAGE_COMPUTE_BIT, 0, push);
            VK10.vkCmdDispatch(commandBuffer, (outputWidth + 15) / 16, (outputHeight + 15) / 16, 1);
        }
    }

    public void destroy() {
        VK10.vkDestroyPipeline(context.vk(), pipeline, null);
        VK10.vkDestroyPipelineLayout(context.vk(), pipelineLayout, null);
        VK10.vkDestroyDescriptorPool(context.vk(), descriptorPool, null);
        VK10.vkDestroyDescriptorSetLayout(context.vk(), descriptorSetLayout, null);
    }

    private static long loadModule(VkDevice device, MemoryStack stack) {
        byte[] bytes;
        try (InputStream input = RtNrdSpatialUpscalePipeline.class.getResourceAsStream(SHADER)) {
            if (input == null) throw new IllegalStateException("missing SPIR-V resource: " + SHADER);
            bytes = input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read " + SHADER, exception);
        }
        ByteBuffer code = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
        try {
            VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
            LongBuffer output = stack.mallocLong(1);
            check(VK10.vkCreateShaderModule(device, info, null, output),
                    "vkCreateShaderModule(NRD spatial upscale)");
            return output.get(0);
        } finally {
            MemoryUtil.memFree(code);
        }
    }
}
