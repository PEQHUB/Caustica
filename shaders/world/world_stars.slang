// Shared procedural star layout and radiance evaluation. The RT miss keeps its existing square
// footprint for secondary/offline transport; the presentation layer uses the same deterministic cell
// lookup with a circular, output-pixel-filtered footprint.
module world_stars;

import world_common;

public static const float SKY_STAR_CELLS = 21.0;
public static const float SKY_STAR_THRESHOLD = 0.86;
public static const float SKY_STAR_HALF = 0.045;

public struct StarSample {
    public bool occupied;
    public float2 local;
    public float size;
    public float brightness;
    public float3 color;
};

public float starHash13(float3 p3) {
    p3 = frac(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return frac((p3.x + p3.y) * p3.z);
}

public float3 starRotateAxis(float3 v, float3 axis, float ang) {
    float c = cos(ang), s = sin(ang);
    return v * c + cross(axis, v) * s + axis * dot(axis, v) * (1.0 - c);
}

public StarSample evaluateStarCell(float3 dir, float4 celestial,
        float densityScale, float sizeScale, float3 tint) {
    StarSample result;
    result.occupied = false;
    result.local = float2(0.0);
    result.size = 0.0;
    result.brightness = 0.0;
    result.color = float3(0.0);
    if (dir.y <= 0.0) return result;

    // Negative angle: rotating the lookup direction is the inverse of rotating star geometry.
    float3 sdir = starRotateAxis(dir, normalize(celestial.xyz), -celestial.w);
    // Cube-map face + 2D gnomonic uv -> uniform square cells, no pole/equator distortion.
    float3 a = abs(sdir);
    float2 uv;
    float face;
    if (a.x >= a.y && a.x >= a.z)      { uv = sdir.yz / a.x; face = sdir.x > 0.0 ? 0.0 : 1.0; }
    else if (a.y >= a.z)               { uv = sdir.xz / a.y; face = sdir.y > 0.0 ? 2.0 : 3.0; }
    else                               { uv = sdir.xy / a.z; face = sdir.z > 0.0 ? 4.0 : 5.0; }
    float2 g = uv * SKY_STAR_CELLS;
    float3 id = float3(floor(g), face);
    float threshold = 1.0 - (1.0 - SKY_STAR_THRESHOLD) * clamp(densityScale, 0.0, 2.0);
    if (starHash13(id) < threshold) return result;

    // Per-star randoms: size, centre, and rotation. The centre inset keeps the legacy square clear of
    // the cell edge; circular output coverage uses the same authored star centre and size.
    float sz = SKY_STAR_HALF * clamp(sizeScale, 0.25, 4.0) * (0.6 + 0.8 * starHash13(id + 53.0));
    float inset = sz * 1.5;
    float2 center = inset + (1.0 - 2.0 * inset)
            * float2(starHash13(id + 11.0), starHash13(id + 23.0));
    float2 q = frac(g) - center;
    float ang = starHash13(id + 91.0) * 6.2831853;
    float cs = cos(ang), sn = sin(ang);
    q = float2(cs * q.x - sn * q.y, sn * q.x + cs * q.y);

    result.occupied = true;
    result.local = q;
    result.size = sz;
    result.brightness = 0.55 + 0.45 * starHash13(id + 71.0);
    result.color = lerp(float3(0.78, 0.60, 0.42), float3(0.50, 0.62, 0.95),
            starHash13(id + 137.0)) * tint;
    return result;
}

public float starVisibility(float3 dir, float VdotS) {
    return min(dir.y * 3.0, 1.0) * max(0.0, 1.0 - pow(abs(VdotS) * 1.002, 100.0));
}

public float starSquareCoverage(float2 local, float size) {
    float box = max(abs(local.x), abs(local.y));
    return 1.0 - smoothstep(size * 0.6, size, box);
}

// Circular coverage is filtered in the same cell-coordinate domain as the star layout. The footprint
// comes from the output pixel's angular corners, so coverage is deterministic instead of jitter/history
// dependent under reconstruction.
public float starCircularCoverage(float2 local, float size, float pixelFootprint) {
    float radius = length(local);
    float edge = max(pixelFootprint * 0.5, 1.0e-5);
    return 1.0 - smoothstep(max(size - edge, 0.0), size + edge, radius);
}

public float3 starRadianceFromSample(float3 dir, float starBrightness, float VdotS,
        StarSample star, float coverage) {
    if (starBrightness <= 0.0 || coverage <= 0.0 || dir.y <= 0.0) return float3(0.0);
    if (!star.occupied) return float3(0.0);
    float visible = coverage * star.brightness * starVisibility(dir, VdotS);
    return bt709ToBt2020(0.002 * visible * star.color * starBrightness);
}

public float3 starRadiance(float3 dir, float starBrightness, float VdotS,
        float4 celestial, float densityScale, float sizeScale, float3 tint,
        float coverage) {
    return starRadianceFromSample(dir, starBrightness, VdotS,
            evaluateStarCell(dir, celestial, densityScale, sizeScale, tint), coverage);
}

public float3 circularStars(float3 dir, float starBrightness, float VdotS, float4 celestial,
        float densityScale, float sizeScale, float3 tint, float pixelFootprint) {
    StarSample star = evaluateStarCell(dir, celestial, densityScale, sizeScale, tint);
    if (!star.occupied) return float3(0.0);
    return starRadianceFromSample(dir, starBrightness, VdotS, star,
            starCircularCoverage(star.local, star.size, pixelFootprint));
}

public float3 sharedStars(float3 dir, float starBrightness, float VdotS, float4 celestial,
        float densityScale, float sizeScale, float3 tint) {
    StarSample star = evaluateStarCell(dir, celestial, densityScale, sizeScale, tint);
    if (!star.occupied) return float3(0.0);
    return starRadianceFromSample(dir, starBrightness, VdotS, star,
            starSquareCoverage(star.local, star.size));
}
