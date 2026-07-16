package dev.comfyfluffy.caustica.rt;

import java.nio.LongBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkQueryPoolCreateInfo;

import static dev.comfyfluffy.caustica.rt.RtContext.check;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR;
import static org.lwjgl.vulkan.VK10.*;

/** Non-blocking, six-frame-ring Vulkan timestamps for baseline and SHaRC GPU cost. */
public final class RtTraceGpuProfiler {
    private static final int RING = 6;
    private static final int QUERIES = 4;
    private final RtContext ctx;
    private final long pool;
    private final double nanosPerTick;
    private final boolean[] used = new boolean[RING];
    private final boolean[] sharc = new boolean[RING];
    private int slot = -1;
    private volatile long baselineTraceNanos;
    private volatile long sharcUpdateNanos;
    private volatile long sharcResolveNanos;
    private volatile long sharcQueryNanos;

    private RtTraceGpuProfiler(RtContext ctx, long pool, double nanosPerTick) {
        this.ctx = ctx; this.pool = pool; this.nanosPerTick = nanosPerTick;
    }

    public static RtTraceGpuProfiler create(RtContext ctx) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkQueryPoolCreateInfo info = VkQueryPoolCreateInfo.calloc(stack).sType$Default()
                    .queryType(VK_QUERY_TYPE_TIMESTAMP).queryCount(RING * QUERIES);
            LongBuffer p = stack.mallocLong(1);
            check(vkCreateQueryPool(ctx.vk(), info, null, p), "vkCreateQueryPool(trace timestamps)");
            VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(ctx.vk().getPhysicalDevice(), props);
            return new RtTraceGpuProfiler(ctx, p.get(0), props.limits().timestampPeriod());
        }
    }

    public void begin(VkCommandBuffer cmd, boolean isSharc) {
        slot = (slot + 1) % RING;
        readCompleted(slot);
        int base = slot * QUERIES;
        vkCmdResetQueryPool(cmd, pool, base, QUERIES);
        used[slot] = true;
        sharc[slot] = isSharc;
        vkCmdWriteTimestamp(cmd, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, pool, base);
    }

    public void updateEnd(VkCommandBuffer cmd) { write(cmd, 1, VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR); }
    public void resolveEnd(VkCommandBuffer cmd) { write(cmd, 2, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT); }
    public void queryEnd(VkCommandBuffer cmd) { write(cmd, 3, VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR); }
    public void baselineEnd(VkCommandBuffer cmd) { write(cmd, 1, VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR); }

    private void write(VkCommandBuffer cmd, int offset, int stage) {
        vkCmdWriteTimestamp(cmd, stage, pool, slot * QUERIES + offset);
    }

    private void readCompleted(int readSlot) {
        if (!used[readSlot]) return;
        int count = sharc[readSlot] ? 4 : 2;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer values = stack.mallocLong(count);
            int result = vkGetQueryPoolResults(ctx.vk(), pool, readSlot * QUERIES, count, values,
                    Long.BYTES, VK_QUERY_RESULT_64_BIT);
            if (result != VK_SUCCESS) return; // never wait on the render thread
            if (sharc[readSlot]) {
                sharcUpdateNanos = elapsed(values.get(0), values.get(1));
                sharcResolveNanos = elapsed(values.get(1), values.get(2));
                sharcQueryNanos = elapsed(values.get(2), values.get(3));
                RtFrameStats.FRAME.count("sharcUpdateGpuNanos", sharcUpdateNanos);
                RtFrameStats.FRAME.count("sharcResolveGpuNanos", sharcResolveNanos);
                RtFrameStats.FRAME.count("sharcQueryGpuNanos", sharcQueryNanos);
            } else {
                baselineTraceNanos = elapsed(values.get(0), values.get(1));
                RtFrameStats.FRAME.count("baselineTraceGpuNanos", baselineTraceNanos);
            }
        }
    }

    private long elapsed(long start, long end) {
        return Math.max(0L, Math.round((end - start) * nanosPerTick));
    }

    public long baselineTraceNanos() { return baselineTraceNanos; }
    public long sharcUpdateNanos() { return sharcUpdateNanos; }
    public long sharcResolveNanos() { return sharcResolveNanos; }
    public long sharcQueryNanos() { return sharcQueryNanos; }

    public void destroy() { vkDestroyQueryPool(ctx.vk(), pool, null); }
}
