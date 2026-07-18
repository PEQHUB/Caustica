#include <NRI.h>
#include <Extensions/NRIHelper.h>
#include <Extensions/NRIRayTracing.h>
#include <Extensions/NRIWrapperVK.h>
#include <NRD.h>
#include <NRDSettings.h>
#include <NRDIntegration.h>
#include <NRDIntegration.hpp>

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <exception>
#include <memory>
#include <string>

#if defined(_WIN32)
#define NRDBRIDGE_API extern "C" __declspec(dllexport)
#else
#define NRDBRIDGE_API extern "C" __attribute__((visibility("default")))
#endif

namespace {
constexpr nrd::Identifier kDenoiserId = 1;

struct BridgeResource {
    uint64_t image;
    int32_t format;
    int32_t slot;
};

struct BridgeCommonSettings {
    float viewToClip[16];
    float viewToClipPrev[16];
    float worldToView[16];
    float worldToViewPrev[16];
    float motionVectorScale[3];
    float cameraJitter[2];
    float cameraJitterPrev[2];
    float timeDeltaMs;
    float denoisingRange;
    float disocclusionThreshold;
    float splitScreen;
    uint32_t frameIndex;
    uint32_t resetHistory;
};

struct BridgeSettings {
    uint32_t maxAccumulatedFrames;
    uint32_t maxFastAccumulatedFrames;
    uint32_t historyFixFrames;
    uint32_t historyFixStride;
    uint32_t relaxAtrousIterations;
    uint32_t antiFirefly;
    uint32_t antilag;
    float prepassBlurRadius;
    float minBlurRadius;
    float maxBlurRadius;
    float lobeAngleFraction;
    float roughnessFraction;
    float planeDistanceSensitivity;
    float hitDistanceA;
    float hitDistanceB;
    float hitDistanceC;
    float antilagSigma;
};

static_assert(sizeof(BridgeResource) == 16);
static_assert(sizeof(BridgeCommonSettings) == 308);
static_assert(sizeof(BridgeSettings) == 68);

struct State {
    nrd::Integration integration;
    nrd::Denoiser denoiser = nrd::Denoiser::REBLUR_DIFFUSE;
    uint16_t width = 0;
    uint16_t height = 0;
    bool sphericalHarmonics = false;
};

std::unique_ptr<State> g_state;
thread_local std::string g_error;

void setError(const char* operation, const char* detail = nullptr) {
    g_error = operation;
    if (detail && *detail) {
        g_error += ": ";
        g_error += detail;
    }
}

nrd::Denoiser selectDenoiser(int32_t family, bool sh) {
    if (family == 1)
        return sh ? nrd::Denoiser::RELAX_DIFFUSE_SH : nrd::Denoiser::RELAX_DIFFUSE;
    return sh ? nrd::Denoiser::REBLUR_DIFFUSE_SH : nrd::Denoiser::REBLUR_DIFFUSE;
}

nrd::ResourceType slotType(int32_t slot) {
    switch (slot) {
        case 0: return nrd::ResourceType::IN_MV;
        case 1: return nrd::ResourceType::IN_NORMAL_ROUGHNESS;
        case 2: return nrd::ResourceType::IN_VIEWZ;
        case 3: return nrd::ResourceType::IN_DIFF_RADIANCE_HITDIST;
        case 4: return nrd::ResourceType::OUT_DIFF_RADIANCE_HITDIST;
        case 5: return nrd::ResourceType::IN_DIFF_SH0;
        case 6: return nrd::ResourceType::IN_DIFF_SH1;
        case 7: return nrd::ResourceType::OUT_DIFF_SH0;
        case 8: return nrd::ResourceType::OUT_DIFF_SH1;
        default: return nrd::ResourceType::MAX_NUM;
    }
}

void copyMatrix(float (&destination)[16], const float (&source)[16]) {
    std::memcpy(destination, source, sizeof(destination));
}
}

NRDBRIDGE_API const char* nrdbridge_version() {
    return "NRD 4.17.3 / integration 22";
}

NRDBRIDGE_API const char* nrdbridge_last_error() {
    return g_error.c_str();
}

