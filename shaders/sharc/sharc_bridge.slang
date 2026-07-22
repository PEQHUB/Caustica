// Caustica-owned adapter for NVIDIA SHaRC.
//
// Migrated to 1.8.0 API with the 1.7.2 path-length correction: pathLength can now reach the
// full SHARC_PROPAGATION_DEPTH rather than being capped at depth - 1. This ensures the
// last-vertex contribution is not missed during cache propagation.
//
// The 1.8 API adds directional SH encoding for glossy contamination reduction. Direction and
// directionality-weight arrays are shifted in lockstep with cache indices and sample weights.
//
// The NVIDIA SDK headers are an external build input selected with SHARC_SDK. They are intentionally
// not copied into Caustica's LGPL source tree. The build adds SHARC_SDK/include to Slang's include path
// only for the separately compiled SHaRC shader family.
//
// This software contains source code provided by NVIDIA Corporation.

#ifndef CAUSTICA_SHARC_BRIDGE
#define CAUSTICA_SHARC_BRIDGE 1

#ifndef SHARC_UPDATE
#define SHARC_UPDATE 0
#endif

#ifndef SHARC_QUERY
#define SHARC_QUERY 0
#endif

#define SHARC_ENABLE_GLSL 0
#define SHARC_ENABLE_64_BIT_ATOMICS 1
#define SHARC_PROPAGATION_DEPTH 4
#define SHARC_MATERIAL_DEMODULATION 1
#define SHARC_SEPARATE_EMISSIVE 1

typealias SharcRWPtr<T> = Ptr<T, Access.ReadWrite, AddressSpace.Device>;

// SHaRC normally declares HLSL RWStructuredBuffer fields. Caustica keeps the cache out of the world
// descriptor set and passes buffer-device addresses instead, so adapt those fields to device pointers.
#define RW_STRUCTURED_BUFFER(name, type) SharcRWPtr<type> name
#define BUFFER_AT_OFFSET(name, offset) name[offset]

#include "SharcCommon.h"

// Directionality weight for a surface event. Diffuse surfaces return 0 (omnidirectional),
// delta surfaces return 1 (fully directional), glossy surfaces return smoothness^2.
float causticaSharcDirectionalityWeight(
        bool isDelta,
        bool isDiffuse,
        float perceptualRoughness) {
    if (isDiffuse) return 0.0;
    if (isDelta) return 1.0;
    float smoothness = saturate(1.0 - perceptualRoughness);
    return smoothness * smoothness;
}

// The stock entry point uses one direct-lighting value both for the current directionless cache entry
// and for propagation to older vertices. Caustica's PBR direct term also contains view-dependent GGX
// and SSS, which must contribute to older diffuse vertices but must never be stored as albedo-demodulated
// directionless radiance at the current vertex. Keep the SDK data structures and accumulation rules, but
// split those two meanings explicitly.
bool causticaSharcUpdateHit(
        in SharcParameters sharcParameters,
        inout SharcState sharcState,
        SharcHitData sharcHitData,
        float3 cacheableDirectLighting,
        float3 propagatedDirectLighting) {
#if SHARC_UPDATE
    HashGridIndex cacheIndex = HashGridInsertEntry(sharcParameters.hashGridData,
            sharcHitData.positionWorld, sharcHitData.normalWorld, sharcParameters.hashGridParameters);
    float3 materialDemodulation = max(sharcHitData.materialDemodulation, float3(1.0e-3));
    SharcAddVoxelData(sharcParameters, cacheIndex, cacheableDirectLighting / materialDemodulation,
            float3(1.0), 1,
            sharcHitData.radianceDirectionWorld, sharcHitData.radianceDirectionWeight);

    float3 propagatedRadiance = propagatedDirectLighting;
#if SHARC_SEPARATE_EMISSIVE
    propagatedRadiance += sharcHitData.emissive;
#endif
    for (uint i = 0; i < sharcState.pathLength; ++i) {
        SharcAddVoxelData(sharcParameters, sharcState.cacheIndices[i], propagatedRadiance,
                sharcState.sampleWeights[i], 0,
                sharcState.radianceDirections[i], sharcState.radianceDirectionWeights[i]);
    }
    // Preserve only the number of existing entries that can fit after
    // inserting the new vertex at index zero.
    uint shiftCount =
        min(sharcState.pathLength, uint(SHARC_PROPAGATION_DEPTH - 1));

    for (uint i = shiftCount; i > 0; --i) {
        sharcState.cacheIndices[i] = sharcState.cacheIndices[i - 1];
        sharcState.sampleWeights[i] = sharcState.sampleWeights[i - 1];
#if SHARC_ENABLE_SH_ENCODING
        sharcState.radianceDirections[i] = sharcState.radianceDirections[i - 1];
        sharcState.radianceDirectionWeights[i] = sharcState.radianceDirectionWeights[i - 1];
#endif
    }

    sharcState.cacheIndices[0] = cacheIndex;
    sharcState.sampleWeights[0] = SharcSampleWeight(1.0 / materialDemodulation);
#if SHARC_ENABLE_SH_ENCODING
    sharcState.radianceDirections[0] = sharcHitData.radianceDirectionWorld;
    sharcState.radianceDirectionWeights[0] = sharcHitData.radianceDirectionWeight;
#endif

    // Unlike the old adapter, pathLength can reach the complete
    // configured propagation depth.
    sharcState.pathLength =
        min(sharcState.pathLength + 1, uint(SHARC_PROPAGATION_DEPTH));
#endif
    return true;
}

// A sampled specular continuation is valid transport for older diffuse cache vertices, but the current
// directionless voxel must not accumulate that view-dependent continuation on the next hit.
void causticaSharcDiscardCurrentVertex(inout SharcState sharcState) {
#if SHARC_UPDATE
    if (sharcState.pathLength == 0) return;
    for (uint i = 0; i + 1 < sharcState.pathLength; ++i) {
        sharcState.cacheIndices[i] = sharcState.cacheIndices[i + 1];
        sharcState.sampleWeights[i] = sharcState.sampleWeights[i + 1];
#if SHARC_ENABLE_SH_ENCODING
        sharcState.radianceDirections[i] = sharcState.radianceDirections[i + 1];
        sharcState.radianceDirectionWeights[i] = sharcState.radianceDirectionWeights[i + 1];
#endif
    }
    sharcState.pathLength--;
#endif
}

SharcParameters causticaSharcParameters(
        uint64_t hashEntriesAddress,
        uint64_t accumulationAddress,
        uint64_t resolvedAddress,
        uint capacity,
        float3 cameraPosition,
        float sceneScale,
        float radianceScale,
        bool antiFirefly) {
    SharcParameters parameters;
    parameters.hashGridParameters.cameraPosition = cameraPosition;
    parameters.hashGridParameters.logarithmBase = SHARC_GRID_LOGARITHM_BASE;
    parameters.hashGridParameters.sceneScale = sceneScale;
    parameters.hashGridParameters.levelBias = float(SHARC_GRID_LEVEL_BIAS);
    parameters.hashGridData.capacity = capacity;
    parameters.hashGridData.hashEntriesBuffer = SharcRWPtr<uint64_t>(hashEntriesAddress);
    parameters.radianceScale = radianceScale;
    parameters.enableAntiFireflyFilter = antiFirefly;
    parameters.accumulationBuffer = SharcRWPtr<SharcAccumulationData>(accumulationAddress);
    parameters.resolvedBuffer = SharcRWPtr<SharcPackedData>(resolvedAddress);
    return parameters;
}

#endif
