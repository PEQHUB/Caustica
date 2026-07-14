package dev.comfyfluffy.caustica.rt.accel;

import org.lwjgl.util.vma.Vma;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;

/**
 * A VMA-backed image + view, created in {@code VK_IMAGE_LAYOUT_GENERAL}. Used for RT output
 * storage images. Created via {@link dev.comfyfluffy.caustica.rt.RtContext#createStorageImage}; freed with {@link #destroy()}.
 */
public final class RtImage {
    public final long image;
    public final long allocation;
    /** VkDeviceMemory backing this image. Distinct from the VMA allocation handle above. */
    public final long memory;
    public final long view;
    public final int width;
    public final int height;
    public final int format;
    public final int usage;

    private final long vma;
    private final VkDevice vk;
    private boolean destroyed;

    public RtImage(long vma, VkDevice vk, long image, long allocation, long memory, long view, int width, int height, int format,
            int usage) {
        this.vma = vma;
        this.vk = vk;
        this.image = image;
        this.allocation = allocation;
        this.memory = memory;
        this.view = view;
        this.width = width;
        this.height = height;
        this.format = format;
        this.usage = usage;
    }

    public void destroy() {
        if (destroyed) {
            return;
        }
        if (view != 0L) {
            VK10.vkDestroyImageView(vk, view, null);
        }
        if (image != 0L) {
            Vma.vmaDestroyImage(vma, image, allocation);
        }
        destroyed = true;
    }
}
