// Build-only contract probe for the native 64-bit-atomic BDA path used by SHaRC update.
#define SHARC_UPDATE 1
#define SHARC_QUERY 0
#include "sharc_bridge.slang"

struct SharcAtomicProbePush {
    uint64_t hashEntriesAddress;
    uint64_t accumulationAddress;
    uint64_t resolvedAddress;
    uint capacity;
};

[[vk::push_constant]] SharcAtomicProbePush probe;

[shader("compute")]
[numthreads(1, 1, 1)]
void main(uint3 id : SV_DispatchThreadID) {
    if (id.x >= probe.capacity) {
        return;
    }
    SharcParameters parameters = causticaSharcParameters(
            probe.hashEntriesAddress,
            probe.accumulationAddress,
            probe.resolvedAddress,
            probe.capacity,
            float3(0.0, 0.0, 0.0),
            1.0,
            1000.0,
            false);
    SharcState state;
    SharcInit(state);
    SharcHitData hit;
    hit.positionWorld = float3(float(id.x), 0.0, 0.0);
    hit.normalWorld = float3(0.0, 1.0, 0.0);
#if SHARC_MATERIAL_DEMODULATION
    hit.materialDemodulation = float3(1.0, 1.0, 1.0);
#endif
#if SHARC_SEPARATE_EMISSIVE
    hit.emissive = float3(0.0, 0.0, 0.0);
#endif
    SharcUpdateHit(parameters, state, hit, float3(0.0, 0.0, 0.0), 0.5);
}
