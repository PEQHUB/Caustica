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

/** Ten core tags, plus optional particle, diffuse, and/or atomic three-tag transparency resources. */
inline bool isSupportedDlssdResourceCount(uint32_t resourceCount) noexcept {
    return resourceCount >= 10 && resourceCount <= 15;
}

inline bool dlssdResourceCountRequiresDiffusePath(uint32_t resourceCount) noexcept {
    return resourceCount == 11 || resourceCount == 14;
}

inline bool dlssdResourceCountRequiresTransparencyLayer(uint32_t resourceCount) noexcept {
    return resourceCount == 13 || resourceCount == 14;
}

inline bool hasExpectedDlssdOptionalResources(uint32_t resourceCount, bool hasParticleHint,
        bool hasDiffusePath,
        bool hasColorBeforeTransparency, bool hasTransparencyLayer,
        bool hasTransparencyLayerOpacity) noexcept {
    if (!isSupportedDlssdResourceCount(resourceCount)) {
        return false;
    }
    const bool hasAnyTransparencyLayer = hasColorBeforeTransparency
            || hasTransparencyLayer || hasTransparencyLayerOpacity;
    const bool hasCompleteTransparencyLayer = hasColorBeforeTransparency
            && hasTransparencyLayer && hasTransparencyLayerOpacity;
    const uint32_t expectedCount = 10u + (hasParticleHint ? 1u : 0u)
            + (hasDiffusePath ? 1u : 0u) + (hasCompleteTransparencyLayer ? 3u : 0u);
    return resourceCount == expectedCount
            && (!hasAnyTransparencyLayer || hasCompleteTransparencyLayer);
}

} // namespace slbridge::detail
