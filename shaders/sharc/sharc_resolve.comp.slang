// SHaRC cache resolve. The NVIDIA algorithm comes from the externally pinned 1.6.5.0 headers; this
// Caustica-owned entry point only adapts its parameters to the renderer's BDA frame record.
#define SHARC_UPDATE 0
#define SHARC_QUERY 0
#define CAUSTICA_SHARC 0

import world_common;
#include "sharc_bridge.slang"

struct SharcResolvePush {
    uint64_t sharcFrameAddr;
};

[[vk::push_constant]] SharcResolvePush resolvePush;

[shader("compute")]
[numthreads(64, 1, 1)]
void main(uint3 id : SV_DispatchThreadID) {
    SharcFrame frame = ConstPtr<SharcFrame>(resolvePush.sharcFrameAddr)[0];
    if (id.x >= frame.capacity) {
        return;
    }

    SharcParameters parameters = causticaSharcParameters(
            frame.hashEntriesAddr,
            frame.accumulationAddr,
            frame.resolvedAddr,
            frame.capacity,
            frame.cameraPosition,
            frame.sceneScale,
            frame.radianceScale,
            (frame.flags & 1u) != 0u);
    SharcResolveParameters resolve;
    resolve.cameraPositionPrev = frame.cameraPositionPrev;
    resolve.accumulationFrameNum = frame.accumulationFrameNum;
    resolve.staleFrameNumMax = frame.staleFrameNumMax;
    resolve.frameIndex = frame.frameIndex;

    SharcRWPtr<uint> stats = SharcRWPtr<uint>(frame.statsAddr);
    if ((frame.flags & 2u) != 0u
            && parameters.hashMapData.hashEntriesBuffer[id.x] != HASH_GRID_INVALID_HASH_KEY) {
        InterlockedAdd(stats[8], 1u); // occupied entries observed by this resolve
        SharcAccumulationData accumulated = parameters.accumulationBuffer[id.x];
        SharcVoxelData previous = SharcUnpackVoxelData(parameters.resolvedBuffer[id.x]);
        uint staleLimit = clamp(resolve.staleFrameNumMax,
                SHARC_STALE_FRAME_NUM_MIN, SHARC_STALE_FRAME_NUM_MAX);
        if (accumulated.data.w == 0u && previous.staleFrameNum + 1u >= staleLimit) {
            InterlockedAdd(stats[11], 1u); // stale entries evicted by this resolve
        }
    }
    SharcResolveEntry(id.x, parameters, resolve);
}
