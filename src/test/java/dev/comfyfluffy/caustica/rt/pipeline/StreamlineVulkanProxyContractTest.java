package dev.comfyfluffy.caustica.rt.pipeline;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prevents a partial Streamline proxy chain from reaching swapchain creation with no registered device. */
final class StreamlineVulkanProxyContractTest {
    @Test
    void instanceDeviceAndSwapchainAllUseTheSameProxyChain() throws Exception {
        String instance = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/mixin/VulkanInstanceMixin.java"));
        String backend = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/mixin/VulkanBackendMixin.java"));
        String surface = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/mixin/VulkanGpuSurfaceMixin.java"));

        assertTrue(instance.contains("StreamlineRuntime.initializeForVulkan()"));
        assertTrue(instance.contains("StreamlineRuntime.vkCreateInstance(createInfo, allocator, instanceOut)"));
        assertTrue(backend.contains("StreamlineRuntime.vkEnumeratePhysicalDevices(instance, count, physicalDevices)"));
        assertTrue(backend.contains("StreamlineRuntime.vkCreateDevice(physicalDevice, createInfo, allocator, deviceOut)"));
        assertTrue(surface.contains("StreamlineRuntime.vkCreateWin32Surface(instance, window, allocator, surfaceOut)"));
        assertTrue(surface.contains("StreamlineRuntime.vkDestroySurface(instance, surface, allocator)"));
        assertTrue(surface.contains("StreamlineRuntime.vkCreateSwapchain(device, pCreateInfo, pAllocator, pSwapchain)"));
        assertTrue(surface.contains("StreamlineRuntime.vkDestroySwapchain(device, swapchain, allocator)"));
        assertTrue(surface.contains("StreamlineRuntime.vkGetSwapchainImages(device, swapchain, count, images)"));
        assertTrue(surface.contains("StreamlineRuntime.vkAcquireNextImage(device, swapchain, timeout"));
        assertTrue(surface.contains("StreamlineRuntime.vkQueuePresent(queue, presentInfo)"));
    }
}
