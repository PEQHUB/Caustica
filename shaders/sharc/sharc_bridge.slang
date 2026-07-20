// Caustica-owned adapter for NVIDIA SHaRC 1.6.5.0.
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
    HashGridIndex cacheIndex = HashMapInsertEntry(sharcParameters.hashMapData,
            sharcHitData.positionWorld, sharcHitData.normalWorld, sharcParameters.gridParameters);
    float3 materialDemodulation = max(sharcHitData.materialDemodulation, float3(1.0e-3));
    SharcAddVoxelData(sharcParameters, cacheIndex, cacheableDirectLighting / materialDemodulation,
            float3(1.0), 1);

    float3 propagatedRadiance = propagatedDirectLighting;
#if SHARC_SEPARATE_EMISSIVE
    propagatedRadiance += sharcHitData.emissive;
#endif
    for (uint i = 0; i < sharcState.pathLength; ++i) {
        SharcAddVoxelData(sharcParameters, sharcState.cacheIndices[i], propagatedRadiance,
                sharcState.sampleWeights[i], 0);
    }
    for (uint i = sharcState.pathLength; i > 0; --i) {
        sharcState.cacheIndices[i] = sharcState.cacheIndices[i - 1];
        sharcState.sampleWeights[i] = sharcState.sampleWeights[i - 1];
    }
    sharcState.cacheIndices[0] = cacheIndex;
    sharcState.sampleWeights[0] = SharcSampleWeight(1.0 / materialDemodulation);
    sharcState.pathLength = min(++sharcState.pathLength, SHARC_PROPAGATION_DEPTH - 1);
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
    parameters.gridParameters.cameraPosition = cameraPosition;
    parameters.gridParameters.logarithmBase = SHARC_GRID_LOGARITHM_BASE;
    parameters.gridParameters.sceneScale = sceneScale;
    parameters.gridParameters.levelBias = float(SHARC_GRID_LEVEL_BIAS);
    parameters.hashMapData.capacity = capacity;
    parameters.hashMapData.hashEntriesBuffer = SharcRWPtr<uint64_t>(hashEntriesAddress);
    parameters.radianceScale = radianceScale;
    parameters.enableAntiFireflyFilter = antiFirefly;
    parameters.accumulationBuffer = SharcRWPtr<SharcAccumulationData>(accumulationAddress);
    parameters.resolvedBuffer = SharcRWPtr<SharcPackedData>(resolvedAddress);
    return parameters;
}

#endif
