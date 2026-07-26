#pragma once

#include "streamline_bridge.h"

namespace slbridge::detail {

/** Official Vulkan texture tags require VkImage + VkImageView, not VkDeviceMemory ownership. */
inline bool isCompleteDlssdVulkanTexture(const slbridge_resource_desc& descriptor) noexcept {
    return descriptor.valid != 0
            && descriptor.image != 0
            && descriptor.view != 0
            && descriptor.width != 0
            && descriptor.height != 0;
}

/** Ten core tags plus independently optional guides and a supported transparency overlay encoding. */
inline bool isSupportedDlssdResourceCount(uint32_t resourceCount) noexcept {
    return resourceCount >= 10 && resourceCount <= 15;
}

inline bool hasExpectedDlssdOptionalResources(uint32_t resourceCount, bool hasParticleHint,
        bool hasDiffusePath,
        bool hasColorBeforeTransparency, bool hasTransparencyLayer,
        bool hasTransparencyLayerOpacity) noexcept {
    if (!isSupportedDlssdResourceCount(resourceCount)) {
        return false;
    }
    const uint32_t expectedCount = 10u + (hasParticleHint ? 1u : 0u)
            + (hasDiffusePath ? 1u : 0u)
            + (hasColorBeforeTransparency ? 1u : 0u)
            + (hasTransparencyLayer ? 1u : 0u)
            + (hasTransparencyLayerOpacity ? 1u : 0u);
    return resourceCount == expectedCount
            && (!hasTransparencyLayerOpacity || hasTransparencyLayer);
}

} // namespace slbridge::detail
