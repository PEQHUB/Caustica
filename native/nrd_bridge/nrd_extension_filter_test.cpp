#include "nrd_extension_filter.h"

#include <cassert>

int main() {
    using nrdbridge::isRayTracingOnlyDeviceExtension;

    assert(isRayTracingOnlyDeviceExtension("VK_KHR_ray_tracing_pipeline"));
    assert(isRayTracingOnlyDeviceExtension("VK_KHR_ray_tracing_maintenance1"));
    assert(isRayTracingOnlyDeviceExtension("VK_EXT_opacity_micromap"));

    assert(!isRayTracingOnlyDeviceExtension("VK_EXT_extended_dynamic_state"));
    assert(!isRayTracingOnlyDeviceExtension("VK_KHR_push_descriptor"));
    assert(!isRayTracingOnlyDeviceExtension("VK_KHR_synchronization2"));
    return 0;
}
