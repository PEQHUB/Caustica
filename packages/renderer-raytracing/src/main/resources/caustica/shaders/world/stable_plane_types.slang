module stable_plane_types;
import world_common;

public static const uint STABLE_PLANE_COUNT = 3u;
public static const uint STABLE_PLANE_INVALID_BRANCH = 0xffffffffu;
public static const uint STABLE_PLANE_FLAG_VALID = 1u;
public static const uint STABLE_PLANE_FLAG_ENVIRONMENT = 2u;
public static const uint STABLE_PLANE_FLAG_DOMINANT = 4u;
public static const uint STABLE_PLANE_FLAG_TRANSMISSION = 8u;
// Flags bits 8 through 15 store the number of delta segments before the endpoint.
public static const uint STABLE_PLANE_DEPTH_SHIFT = 8u;
public static const uint STABLE_PLANE_DEPTH_MASK = 255u;
// A branch id is 1 for the camera path; each delta event shifts in two bits naming its lobe. The leading
// bit and every reflection bit therefore occupy even bit positions.
public static const uint STABLE_PLANE_BRANCH_REFLECTION = 1u;
public static const uint STABLE_PLANE_BRANCH_TRANSMISSION = 2u;

public bool stablePlaneBranchReflects(uint branchId) {
    return countbits(branchId & 0x55555555u) > 1u;
}

// Logical world-space guide values; physical scratch storage is scalar SoA.
public struct StablePlaneRecord {
    public float3 currentVirtualPosition;
    public float roughness;
    public float3 previousVirtualPosition;
    public float throughputLuminance;
    public float3 normal;
    public float diffuseHitDistance;
    public float3 diffuseBsdfEstimate;
    public float specularHitDistance;
    public float3 specularBsdfEstimate;
    public float3 noisyDiffuseRadiance;
    public uint flags;
    public float3 noisySpecularRadiance;
    public uint branchId;
};