NRDBRIDGE_API int32_t nrdbridge_create(uint64_t instance, uint64_t physicalDevice, uint64_t device,
        uint32_t graphicsQueueFamily, uint32_t width, uint32_t height, int32_t family, int32_t sh) {
    try {
        g_error.clear();
        if (!instance || !physicalDevice || !device || width == 0 || height == 0 || width > 65535 || height > 65535) {
            setError("nrdbridge_create", "invalid Vulkan handles or dimensions");
            return -1;
        }

        auto state = std::make_unique<State>();
        state->width = static_cast<uint16_t>(width);
        state->height = static_cast<uint16_t>(height);
        state->sphericalHarmonics = sh != 0;
        state->denoiser = selectDenoiser(family, state->sphericalHarmonics);

        const nrd::LibraryDesc& libraryDesc = *nrd::GetLibraryDesc();
        nri::QueueFamilyVKDesc queueFamily = {};
        queueFamily.queueNum = 1;
        queueFamily.queueType = nri::QueueType::GRAPHICS;
        queueFamily.familyIndex = graphicsQueueFamily;

        nri::DeviceCreationVKDesc deviceDesc = {};
        deviceDesc.vkBindingOffsets.sRegister = libraryDesc.spirvBindingOffsets.samplerOffset;
        deviceDesc.vkBindingOffsets.tRegister = libraryDesc.spirvBindingOffsets.textureOffset;
        deviceDesc.vkBindingOffsets.bRegister = libraryDesc.spirvBindingOffsets.constantBufferOffset;
        deviceDesc.vkBindingOffsets.uRegister = libraryDesc.spirvBindingOffsets.storageTextureAndBufferOffset;
        deviceDesc.vkInstance = reinterpret_cast<void*>(instance);
        deviceDesc.vkPhysicalDevice = reinterpret_cast<void*>(physicalDevice);
        deviceDesc.vkDevice = reinterpret_cast<void*>(device);
        deviceDesc.queueFamilies = &queueFamily;
        deviceDesc.queueFamilyNum = 1;
        deviceDesc.minorVersion = 2;

        nrd::DenoiserDesc denoiserDesc = {kDenoiserId, state->denoiser};
        nrd::InstanceCreationDesc instanceDesc = {};
        instanceDesc.denoisers = &denoiserDesc;
        instanceDesc.denoisersNum = 1;

        nrd::IntegrationCreationDesc integrationDesc = {};
        std::snprintf(integrationDesc.name, sizeof(integrationDesc.name), "%s", "Caustica NRD");
        integrationDesc.resourceWidth = state->width;
        integrationDesc.resourceHeight = state->height;
        integrationDesc.queuedFrameNum = 3;
        integrationDesc.autoWaitForIdle = false;
        integrationDesc.enableWholeLifetimeDescriptorCaching = false;

        if (state->integration.RecreateVK(integrationDesc, instanceDesc, deviceDesc) != nrd::Result::SUCCESS) {
            setError("NRD Integration::RecreateVK failed");
            return -2;
        }
        g_state = std::move(state);
        return 0;
    } catch (const std::exception& e) {
        setError("nrdbridge_create", e.what());
        return -3;
    } catch (...) {
        setError("nrdbridge_create", "unknown exception");
        return -4;
    }
}

