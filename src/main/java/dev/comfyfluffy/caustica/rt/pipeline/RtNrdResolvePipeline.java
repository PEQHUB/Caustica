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

/** Converts NRD's packed radiance/SH output back to Caustica scene-linear RGB. */
public final class RtNrdResolvePipeline {
    private static final String SHADER = "/caustica/rt/nrd_resolve.comp.spv";
    private final RtContext context;
    private final long descriptorSetLayout;
    private final long descriptorPool;
    private final long descriptorSet;
    private final long pipelineLayout;
    private final long pipeline;

    private RtNrdResolvePipeline(RtContext context, long descriptorSetLayout, long descriptorPool,
            long descriptorSet, long pipelineLayout, long pipeline) {
        this.context = context;
        this.descriptorSetLayout = descriptorSetLayout;
        this.descriptorPool = descriptorPool;
        this.descriptorSet = descriptorSet;
        this.pipelineLayout = pipelineLayout;
        this.pipeline = pipeline;
    }

    public static RtNrdResolvePipeline create(RtContext context) {
        VkDevice device = context.vk();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer output = stack.mallocLong(1);
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(9, stack);
            for (int i = 0; i < 9; i++) bindings.get(i).binding(i)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(1)
                    .stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);
            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default().pBindings(bindings);
            check(VK10.vkCreateDescriptorSetLayout(device, layoutInfo, null, output),
                    "vkCreateDescriptorSetLayout(NRD resolve)");
            long setLayout = output.get(0);
            VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1, stack)
                    .type(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(9);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                    .maxSets(1).pPoolSizes(poolSize);
            check(VK10.vkCreateDescriptorPool(device, poolInfo, null, output),
                    "vkCreateDescriptorPool(NRD resolve)");
            long pool = output.get(0);
            VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                    .descriptorPool(pool).pSetLayouts(stack.longs(setLayout));
            check(VK10.vkAllocateDescriptorSets(device, allocateInfo, output),
                    "vkAllocateDescriptorSets(NRD resolve)");
            long set = output.get(0);
            VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(16);
            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default().pSetLayouts(stack.longs(setLayout)).pPushConstantRanges(range);
            check(VK10.vkCreatePipelineLayout(device, pipelineLayoutInfo, null, output),
                    "vkCreatePipelineLayout(NRD resolve)");
            long pipelineLayout = output.get(0);
            long module = loadModule(device, stack);
            VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack).sType$Default()
                    .stage(VK10.VK_SHADER_STAGE_COMPUTE_BIT).module(module).pName(stack.UTF8("main"));
            VkComputePipelineCreateInfo.Buffer createInfo = VkComputePipelineCreateInfo.calloc(1, stack);
            createInfo.get(0).sType$Default().stage(stage).layout(pipelineLayout);
            check(VK10.vkCreateComputePipelines(device, 0L, createInfo, null, output),
                    "vkCreateComputePipelines(NRD resolve)");
            long pipeline = output.get(0);
            VK10.vkDestroyShaderModule(device, module, null);
            RtDebugLabels.name(context, VK10.VK_OBJECT_TYPE_PIPELINE, pipeline, "NRD resolve");
            return new RtNrdResolvePipeline(context, setLayout, pool, set, pipelineLayout, pipeline);
        }
    }

    public void setImages(long diffuseSh0, long diffuseSh1, long specularSh0, long specularSh1,
            long normalRoughness, long diffuseAlbedo, long specularAlbedo, long viewDirection, long output) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long[] views = {diffuseSh0, diffuseSh1, specularSh0, specularSh1,
                    normalRoughness, diffuseAlbedo, specularAlbedo, viewDirection, output};
            VkDescriptorImageInfo.Buffer images = VkDescriptorImageInfo.calloc(9, stack);
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(9, stack);
            for (int i = 0; i < 9; i++) {
                images.get(i).imageView(views[i]).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
                writes.get(i).sType$Default().dstSet(descriptorSet).dstBinding(i).descriptorCount(1)
                        .descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                        .pImageInfo(VkDescriptorImageInfo.create(images.address(i), 1));
            }
            VK10.vkUpdateDescriptorSets(context.vk(), writes, null);
        }
    }

    public void dispatch(VkCommandBuffer commandBuffer, int width, int height, int family, boolean sh) {
        try (MemoryStack stack = MemoryStack.stackPush();
                RtDebugLabels.Scope ignored = RtDebugLabels.scope(context, commandBuffer, "NRD resolve")) {
            VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            VK10.vkCmdBindDescriptorSets(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_COMPUTE,
                    pipelineLayout, 0, stack.longs(descriptorSet), null);
            ByteBuffer push = stack.malloc(16).putInt(0, width).putInt(4, height)
                    .putInt(8, family).putInt(12, sh ? 1 : 0);
            VK10.vkCmdPushConstants(commandBuffer, pipelineLayout, VK10.VK_SHADER_STAGE_COMPUTE_BIT, 0, push);
            VK10.vkCmdDispatch(commandBuffer, (width + 15) / 16, (height + 15) / 16, 1);
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
        try (InputStream input = RtNrdResolvePipeline.class.getResourceAsStream(SHADER)) {
            if (input == null) throw new IllegalStateException("missing SPIR-V resource: " + SHADER);
            bytes = input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read " + SHADER, exception);
        }
        ByteBuffer code = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
        try {
            VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
            LongBuffer output = stack.mallocLong(1);
            check(VK10.vkCreateShaderModule(device, info, null, output), "vkCreateShaderModule(NRD resolve)");
            return output.get(0);
        } finally {
            MemoryUtil.memFree(code);
        }
    }
}
