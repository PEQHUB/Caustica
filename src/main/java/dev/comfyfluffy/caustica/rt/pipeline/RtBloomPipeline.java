package dev.comfyfluffy.caustica.rt.pipeline;

import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.RtDebugLabels;
import dev.comfyfluffy.caustica.rt.accel.RtImage;
import dev.comfyfluffy.caustica.rt.gen.BloomPushData;
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
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static dev.comfyfluffy.caustica.rt.RtContext.check;
import static dev.comfyfluffy.caustica.rt.pipeline.RtBindings.*;

/**
 * Scene-referred bloom as a downsample/upsample mip pyramid (Jimenez, SIGGRAPH 2014). The pyramid
 * provides broad support without comb-spaced taps or a fixed-width highlight slab.
 *
 * <p>Each pyramid step is its own dispatch with its own descriptor set holding that step's destination
 * storage image and source sampled image. Binding the pair per step, rather than indexing a descriptor
 * array from a push constant, keeps the shader free of dynamic storage-image indexing — which would
 * require {@code shaderStorageImageArrayDynamicIndexing}, a feature this device bring-up does not ask
 * for.
 *
 * <p>Bloom stays outside the temporal reconstruction and the exposure histogram; only the display mapper
 * consumes the finished pyramid, whose level 0 accumulates every band.
 */
public final class RtBloomPipeline {
    private static final String SHADER = "/caustica/shaders/pipelines/bloom/main.comp.spv";
    /** Pyramid depth ceiling. Level 7 of a 4K pyramid is already 15x8 texels — nothing wider is useful. */
    public static final int MAX_LEVELS = 8;
    private static final int MODE_PREFILTER = 0;
    private static final int MODE_DOWNSAMPLE = 1;
    private static final int MODE_UPSAMPLE = 2;
    // One set per possible step: [0] prefilter into level 0, [i] downsample into level i (i >= 1),
    // [MAX_LEVELS + i] upsample level i+1 onto level i.
    private static final int SET_COUNT = MAX_LEVELS * 2;

    private final RtContext ctx;
    private final long descriptorSetLayout;
    private final long descriptorPool;
    private final long[] descriptorSets;
    private final long pipelineLayout;
    private final long pipeline;
    private final long sampler;
    private long boundSourceView;
    private long boundExposureView;
    private long[] boundLevelViews = new long[0];
    private boolean destroyed;

    private RtBloomPipeline(RtContext ctx, long descriptorSetLayout, long descriptorPool,
                            long[] descriptorSets, long pipelineLayout, long pipeline, long sampler) {
        this.ctx = ctx;
        this.descriptorSetLayout = descriptorSetLayout;
        this.descriptorPool = descriptorPool;
        this.descriptorSets = descriptorSets;
        this.pipelineLayout = pipelineLayout;
        this.pipeline = pipeline;
        this.sampler = sampler;
    }

