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

/** Adds deterministic primary-sky stars after reconstruction, before exposure and optical glare. */
public final class RtSkyStarLayerPipeline {
    private static final String SHADER = "/caustica/rt/sky_stars.comp.spv";
    private final RtContext context;
    private final long descriptorSetLayout;
    private final long descriptorPool;
    private final long descriptorSet;
    private final long pipelineLayout;
    private final long pipeline;
    private long boundOutput;
    private long boundMask;
    private long boundDepth;

    private RtSkyStarLayerPipeline(RtContext context, long descriptorSetLayout, long descriptorPool,
            long descriptorSet, long pipelineLayout, long pipeline) {
        this.context = context;
        this.descriptorSetLayout = descriptorSetLayout;
        this.descriptorPool = descriptorPool;
        this.descriptorSet = descriptorSet;
        this.pipelineLayout = pipelineLayout;
        this.pipeline = pipeline;
    }

    public static RtSkyStarLayerPipeline create(RtContext context) {
        VkDevice device = context.vk();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer out = stack.mallocLong(1);
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(3, stack);
            for (int i = 0; i < 3; i++) {
                bindings.get(i).binding(i).descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                        .descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);
            }
            check(VK10.vkCreateDescriptorSetLayout(device,
                    VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default().pBindings(bindings), null, out),
                    "vkCreateDescriptorSetLayout(sky stars)");
            long setLayout = out.get(0);
            VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1, stack)
                    .type(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(3);
            check(VK10.vkCreateDescriptorPool(device,
                    VkDescriptorPoolCreateInfo.calloc(stack).sType$Default().maxSets(1).pPoolSizes(poolSize),
                    null, out), "vkCreateDescriptorPool(sky stars)");
            long pool = out.get(0);
            check(VK10.vkAllocateDescriptorSets(device,
                    VkDescriptorSetAllocateInfo.calloc(stack).sType$Default().descriptorPool(pool)
                            .pSetLayouts(stack.longs(setLayout)), out),
                    "vkAllocateDescriptorSets(sky stars)");
            long set = out.get(0);
            VkPushConstantRange.Buffer pushRange = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(24);
            check(VK10.vkCreatePipelineLayout(device,
                    VkPipelineLayoutCreateInfo.calloc(stack).sType$Default().pSetLayouts(stack.longs(setLayout))
                            .pPushConstantRanges(pushRange), null, out),
                    "vkCreatePipelineLayout(sky stars)");
            long layout = out.get(0);
            long module = loadModule(device, stack);
            VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack).sType$Default()
                    .stage(VK10.VK_SHADER_STAGE_COMPUTE_BIT).module(module).pName(stack.UTF8("main"));
            VkComputePipelineCreateInfo.Buffer info = VkComputePipelineCreateInfo.calloc(1, stack);
            info.get(0).sType$Default().stage(stage).layout(layout);
            check(VK10.vkCreateComputePipelines(device, VK10.VK_NULL_HANDLE, info, null, out),
                    "vkCreateComputePipelines(sky stars)");
            long pipeline = out.get(0);
            VK10.vkDestroyShaderModule(device, module, null);
            RtDebugLabels.name(context, VK10.VK_OBJECT_TYPE_PIPELINE, pipeline, "direct sky stars");
            return new RtSkyStarLayerPipeline(context, setLayout, pool, set, layout, pipeline);
        }
    }

    public void setImages(long output, long mask, long depth) {
        if (output == boundOutput && mask == boundMask && depth == boundDepth) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long[] views = {output, mask, depth};
            VkDescriptorImageInfo.Buffer images = VkDescriptorImageInfo.calloc(3, stack);
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(3, stack);
            for (int i = 0; i < 3; i++) {
                images.get(i).imageView(views[i]).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
                writes.get(i).sType$Default().dstSet(descriptorSet).dstBinding(i).descriptorCount(1)
                        .descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                        .pImageInfo(VkDescriptorImageInfo.create(images.address(i), 1));
            }
            VK10.vkUpdateDescriptorSets(context.vk(), writes, null);
        }
        boundOutput = output;
        boundMask = mask;
        boundDepth = depth;
    }

    public void dispatch(VkCommandBuffer commandBuffer, long worldPushAddress,
            int sourceWidth, int sourceHeight, int outputWidth, int outputHeight) {
        try (MemoryStack stack = MemoryStack.stackPush();
                RtDebugLabels.Scope ignored = RtDebugLabels.scope(context, commandBuffer, "direct sky stars")) {
            VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            VK10.vkCmdBindDescriptorSets(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_COMPUTE,
                    pipelineLayout, 0, stack.longs(descriptorSet), null);
            ByteBuffer push = stack.malloc(24).putLong(0, worldPushAddress)
                    .putInt(8, sourceWidth).putInt(12, sourceHeight)
                    .putInt(16, outputWidth).putInt(20, outputHeight);
            VK10.vkCmdPushConstants(commandBuffer, pipelineLayout, VK10.VK_SHADER_STAGE_COMPUTE_BIT,
                    0, push);
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
        try (InputStream input = RtSkyStarLayerPipeline.class.getResourceAsStream(SHADER)) {
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
                    "vkCreateShaderModule(sky stars)");
            return output.get(0);
        } finally {
            MemoryUtil.memFree(code);
        }
    }
}