// Each scalar word owns one contiguous stream of all logical plane/pixel records.
public struct StablePlaneStorage {
    public uint64_t address;
    public uint recordCount;
    public uint loadWord(uint index, uint word) {
        return ((ConstPtr<uint>)address)[uint64_t(word) * uint64_t(recordCount) + uint64_t(index)];
    }
    public void storeWord(uint index, uint word, uint value) {
        ((DevicePtr<uint>)address)[uint64_t(word) * uint64_t(recordCount) + uint64_t(index)] = value;
    }
    public float3 loadCurrentVirtualPosition(uint index) { return float3(asfloat(loadWord(index, 0u)), asfloat(loadWord(index, 1u)), asfloat(loadWord(index, 2u))); }
    public void storeCurrentVirtualPosition(uint index, float3 value) {
        storeWord(index, 0u, asuint(value.x));
        storeWord(index, 1u, asuint(value.y));
        storeWord(index, 2u, asuint(value.z));
    }
    public float loadRoughness(uint index) { return asfloat(loadWord(index, 3u)); }
    public void storeRoughness(uint index, float value) {
        storeWord(index, 3u, asuint(value));
    }
    public float3 loadPreviousVirtualPosition(uint index) { return float3(asfloat(loadWord(index, 4u)), asfloat(loadWord(index, 5u)), asfloat(loadWord(index, 6u))); }
    public void storePreviousVirtualPosition(uint index, float3 value) {
        storeWord(index, 4u, asuint(value.x));
        storeWord(index, 5u, asuint(value.y));
        storeWord(index, 6u, asuint(value.z));
    }
    public float loadThroughputLuminance(uint index) { return asfloat(loadWord(index, 7u)); }
    public void storeThroughputLuminance(uint index, float value) {
        storeWord(index, 7u, asuint(value));
    }
    public float3 loadNormal(uint index) { return float3(asfloat(loadWord(index, 8u)), asfloat(loadWord(index, 9u)), asfloat(loadWord(index, 10u))); }
    public void storeNormal(uint index, float3 value) {
        storeWord(index, 8u, asuint(value.x));
        storeWord(index, 9u, asuint(value.y));
        storeWord(index, 10u, asuint(value.z));
    }
    public float loadDiffuseHitDistance(uint index) { return asfloat(loadWord(index, 11u)); }
    public void storeDiffuseHitDistance(uint index, float value) {
        storeWord(index, 11u, asuint(value));
    }
    public float3 loadDiffuseBsdfEstimate(uint index) { return float3(asfloat(loadWord(index, 12u)), asfloat(loadWord(index, 13u)), asfloat(loadWord(index, 14u))); }
    public void storeDiffuseBsdfEstimate(uint index, float3 value) {
        storeWord(index, 12u, asuint(value.x));
        storeWord(index, 13u, asuint(value.y));
        storeWord(index, 14u, asuint(value.z));
    }
    public float loadSpecularHitDistance(uint index) { return asfloat(loadWord(index, 15u)); }
    public void storeSpecularHitDistance(uint index, float value) {
        storeWord(index, 15u, asuint(value));
    }
    public float3 loadSpecularBsdfEstimate(uint index) { return float3(asfloat(loadWord(index, 16u)), asfloat(loadWord(index, 17u)), asfloat(loadWord(index, 18u))); }
    public void storeSpecularBsdfEstimate(uint index, float3 value) {
        storeWord(index, 16u, asuint(value.x));
        storeWord(index, 17u, asuint(value.y));
        storeWord(index, 18u, asuint(value.z));
    }
    public float3 loadNoisyDiffuseRadiance(uint index) { return float3(asfloat(loadWord(index, 20u)), asfloat(loadWord(index, 21u)), asfloat(loadWord(index, 22u))); }
    public void storeNoisyDiffuseRadiance(uint index, float3 value) {
        storeWord(index, 20u, asuint(value.x));
        storeWord(index, 21u, asuint(value.y));
        storeWord(index, 22u, asuint(value.z));
    }
    public uint loadFlags(uint index) { return loadWord(index, 23u); }
    public void storeFlags(uint index, uint value) {
        storeWord(index, 23u, value);
    }
    public float3 loadNoisySpecularRadiance(uint index) { return float3(asfloat(loadWord(index, 24u)), asfloat(loadWord(index, 25u)), asfloat(loadWord(index, 26u))); }
    public void storeNoisySpecularRadiance(uint index, float3 value) {
        storeWord(index, 24u, asuint(value.x));
        storeWord(index, 25u, asuint(value.y));
        storeWord(index, 26u, asuint(value.z));
    }
    public uint loadBranchId(uint index) { return loadWord(index, 27u); }
    public void storeBranchId(uint index, uint value) {
        storeWord(index, 27u, value);
    }
    public StablePlaneRecord load(uint index) {
        StablePlaneRecord value = {};
        value.currentVirtualPosition = loadCurrentVirtualPosition(index);
        value.roughness = loadRoughness(index);
        value.previousVirtualPosition = loadPreviousVirtualPosition(index);
        value.throughputLuminance = loadThroughputLuminance(index);
        value.normal = loadNormal(index);
        value.diffuseHitDistance = loadDiffuseHitDistance(index);
        value.diffuseBsdfEstimate = loadDiffuseBsdfEstimate(index);
        value.specularHitDistance = loadSpecularHitDistance(index);
        value.specularBsdfEstimate = loadSpecularBsdfEstimate(index);
        value.noisyDiffuseRadiance = loadNoisyDiffuseRadiance(index);
        value.flags = loadFlags(index);
        value.noisySpecularRadiance = loadNoisySpecularRadiance(index);
        value.branchId = loadBranchId(index);
        return value;
    }
    public void store(uint index, StablePlaneRecord value) {
        storeCurrentVirtualPosition(index, value.currentVirtualPosition);
        storeRoughness(index, value.roughness);
        storePreviousVirtualPosition(index, value.previousVirtualPosition);
        storeThroughputLuminance(index, value.throughputLuminance);
        storeNormal(index, value.normal);
        storeDiffuseHitDistance(index, value.diffuseHitDistance);
        storeDiffuseBsdfEstimate(index, value.diffuseBsdfEstimate);
        storeSpecularHitDistance(index, value.specularHitDistance);
        storeSpecularBsdfEstimate(index, value.specularBsdfEstimate);
        storeNoisyDiffuseRadiance(index, value.noisyDiffuseRadiance);
        storeFlags(index, value.flags);
        storeNoisySpecularRadiance(index, value.noisySpecularRadiance);
        storeBranchId(index, value.branchId);
    }
};
public StablePlaneStorage stablePlaneStorage(uint64_t address, uint recordCount) {
    StablePlaneStorage storage;
    storage.address = address;
    storage.recordCount = recordCount;
    return storage;
}