NRDBRIDGE_API int32_t nrdbridge_evaluate(uint64_t commandBuffer, const BridgeResource* resources,
        uint32_t resourceCount, const BridgeCommonSettings* bridgeCommon, const BridgeSettings* bridgeSettings) {
    try {
        g_error.clear();
        if (!g_state || !commandBuffer || !resources || !bridgeCommon || !bridgeSettings) {
            setError("nrdbridge_evaluate", "bridge is not initialized or an argument is null");
            return -1;
        }

        // NRD's integration contract starts a frame before accepting any per-frame settings.
        g_state->integration.NewFrame();

        nrd::CommonSettings common = {};
        copyMatrix(common.viewToClipMatrix, bridgeCommon->viewToClip);
        copyMatrix(common.viewToClipMatrixPrev, bridgeCommon->viewToClipPrev);
        copyMatrix(common.worldToViewMatrix, bridgeCommon->worldToView);
        copyMatrix(common.worldToViewMatrixPrev, bridgeCommon->worldToViewPrev);
        std::copy_n(bridgeCommon->motionVectorScale, 3, common.motionVectorScale);
        std::copy_n(bridgeCommon->cameraJitter, 2, common.cameraJitter);
        std::copy_n(bridgeCommon->cameraJitterPrev, 2, common.cameraJitterPrev);
        common.resourceSize[0] = g_state->width;
        common.resourceSize[1] = g_state->height;
        common.resourceSizePrev[0] = g_state->width;
        common.resourceSizePrev[1] = g_state->height;
        common.rectSize[0] = g_state->width;
        common.rectSize[1] = g_state->height;
        common.rectSizePrev[0] = g_state->width;
        common.rectSizePrev[1] = g_state->height;
        common.timeDeltaBetweenFrames = bridgeCommon->timeDeltaMs;
        common.denoisingRange = bridgeCommon->denoisingRange;
        common.disocclusionThreshold = bridgeCommon->disocclusionThreshold;
        common.splitScreen = bridgeCommon->splitScreen;
        common.frameIndex = bridgeCommon->frameIndex;
        common.accumulationMode = bridgeCommon->resetHistory
                ? nrd::AccumulationMode::CLEAR_AND_RESTART : nrd::AccumulationMode::CONTINUE;
        if (g_state->integration.SetCommonSettings(common) != nrd::Result::SUCCESS) {
            setError("NRD SetCommonSettings failed");
            return -2;
        }

        if (g_state->denoiser == nrd::Denoiser::REBLUR_DIFFUSE
                || g_state->denoiser == nrd::Denoiser::REBLUR_DIFFUSE_SH) {
            nrd::ReblurSettings settings = {};
            settings.maxAccumulatedFrameNum = std::min(bridgeSettings->maxAccumulatedFrames, nrd::REBLUR_MAX_HISTORY_FRAME_NUM);
            settings.maxFastAccumulatedFrameNum = std::min(bridgeSettings->maxFastAccumulatedFrames, settings.maxAccumulatedFrameNum);
            settings.historyFixFrameNum = std::min(bridgeSettings->historyFixFrames, 3u);
            settings.historyFixBasePixelStride = bridgeSettings->historyFixStride;
            settings.historyFixAlternatePixelStride = bridgeSettings->historyFixStride;
            settings.diffusePrepassBlurRadius = bridgeSettings->prepassBlurRadius;
            settings.minBlurRadius = bridgeSettings->minBlurRadius;
            settings.maxBlurRadius = bridgeSettings->maxBlurRadius;
            settings.lobeAngleFraction = bridgeSettings->lobeAngleFraction;
            settings.roughnessFraction = bridgeSettings->roughnessFraction;
            settings.planeDistanceSensitivity = bridgeSettings->planeDistanceSensitivity;
            settings.hitDistanceParameters.A = bridgeSettings->hitDistanceA;
            settings.hitDistanceParameters.B = bridgeSettings->hitDistanceB;
            settings.hitDistanceParameters.C = bridgeSettings->hitDistanceC;
            settings.enableAntiFirefly = bridgeSettings->antiFirefly != 0;
            settings.antilagSettings.luminanceSigmaScale = bridgeSettings->antilag
                    ? bridgeSettings->antilagSigma : 1.0e6f;
            if (g_state->integration.SetDenoiserSettings(kDenoiserId, &settings) != nrd::Result::SUCCESS) {
                setError("NRD SetDenoiserSettings(REBLUR) failed");
                return -3;
            }
        } else {
            nrd::RelaxSettings settings = {};
            settings.diffuseMaxAccumulatedFrameNum = bridgeSettings->maxAccumulatedFrames;
            settings.diffuseMaxFastAccumulatedFrameNum = std::min(
                    bridgeSettings->maxFastAccumulatedFrames, bridgeSettings->maxAccumulatedFrames);
            settings.historyFixFrameNum = std::min(bridgeSettings->historyFixFrames, 3u);
            settings.historyFixBasePixelStride = bridgeSettings->historyFixStride;
            settings.historyFixAlternatePixelStride = bridgeSettings->historyFixStride;
            settings.diffusePrepassBlurRadius = bridgeSettings->prepassBlurRadius;
            settings.lobeAngleFraction = bridgeSettings->lobeAngleFraction;
            settings.roughnessFraction = bridgeSettings->roughnessFraction;
            settings.atrousIterationNum = std::clamp(bridgeSettings->relaxAtrousIterations, 2u, 8u);
            settings.enableAntiFirefly = bridgeSettings->antiFirefly != 0;
            if (!bridgeSettings->antilag) {
                settings.antilagSettings.accelerationAmount = 0.0f;
                settings.antilagSettings.resetAmount = 0.0f;
            } else {
                settings.antilagSettings.spatialSigmaScale = bridgeSettings->antilagSigma;
            }
            if (g_state->integration.SetDenoiserSettings(kDenoiserId, &settings) != nrd::Result::SUCCESS) {
                setError("NRD SetDenoiserSettings(RELAX) failed");
                return -4;
            }
        }

        nrd::ResourceSnapshot snapshot;
        snapshot.restoreInitialState = true;
        for (uint32_t i = 0; i < resourceCount; ++i) {
            const nrd::ResourceType type = slotType(resources[i].slot);
            if (type == nrd::ResourceType::MAX_NUM || resources[i].image == 0)
                continue;
            nrd::Resource resource = {};
            resource.vk.image = resources[i].image;
            resource.vk.format = resources[i].format;
            resource.state.access = nri::AccessBits::SHADER_RESOURCE_STORAGE;
            resource.state.layout = nri::Layout::GENERAL;
            resource.state.stages = nri::StageBits::ALL;
            snapshot.SetResource(type, resource);
        }

        const nrd::Identifier denoisers[] = {kDenoiserId};
        nri::CommandBufferVKDesc commandDesc = {};
        commandDesc.vkCommandBuffer = reinterpret_cast<void*>(commandBuffer);
        commandDesc.queueType = nri::QueueType::GRAPHICS;
        g_state->integration.DenoiseVK(denoisers, 1, commandDesc, snapshot);
        return 0;
    } catch (const std::exception& e) {
        setError("nrdbridge_evaluate", e.what());
        return -5;
    } catch (...) {
        setError("nrdbridge_evaluate", "unknown exception");
        return -6;
    }
}

NRDBRIDGE_API void nrdbridge_destroy() {
    g_state.reset();
    g_error.clear();
}
