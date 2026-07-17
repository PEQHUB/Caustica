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

/** Ten core tags, plus at most the optional combined diffuse direction/hit-distance guide. */
inline bool isSupportedDlssdResourceCount(uint32_t resourceCount) noexcept {
    return resourceCount == 10 || resourceCount == 11;
}

} // namespace slbridge::detail
