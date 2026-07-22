// SHaRC cache resolve. Migrated to 1.8.0 API: uses hashGridData, SharcGetAccumulatedSampleNum,
// responsiveFrameNum, and the dual RGB/SH accumulation layout helpers.
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
            frame.radianceScale);
    SharcResolveParameters resolve;
    resolve.cameraPositionPrev = frame.cameraPositionPrev;
    resolve.accumulationFrameNum = frame.accumulationFrameNum;
    resolve.responsiveFrameNum = 1u;
    resolve.staleFrameNumMax = frame.staleFrameNumMax;
    resolve.frameIndex = frame.frameIndex;

    SharcRWPtr<uint> stats = SharcRWPtr<uint>(frame.statsAddr);
    if ((frame.flags & 2u) != 0u
            && parameters.hashGridData.hashEntriesBuffer[id.x] != HASH_GRID_INVALID_HASH_KEY) {
        InterlockedAdd(stats[8], 1u);
        SharcAccumulationData accumulated = parameters.accumulationBuffer[id.x];
        SharcVoxelData previous = SharcUnpackVoxelData(parameters.resolvedBuffer[id.x]);
        uint staleLimit = clamp(resolve.staleFrameNumMax,
                SHARC_STALE_FRAME_NUM_MIN, SHARC_STALE_FRAME_NUM_MAX);
        float sampleNum = SharcGetAccumulatedSampleNum(accumulated);
        if (sampleNum == 0.0 && previous.staleFrameNum + 1u >= staleLimit) {
            InterlockedAdd(stats[11], 1u);
        }
    }
    SharcResolveEntry(id.x, parameters, resolve);
}