    public static RtBloomPipeline create(RtContext ctx) {
        VkDevice vk = ctx.vk();
        long descriptorSetLayout = 0L;
        long descriptorPool = 0L;
        long pipelineLayout = 0L;
        long module = 0L;
        long pipeline = 0L;
        long sampler = 0L;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer bindings =
                    VkDescriptorSetLayoutBinding.calloc(BLOOM_BINDING_COUNT, stack);
            bindings.get(BLOOM_OUTPUT).binding(BLOOM_OUTPUT)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(1)
                    .stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);
            bindings.get(BLOOM_SOURCE).binding(BLOOM_SOURCE)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);
            bindings.get(BLOOM_EXPOSURE).binding(BLOOM_EXPOSURE)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(1)
                    .stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);

            LongBuffer handle = stack.mallocLong(1);
            VkDescriptorSetLayoutCreateInfo layoutInfo =
                    VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default().pBindings(bindings);
            check(VK10.vkCreateDescriptorSetLayout(vk, layoutInfo, null, handle),
                    "vkCreateDescriptorSetLayout(rt bloom)");
            descriptorSetLayout = handle.get(0);
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_DESCRIPTOR_SET_LAYOUT,
                    descriptorSetLayout, "bloom descriptor set layout");

            VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(2, stack);
            poolSize.get(0).type(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(SET_COUNT * 2);
            poolSize.get(1).type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(SET_COUNT);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default().maxSets(SET_COUNT).pPoolSizes(poolSize);
            check(VK10.vkCreateDescriptorPool(vk, poolInfo, null, handle),
                    "vkCreateDescriptorPool(rt bloom)");
            descriptorPool = handle.get(0);
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_DESCRIPTOR_POOL,
                    descriptorPool, "bloom descriptor pool");

            LongBuffer setLayouts = stack.mallocLong(SET_COUNT);
            for (int i = 0; i < SET_COUNT; i++) {
                setLayouts.put(i, descriptorSetLayout);
            }
            VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default().descriptorPool(descriptorPool).pSetLayouts(setLayouts);
            LongBuffer setHandles = stack.mallocLong(SET_COUNT);
            check(VK10.vkAllocateDescriptorSets(vk, allocateInfo, setHandles),
                    "vkAllocateDescriptorSets(rt bloom)");
            long[] descriptorSets = new long[SET_COUNT];
            for (int i = 0; i < SET_COUNT; i++) {
                descriptorSets[i] = setHandles.get(i);
                RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_DESCRIPTOR_SET,
                        descriptorSets[i], "bloom descriptor set " + i);
            }

            VkPushConstantRange.Buffer pushRange = VkPushConstantRange.calloc(1, stack);
            pushRange.get(0).stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT)
                    .offset(0).size(BloomPushData.BYTE_SIZE);
            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default().pSetLayouts(stack.longs(descriptorSetLayout))
                    .pPushConstantRanges(pushRange);
            check(VK10.vkCreatePipelineLayout(vk, pipelineLayoutInfo, null, handle),
                    "vkCreatePipelineLayout(rt bloom)");
            pipelineLayout = handle.get(0);
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_PIPELINE_LAYOUT,
                    pipelineLayout, "bloom pipeline layout");

            module = loadModule(vk, stack);
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_SHADER_MODULE, module, "bloom shader module");
            VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack)
                    .sType$Default().stage(VK10.VK_SHADER_STAGE_COMPUTE_BIT)
                    .module(module).pName(stack.UTF8("main"));
            VkComputePipelineCreateInfo.Buffer pipelineInfo =
                    VkComputePipelineCreateInfo.calloc(1, stack);
            pipelineInfo.get(0).sType$Default().stage(stage).layout(pipelineLayout);
            LongBuffer pipelineHandle = stack.mallocLong(1);
            check(VK10.vkCreateComputePipelines(vk, VK10.VK_NULL_HANDLE,
                    pipelineInfo, null, pipelineHandle), "vkCreateComputePipelines(rt bloom)");
            pipeline = pipelineHandle.get(0);
            VK10.vkDestroyShaderModule(vk, module, null);
            module = 0L;
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_PIPELINE,
                    pipeline, "bloom compute pipeline");

            // CLAMP_TO_EDGE, not border: a highlight touching the frame edge should bleed along the edge
            // like a real lens, not fade into a black border that reads as a dark seam.
            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack).sType$Default()
                    .magFilter(VK10.VK_FILTER_LINEAR)
                    .minFilter(VK10.VK_FILTER_LINEAR)
                    .mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .minLod(0.0f)
                    .maxLod(0.0f);
            check(VK10.vkCreateSampler(vk, samplerInfo, null, handle),
                    "vkCreateSampler(rt bloom)");
            sampler = handle.get(0);
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_SAMPLER, sampler, "bloom linear sampler");

            return new RtBloomPipeline(ctx, descriptorSetLayout, descriptorPool, descriptorSets,
                    pipelineLayout, pipeline, sampler);
        } catch (Throwable t) {
            if (module != 0L) VK10.vkDestroyShaderModule(vk, module, null);
            if (sampler != 0L) VK10.vkDestroySampler(vk, sampler, null);
            if (pipeline != 0L) VK10.vkDestroyPipeline(vk, pipeline, null);
            if (pipelineLayout != 0L) VK10.vkDestroyPipelineLayout(vk, pipelineLayout, null);
            if (descriptorPool != 0L) VK10.vkDestroyDescriptorPool(vk, descriptorPool, null);
            if (descriptorSetLayout != 0L) VK10.vkDestroyDescriptorSetLayout(vk, descriptorSetLayout, null);
            throw t;
        }
    }

    public long sampler() {
        return sampler;
    }

    /**
     * Deepest pyramid the given level-0 size supports: halving stops before a level would lose an axis.
     * A level of 8 texels is already a whole-screen blur, so this rarely binds before {@link #MAX_LEVELS}.
     */
    public static int levelsFor(int level0Width, int level0Height, int requested) {
        int levels = 1;
        int w = level0Width;
        int h = level0Height;
        while (levels < Math.min(requested, MAX_LEVELS) && w > 8 && h > 8) {
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
            levels++;
        }
        return levels;
    }

    /**
     * Point every step's descriptor set at this frame's images. {@code levels[0]} is the half-resolution
     * prefiltered level the display mapper reads; the rest are the pyramid.
     */
    public void setImages(long sourceView, long exposureView, RtImage[] levels) {
        if (boundSourceView == sourceView && boundExposureView == exposureView
                && sameViews(levels)) {
            return;
        }
        int levelCount = levels.length;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int stepCount = 1 + (levelCount - 1) * 2; // prefilter + downsamples + upsamples
            VkDescriptorImageInfo.Buffer images = VkDescriptorImageInfo.calloc(stepCount * 3, stack);
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(stepCount * 3, stack);
            int index = 0;
            for (int step = 0; step < stepCount; step++) {
                long dstView;
                long srcView;
                int set;
                if (step == 0) {
                    set = 0;
                    dstView = levels[0].view;
                    srcView = sourceView;
                } else if (step < levelCount) {
                    set = step;
                    dstView = levels[step].view;
                    srcView = levels[step - 1].view;
                } else {
                    // Upsample steps run coarse-to-fine; the set index only has to be unique per step.
                    int dst = levelCount - 1 - (step - levelCount) - 1;
                    set = MAX_LEVELS + dst;
                    dstView = levels[dst].view;
                    srcView = levels[dst + 1].view;
                }
                index = writeSet(stack, images, writes, index, descriptorSets[set],
                        dstView, srcView, exposureView);
            }
            VkWriteDescriptorSet.Buffer used = VkWriteDescriptorSet.create(writes.address(), index);
            VK10.vkUpdateDescriptorSets(ctx.vk(), used, null);
        }
        boundSourceView = sourceView;
        boundExposureView = exposureView;
        boundLevelViews = new long[levelCount];
        for (int i = 0; i < levelCount; i++) {
            boundLevelViews[i] = levels[i].view;
        }
    }

    private boolean sameViews(RtImage[] levels) {
        if (boundLevelViews.length != levels.length) {
            return false;
        }
        for (int i = 0; i < levels.length; i++) {
            if (boundLevelViews[i] != levels[i].view) {
                return false;
            }
        }
        return true;
    }

    private int writeSet(MemoryStack stack, VkDescriptorImageInfo.Buffer images,
                         VkWriteDescriptorSet.Buffer writes, int index, long set,
                         long dstView, long srcView, long exposureView) {
        images.get(index).imageView(dstView).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
        writes.get(index).sType$Default().dstSet(set).dstBinding(BLOOM_OUTPUT)
                .descriptorCount(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                .pImageInfo(VkDescriptorImageInfo.create(images.address(index), 1));
        index++;
        images.get(index).imageView(srcView).sampler(sampler)
                .imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
        writes.get(index).sType$Default().dstSet(set).dstBinding(BLOOM_SOURCE)
                .descriptorCount(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .pImageInfo(VkDescriptorImageInfo.create(images.address(index), 1));
        index++;
        images.get(index).imageView(exposureView).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
        writes.get(index).sType$Default().dstSet(set).dstBinding(BLOOM_EXPOSURE)
                .descriptorCount(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                .pImageInfo(VkDescriptorImageInfo.create(images.address(index), 1));
        return index + 1;
    }

    /**
     * Record the whole pyramid: prefilter into level 0, downsample to the top, then tent back down
     * accumulating each band. A memory barrier separates every step — each one reads exactly what the
     * previous wrote.
     */
    public void dispatch(VkCommandBuffer cmd, RtImage[] levels,
                         float threshold, float softKneeFraction, float radius) {
        float softKnee = threshold * softKneeFraction;
        int levelCount = levels.length;
        try (MemoryStack stack = MemoryStack.stackPush();
             RtDebugLabels.Scope ignored = RtDebugLabels.scope(ctx, cmd, "scene bloom")) {
            VK10.vkCmdBindPipeline(cmd, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            recordStep(cmd, stack, descriptorSets[0], levels[0], MODE_PREFILTER,
                    threshold, softKnee, radius);
            for (int level = 1; level < levelCount; level++) {
                recordStep(cmd, stack, descriptorSets[level], levels[level], MODE_DOWNSAMPLE,
                        threshold, softKnee, radius);
            }
            for (int level = levelCount - 2; level >= 0; level--) {
                recordStep(cmd, stack, descriptorSets[MAX_LEVELS + level], levels[level], MODE_UPSAMPLE,
                        threshold, softKnee, radius);
            }
        }
    }

    private void recordStep(VkCommandBuffer cmd, MemoryStack stack, long set, RtImage dst, int mode,
                            float threshold, float softKnee, float radius) {
        VK10.vkCmdBindDescriptorSets(cmd, VK10.VK_PIPELINE_BIND_POINT_COMPUTE,
                pipelineLayout, 0, stack.longs(set), null);
        ByteBuffer push = stack.malloc(BloomPushData.BYTE_SIZE);
        new BloomPushData(mode, threshold, softKnee, radius).write(push);
        VK10.vkCmdPushConstants(cmd, pipelineLayout, VK10.VK_SHADER_STAGE_COMPUTE_BIT, 0, push);
        VK10.vkCmdDispatch(cmd, (dst.width + 7) / 8, (dst.height + 7) / 8, 1);
        VulkanCommandEncoder.memoryBarrier(cmd, stack);
    }

    public void destroy() {
        if (destroyed) {
            return;
        }
        VkDevice vk = ctx.vk();
        VK10.vkDestroyPipeline(vk, pipeline, null);
        VK10.vkDestroySampler(vk, sampler, null);
        VK10.vkDestroyPipelineLayout(vk, pipelineLayout, null);
        VK10.vkDestroyDescriptorPool(vk, descriptorPool, null);
        VK10.vkDestroyDescriptorSetLayout(vk, descriptorSetLayout, null);
        destroyed = true;
    }

    private static long loadModule(VkDevice vk, MemoryStack stack) {
        byte[] bytes;
        try (InputStream input = RtBloomPipeline.class.getResourceAsStream(SHADER)) {
            if (input == null) {
                throw new IllegalStateException("missing SPIR-V resource: " + SHADER);
            }
            bytes = input.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("failed to read SPIR-V resource: " + SHADER, e);
        }
        ByteBuffer code = MemoryUtil.memAlloc(bytes.length).put(bytes);
        code.flip();
        try {
            VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType$Default().pCode(code);
            LongBuffer module = stack.mallocLong(1);
            check(VK10.vkCreateShaderModule(vk, moduleInfo, null, module),
                    "vkCreateShaderModule(bloom)");
            return module.get(0);
        } finally {
            MemoryUtil.memFree(code);
        }
    }
}
