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

/** Ten core tags, plus the optional diffuse guide and/or the atomic three-tag transparency layer. */
inline bool isSupportedDlssdResourceCount(uint32_t resourceCount) noexcept {
    return resourceCount == 10 || resourceCount == 11
            || resourceCount == 13 || resourceCount == 14;
}

inline bool dlssdResourceCountRequiresDiffusePath(uint32_t resourceCount) noexcept {
    return resourceCount == 11 || resourceCount == 14;
}

inline bool dlssdResourceCountRequiresTransparencyLayer(uint32_t resourceCount) noexcept {
    return resourceCount == 13 || resourceCount == 14;
}

inline bool hasExpectedDlssdOptionalResources(uint32_t resourceCount, bool hasDiffusePath,
        bool hasColorBeforeTransparency, bool hasTransparencyLayer,
        bool hasTransparencyLayerOpacity) noexcept {
    if (!isSupportedDlssdResourceCount(resourceCount)) {
        return false;
    }
    const bool hasAnyTransparencyLayer = hasColorBeforeTransparency
            || hasTransparencyLayer || hasTransparencyLayerOpacity;
    const bool hasCompleteTransparencyLayer = hasColorBeforeTransparency
            && hasTransparencyLayer && hasTransparencyLayerOpacity;
    const bool requiresTransparencyLayer = dlssdResourceCountRequiresTransparencyLayer(resourceCount);
    return hasDiffusePath == dlssdResourceCountRequiresDiffusePath(resourceCount)
            && hasAnyTransparencyLayer == requiresTransparencyLayer
            && (!requiresTransparencyLayer || hasCompleteTransparencyLayer);
}

} // namespace slbridge::detail
