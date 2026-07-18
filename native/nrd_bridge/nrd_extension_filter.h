#pragma once

#include <string_view>

namespace nrdbridge {

// NRD records compute dispatches only. NRI's Vulkan wrapper otherwise treats every extension name
// supplied here as an instruction to resolve that extension's complete dispatch table. In particular,
// NRI 179 resolves vkCmdTraceRaysIndirect2KHR whenever VK_KHR_ray_tracing_pipeline is listed, even when
// VK_KHR_ray_tracing_maintenance1 was not enabled on the wrapped device. Keep ray-tracing ownership in
// Caustica and expose only the non-ray-tracing device surface to NRD's private NRI wrapper.
constexpr bool isRayTracingOnlyDeviceExtension(std::string_view extension) noexcept {
    return extension == "VK_KHR_acceleration_structure"
            || extension == "VK_KHR_ray_query"
            || extension == "VK_KHR_ray_tracing_maintenance1"
            || extension == "VK_KHR_ray_tracing_pipeline"
            || extension == "VK_KHR_ray_tracing_position_fetch"
            || extension == "VK_EXT_opacity_micromap"
            || extension == "VK_EXT_ray_tracing_invocation_reorder"
            || extension == "VK_NV_ray_tracing_invocation_reorder";
}

} // namespace nrdbridge
