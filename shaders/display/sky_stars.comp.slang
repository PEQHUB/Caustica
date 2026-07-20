// Deterministic primary-sky star presentation pass. It runs after reconstruction and writes directly
// into the scene-linear output, so DLSS-RR/NRD cannot accumulate the primary dots into temporal history.
import world_common;
import world_stars;

[[vk::binding(0, 0)]] [format("rgba16f")] RWTexture2D<float4> outputImage;
[[vk::binding(1, 0)]] [format("r16f")] RWTexture2D<float> primarySkyMask;

struct SkyStarLayerPush {
    uint64_t worldPushAddr;
    uint sourceWidth;
    uint sourceHeight;
    uint outputWidth;
    uint outputHeight;
};
[[vk::push_constant]] SkyStarLayerPush pc;

bool conservativePrimarySky(uint2 outputPixel)
{
    float2 sourcePixel =
        (float2(outputPixel) + 0.5)
        * float2(pc.sourceWidth, pc.sourceHeight)
        / float2(pc.outputWidth, pc.outputHeight)
        - 0.5;

    int2 center = int2(floor(sourcePixel));

    for (int y = 0; y <= 1; ++y)
    for (int x = 0; x <= 1; ++x)
    {
        int2 p = clamp(
            center + int2(x, y),
            int2(0),
            int2(pc.sourceWidth, pc.sourceHeight) - 1);

        if (primarySkyMask.Load(p) < 0.5)
            return false;
    }

    return true;
}

[numthreads(16, 16, 1)]
void main(uint3 dispatchThreadId : SV_DispatchThreadID) {
    int2 pixel = int2(dispatchThreadId.xy);
    if (pixel.x >= int(pc.outputWidth) || pixel.y >= int(pc.outputHeight)) return;

    WorldPush world = ConstPtr<WorldPush>(pc.worldPushAddr)[0];
    if ((world.flags & FRAME_FLAG_DIRECT_SKY_STARS) == 0u
            || (world.flags & (1u << 10u)) == 0u) return;

    if (!conservativePrimarySky(uint2(pixel)))
        return;

    float2 outputSize = float2(pc.outputWidth, pc.outputHeight);
    float2 invOutput = 1.0 / outputSize;
    float2 centerPixel = float2(pixel);
    float2 centerUv = (centerPixel + 0.5) * invOutput;
    float2 centerNdc = centerUv * 2.0 - 1.0;
    float4 centerNearH = mul(world.invViewProj, float4(centerNdc.x, centerNdc.y, 1.0, 1.0));
    float4 centerFarH = mul(world.invViewProj, float4(centerNdc.x, centerNdc.y, 0.0, 1.0));
    float3 centerNear = centerNearH.xyz / centerNearH.w;
    float3 centerFar = centerFarH.xyz / centerFarH.w;
    float3 direction = normalize(centerFar - centerNear);

    // The angular footprint is measured at the four output-pixel corners. Multiplying by STAR_CELLS
    // converts the angular footprint into the same deterministic cell domain used by the RT layout.
    float maxAngularFootprint = 0.0;
    [unroll]
    for (uint corner = 0u; corner < 4u; corner++) {
        float2 offset = float2((corner & 1u) != 0u ? 0.5 : -0.5,
                (corner & 2u) != 0u ? 0.5 : -0.5);
        float2 cornerUv = (centerPixel + offset + 0.5) * invOutput;
        float2 cornerNdc = cornerUv * 2.0 - 1.0;
        float4 nearH = mul(world.invViewProj, float4(cornerNdc.x, cornerNdc.y, 1.0, 1.0));
        float4 farH = mul(world.invViewProj, float4(cornerNdc.x, cornerNdc.y, 0.0, 1.0));
        float3 cornerNear = nearH.xyz / nearH.w;
        float3 cornerFar = farH.xyz / farH.w;
        maxAngularFootprint = max(maxAngularFootprint,
                length(normalize(cornerFar - cornerNear) - direction));
    }

    float VdotS = dot(direction, world.sunDir.xyz);
    float3 star = circularStars(direction,
            world.lightRadiance.w * world.skyCelestialAppearance.w,
            VdotS, world.celestial, world.skyStarAppearance.x, world.skyStarAppearance.y,
            world.skyStarTint.xyz, maxAngularFootprint * SKY_STAR_CELLS);
    if (any(star > float3(0.0))) {
        float4 scene = outputImage[pixel];
        outputImage[pixel] = float4(scene.rgb + star, scene.a);
    }
}
