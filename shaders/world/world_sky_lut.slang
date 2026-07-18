// Shared physical sky-view lookup used by visible misses and camera-space boundary fog.
module world_sky_lut;

import world_common;

#ifndef CAUSTICA_NRD
#define CAUSTICA_NRD 0
#endif
#if CAUSTICA_NRD
[[vk::binding(20, 0)]] [format("rgba32f")] RWTexture2D<float4> skyViewLut;
#else
[[vk::binding(14, 0)]] [format("rgba32f")] RWTexture2D<float4> skyViewLut;
#endif

static const float SKY_VIEW_PI = 3.14159265359;
public static const float3 SKY_AIRGLOW_ZENITH = float3(0.00000060, 0.00000078, 0.00000120);
public static const float3 SKY_AIRGLOW_HORIZON = float3(0.00000082, 0.00000088, 0.00000105);

public float3 sampleSkyView(float3 dir) {
    uint width, height;
    skyViewLut.GetDimensions(width, height);
    float elevation = asin(clamp(dir.y, 0.0, 1.0));
    float2 uv = float2(atan2(dir.z, dir.x) / (2.0 * SKY_VIEW_PI) + 0.5,
            sqrt(elevation / (0.5 * SKY_VIEW_PI)));
    float2 pixel = uv * float2(width, height) - 0.5;
    int2 base = int2(floor(pixel));
    float2 f = frac(pixel);
    int x0 = ((base.x % int(width)) + int(width)) % int(width);
    int x1 = (x0 + 1) % int(width);
    int y0 = clamp(base.y, 0, int(height) - 1);
    int y1 = min(y0 + 1, int(height) - 1);
    float3 a = lerp(skyViewLut[uint2(x0, y0)].rgb, skyViewLut[uint2(x1, y0)].rgb, f.x);
    float3 b = lerp(skyViewLut[uint2(x0, y1)].rgb, skyViewLut[uint2(x1, y1)].rgb, f.x);
    return lerp(a, b, f.y);
}

// This is presentation in-scatter, not a path light. It is evaluated once after camera transport so
// the fog responds to the physical sky without adding rays or injecting energy into indirect bounces.
public float3 atmosphericBoundaryFog(float3 dir, WorldPush pc) {
    bool earthAtmosphere = (pc.flags & (1u << 10u)) != 0u;
    if (!earthAtmosphere) return max(pc.borderFogColor.xyz, float3(0.0));

    float3 fogDir = dir.y >= 0.0 ? normalize(dir)
            : normalize(float3(dir.x + 1.0e-6, 0.0, dir.z));
    float3 col = sampleSkyView(fogDir);
    float3 airglow = bt709ToBt2020(lerp(SKY_AIRGLOW_HORIZON, SKY_AIRGLOW_ZENITH,
            clamp(fogDir.y, 0.0, 1.0)));
    col += airglow * pc.skyLighting.z * (1.0 - pc.skyState.w);
    return max(col * pc.skyState.z, float3(0.0));
}
