package dev.comfyfluffy.caustica.rt.pipeline;

import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.RtDebugLabels;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static dev.comfyfluffy.caustica.rt.RtContext.check;

/** Three-level scene-linear glare approximation evaluated after reconstruction and exposure metering. */
public final class RtBloomPipeline {
    private static final String SHADER = "/caustica/rt/bloom.comp.spv";
    private final RtContext ctx;
    private final long descriptorSetLayout;
    private final long descriptorPool;
    private final long descriptorSet;
    private final long pipelineLayout;
    private final long pipeline;
    private long boundSource;
    private long boundExposure;
    private long boundHalf;
    private long boundQuarter;
    private long boundEighth;

    private RtBloomPipeline(RtContext ctx, long descriptorSetLayout, long descriptorPool,
                            long descriptorSet, long pipelineLayout, long pipeline) {
        this.ctx = ctx;
        this.descriptorSetLayout = descriptorSetLayout;
        this.descriptorPool = descriptorPool;
        this.descriptorSet = descriptorSet;
        this.pipelineLayout = pipelineLayout;
        this.pipeline = pipeline;
    }

    public static RtBloomPipeline create(RtContext ctx) {
        VkDevice vk = ctx.vk();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer out = stack.mallocLong(1);
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(5, stack);
            for (int i = 0; i < 5; i++) {
                bindings.get(i).binding(i).descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                        .descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);
            }
            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default().pBindings(bindings);
            check(VK10.vkCreateDescriptorSetLayout(vk, layoutInfo, null, out),
                    "vkCreateDescriptorSetLayout(rt bloom)");
            long descriptorSetLayout = out.get(0);

            VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1, stack)
                    .type(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(5);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                    .maxSets(1).pPoolSizes(poolSize);
            check(VK10.vkCreateDescriptorPool(vk, poolInfo, null, out), "vkCreateDescriptorPool(rt bloom)");
            long descriptorPool = out.get(0);

            VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                    .descriptorPool(descriptorPool).pSetLayouts(stack.longs(descriptorSetLayout));
            check(VK10.vkAllocateDescriptorSets(vk, allocateInfo, out), "vkAllocateDescriptorSets(rt bloom)");
            long descriptorSet = out.get(0);

            VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(4);
            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default().pSetLayouts(stack.longs(descriptorSetLayout)).pPushConstantRanges(range);
            check(VK10.vkCreatePipelineLayout(vk, pipelineLayoutInfo, null, out),
                    "vkCreatePipelineLayout(rt bloom)");
            long pipelineLayout = out.get(0);

            long module = loadModule(vk, stack);
            VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack).sType$Default()
                    .stage(VK10.VK_SHADER_STAGE_COMPUTE_BIT).module(module).pName(stack.UTF8("main"));
            VkComputePipelineCreateInfo.Buffer createInfo = VkComputePipelineCreateInfo.calloc(1, stack);
            createInfo.get(0).sType$Default().stage(stage).layout(pipelineLayout);
            check(VK10.vkCreateComputePipelines(vk, VK10.VK_NULL_HANDLE, createInfo, null, out),
                    "vkCreateComputePipelines(rt bloom)");
            long pipeline = out.get(0);
            VK10.vkDestroyShaderModule(vk, module, null);
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_PIPELINE, pipeline, "HDR optical glare");
            return new RtBloomPipeline(ctx, descriptorSetLayout, descriptorPool, descriptorSet,
                    pipelineLayout, pipeline);
        }
    }

    public void setImages(long source, long exposure, long half, long quarter, long eighth) {
        if (source == boundSource && exposure == boundExposure && half == boundHalf
                && quarter == boundQuarter && eighth == boundEighth) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long[] views = {source, exposure, half, quarter, eighth};
            VkDescriptorImageInfo.Buffer images = VkDescriptorImageInfo.calloc(5, stack);
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(5, stack);
            for (int i = 0; i < 5; i++) {
                images.get(i).imageView(views[i]).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
                writes.get(i).sType$Default().dstSet(descriptorSet).dstBinding(i).descriptorCount(1)
                        .descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                        .pImageInfo(VkDescriptorImageInfo.create(images.address(i), 1));
            }
            VK10.vkUpdateDescriptorSets(ctx.vk(), writes, null);
        }
        boundSource = source;
        boundExposure = exposure;
        boundHalf = half;
        boundQuarter = quarter;
        boundEighth = eighth;
    }

    public void dispatch(VkCommandBuffer cmd, int width, int height) {
        int halfW = Math.max(1, (width + 1) / 2);
        int halfH = Math.max(1, (height + 1) / 2);
        int quarterW = Math.max(1, (halfW + 1) / 2);
        int quarterH = Math.max(1, (halfH + 1) / 2);
        int eighthW = Math.max(1, (quarterW + 1) / 2);
        int eighthH = Math.max(1, (quarterH + 1) / 2);
        try (MemoryStack stack = MemoryStack.stackPush();
             RtDebugLabels.Scope ignored = RtDebugLabels.scope(ctx, cmd, "HDR optical glare")) {
            VK10.vkCmdBindPipeline(cmd, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            VK10.vkCmdBindDescriptorSets(cmd, VK10.VK_PIPELINE_BIND_POINT_COMPUTE,
                    pipelineLayout, 0, stack.longs(descriptorSet), null);
            dispatchPass(cmd, stack, 0, halfW, halfH);
            VulkanCommandEncoder.memoryBarrier(cmd, stack);
            dispatchPass(cmd, stack, 1, quarterW, quarterH);
            VulkanCommandEncoder.memoryBarrier(cmd, stack);
            dispatchPass(cmd, stack, 2, eighthW, eighthH);
            VulkanCommandEncoder.memoryBarrier(cmd, stack);
            dispatchPass(cmd, stack, 3, quarterW, quarterH);
            VulkanCommandEncoder.memoryBarrier(cmd, stack);
            dispatchPass(cmd, stack, 4, halfW, halfH);
        }
    }

    private void dispatchPass(VkCommandBuffer cmd, MemoryStack stack, int pass, int width, int height) {
        ByteBuffer push = stack.malloc(4).putInt(0, pass);
        VK10.vkCmdPushConstants(cmd, pipelineLayout, VK10.VK_SHADER_STAGE_COMPUTE_BIT, 0, push);
        VK10.vkCmdDispatch(cmd, (width + 7) / 8, (height + 7) / 8, 1);
    }

    public void destroy() {
        VK10.vkDestroyPipeline(ctx.vk(), pipeline, null);
        VK10.vkDestroyPipelineLayout(ctx.vk(), pipelineLayout, null);
        VK10.vkDestroyDescriptorPool(ctx.vk(), descriptorPool, null);
        VK10.vkDestroyDescriptorSetLayout(ctx.vk(), descriptorSetLayout, null);
    }

    private static long loadModule(VkDevice vk, MemoryStack stack) {
        byte[] bytes;
        try (InputStream input = RtBloomPipeline.class.getResourceAsStream(SHADER)) {
            if (input == null) throw new IllegalStateException("missing SPIR-V resource: " + SHADER);
            bytes = input.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("failed to read " + SHADER, e);
        }
        ByteBuffer code = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
        try {
            VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
            LongBuffer out = stack.mallocLong(1);
            check(VK10.vkCreateShaderModule(vk, info, null, out), "vkCreateShaderModule(rt bloom)");
            return out.get(0);
        } finally {
            MemoryUtil.memFree(code);
        }
    }
}
