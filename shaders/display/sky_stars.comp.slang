// Deterministic primary-sky star presentation pass. It runs after reconstruction and writes directly
// into the scene-linear output, so DLSS-RR/NRD cannot accumulate the primary dots into temporal history.
import world_common;
import world_stars;

[[vk::binding(0, 0)]] [format("rgba16f")] RWTexture2D<float4> outputImage;
[[vk::binding(1, 0)]] [format("r16f")] RWTexture2D<float> primarySkyMask;
[[vk::binding(2, 0)]] [format("r32f")] RWTexture2D<float> primarySkyDepth;

struct SkyStarLayerPush {
    uint64_t worldPushAddr;
    uint sourceWidth;
    uint sourceHeight;
    uint outputWidth;
    uint outputHeight;
};
[[vk::push_constant]] SkyStarLayerPush pc;

[numthreads(16, 16, 1)]
void main(uint3 dispatchThreadId : SV_DispatchThreadID) {
    int2 pixel = int2(dispatchThreadId.xy);
    if (pixel.x >= int(pc.outputWidth) || pixel.y >= int(pc.outputHeight)) return;

    WorldPush world = ConstPtr<WorldPush>(pc.worldPushAddr)[0];
    if ((world.flags & FRAME_FLAG_DIRECT_SKY_STARS) == 0u
            || (world.flags & (1u << 10u)) == 0u) return;

    // Map the output pixel footprint back to the RT-input visibility guides. Every covered source sample
    // must be a primary sky miss with the explicit zero-depth sky marker; a single geometry sample rejects
    // the output pixel conservatively. The depth check is intentionally independent of the compact mask so
    // a stale or misclassified mask sample cannot paint stars over a terrain silhouette.
    float2 sourceSize = float2(pc.sourceWidth, pc.sourceHeight);
    float2 outputSize = float2(pc.outputWidth, pc.outputHeight);
    // The RT mask was traced with the frame's subpixel jitter. Undo that shift before looking up the
    // source visibility footprint; otherwise a terrain edge can borrow the neighboring sky classification.
    float2 sourceCenter = (float2(pixel) + 0.5) * sourceSize / outputSize - 0.5 - world.jitter;
    float2 sourceHalfFootprint = 0.5 * sourceSize / outputSize;
    int2 minSource = int2(ceil(sourceCenter - sourceHalfFootprint));
    int2 maxSource = int2(floor(sourceCenter + sourceHalfFootprint));
    if (minSource.x < 0 || minSource.y < 0
            || maxSource.x >= int(pc.sourceWidth) || maxSource.y >= int(pc.sourceHeight)) return;
    [loop]
    for (int y = minSource.y; y <= maxSource.y; y++) {
        [loop]
        for (int x = minSource.x; x <= maxSource.x; x++) {
            if (primarySkyMask.Load(int2(x, y)) < 0.5) return;
            if (primarySkyDepth.Load(int2(x, y)) > 0.0) return;
        }
    }

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
