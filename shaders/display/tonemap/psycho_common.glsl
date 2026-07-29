#ifndef CAUSTICA_TONEMAP_PSYCHO_COMMON_GLSL
#define CAUSTICA_TONEMAP_PSYCHO_COMMON_GLSL

#include "common.glsl"

const mat3 PSYCHO_BT2020_TO_XYZ = mat3(
    0.6369580483, 0.2627002120, 0.0000000000,
    0.1446169036, 0.6779980715, 0.0280726930,
    0.1688809752, 0.0593017165, 1.0609850577);

const mat3 PSYCHO_XYZ_TO_BT2020 = mat3(
     1.7166511880, -0.6666843518,  0.0176398574,
    -0.3556707838,  1.6164812366, -0.0427706133,
    -0.2533662814,  0.0157685458,  0.9421031212);

const mat3 PSYCHO_XYZ_TO_LMS = mat3(
    0.2670502842655792,  -0.38706882411220156, 0.026727793989083093,
    0.8471990148492798,   1.165429935890458,   -0.02729131667566509,
   -0.03470416612462053,  0.10302286696614202,  0.5333267257603284);

const mat3 PSYCHO_LMS_TO_XYZ = mat3(
     1.81146296,  0.60691241, -0.05972506,
    -1.30814926,  0.41590626,  0.08684090,
     0.37056946, -0.04084826,  1.85436177);

const vec3 PSYCHO_MB_WEIGHTS = vec3(0.68990272, 0.34832189, 0.0371597);
const vec2 PSYCHO_WHITE_D65 = vec2(0.31272, 0.32903);

vec3 psychoSignPow(vec3 x, vec3 e) {
    return vec3(
        (x.x < 0.0 ? -1.0 : 1.0) * pow(abs(x.x), e.x),
        (x.y < 0.0 ? -1.0 : 1.0) * pow(abs(x.y), e.y),
        (x.z < 0.0 ? -1.0 : 1.0) * pow(abs(x.z), e.z));
}

vec3 psychoLMSFromBT2020(vec3 bt2020) {
    return PSYCHO_XYZ_TO_LMS * (PSYCHO_BT2020_TO_XYZ * bt2020);
}

vec3 psychoBT2020FromLMS(vec3 lmsAbs) {
    return PSYCHO_XYZ_TO_BT2020 * (PSYCHO_LMS_TO_XYZ * lmsAbs);
}

float psychoLuminanceFromLMS(vec3 lms) {
    return dot(lms, vec3(0.68990272, 0.34832189, 0.0));
}

float psychoMBYFromLMS(vec3 lms) {
    return PSYCHO_MB_WEIGHTS.x * lms.x + PSYCHO_MB_WEIGHTS.y * lms.y;
}

vec3 psychoMB2FromLMS(vec3 lms) {
    const float eps = 1.0e-12;
    float wl = PSYCHO_MB_WEIGHTS.x * lms.x;
    float wm = PSYCHO_MB_WEIGHTS.y * lms.y;
    float yMb = wl + wm;
    if (yMb <= eps) {
        return vec3(0.0);
    }
    float inv = safeDiv(1.0, yMb, 0.0);
    return vec3(wl * inv, PSYCHO_MB_WEIGHTS.z * lms.z * inv, yMb);
}

vec3 psychoLMSFromMB2(vec3 mb2) {
    float l = mb2.x;
    float s = mb2.y;
    float y = max(mb2.z, 0.0);
    float L = safeDiv(l * y, PSYCHO_MB_WEIGHTS.x, 0.0);
    float M = safeDiv((1.0 - l) * y, PSYCHO_MB_WEIGHTS.y, 0.0);
    float S = safeDiv(s * y, PSYCHO_MB_WEIGHTS.z, 0.0);
    return vec3(L, M, S);
}

vec3 psychoXYZFromxyY(vec3 xyY) {
    vec3 xyz;
    xyz.xz = vec2(xyY.x, 1.0 - xyY.x - xyY.y) / xyY.y * xyY.z;
    xyz.y = xyY.z;
    return xyz;
}

vec2 psychoWhiteD65Chromaticity() {
    vec3 d65Xyz = psychoXYZFromxyY(vec3(PSYCHO_WHITE_D65, 1.0));
    vec3 d65Lms = PSYCHO_XYZ_TO_LMS * d65Xyz;
    return psychoMB2FromLMS(d65Lms).xy;
}

vec2 psychoMBFromLMS(vec3 lms) {
    float yMb = psychoMBYFromLMS(lms);
    if (yMb <= 0.0) {
        return vec2(0.0);
    }
    return vec2(
        safeDiv(PSYCHO_MB_WEIGHTS.x * lms.x, yMb, 0.0),
        safeDiv(PSYCHO_MB_WEIGHTS.z * lms.z, yMb, 0.0));
}

vec2 psychoMBFromBT2020Primary(vec3 primaryRgb) {
    vec3 xyz = PSYCHO_BT2020_TO_XYZ * primaryRgb;
    vec3 lms = PSYCHO_XYZ_TO_LMS * xyz;
    return psychoMBFromLMS(lms);
}

float psychoCross2(vec2 a, vec2 b) {
    return a.x * b.y - a.y * b.x;
}

bool psychoRaySegmentHit2D(vec2 origin, vec2 dir, vec2 a, vec2 b, out float tHit) {
    const float eps = 1.0e-20;
    tHit = 0.0;
    vec2 e = b - a;
    float denom = psychoCross2(dir, e);
    if (abs(denom) <= eps) {
        return false;
    }
    vec2 ao = a - origin;
    float t = psychoCross2(ao, e) / denom;
    float u = psychoCross2(ao, dir) / denom;
    if (t < 0.0 || u < 0.0 || u > 1.0) {
        return false;
    }
    tHit = t;
    return true;
}

float psychoRayMaxTBT2020(vec2 origin, vec2 direction, out bool hasSolution) {
    const float intervalMax = 1.0e30;
    const float eps = 1.0e-14;
    hasSolution = false;
    if (dot(direction, direction) <= eps) {
        return 0.0;
    }

    vec2 r = psychoMBFromBT2020Primary(vec3(1.0, 0.0, 0.0));
    vec2 g = psychoMBFromBT2020Primary(vec3(0.0, 1.0, 0.0));
    vec2 b = psychoMBFromBT2020Primary(vec3(0.0, 0.0, 1.0));

    float tBest = intervalMax;
    float t;
    bool hitAny = false;

    if (psychoRaySegmentHit2D(origin, direction, r, g, t)) { tBest = min(tBest, t); hitAny = true; }
    if (psychoRaySegmentHit2D(origin, direction, g, b, t)) { tBest = min(tBest, t); hitAny = true; }
    if (psychoRaySegmentHit2D(origin, direction, b, r, t)) { tBest = min(tBest, t); hitAny = true; }

    hasSolution = hitAny;
    return hitAny ? max(tBest, 0.0) : 0.0;
}

vec3 psychoGamutCompress(vec3 lms) {
    const float eps = 1.0e-14;
    float yMb = psychoMBYFromLMS(abs(lms));
    vec2 white = psychoWhiteD65Chromaticity();
    vec2 mb0 = psychoMBFromLMS(lms);
    vec2 direction = mb0 - white;
    if (dot(direction, direction) < eps) {
        return lms;
    }

    bool hasSolution;
    float tMax = psychoRayMaxTBT2020(white, direction, hasSolution);
    if (!hasSolution) {
        return lms;
    }

    float whiteRatio = max(safeDiv(1.0 - tMax, tMax, 0.0), 0.0);
    float whiteAdd = yMb * whiteRatio;
    vec3 whiteUnitLms = psychoLMSFromMB2(vec3(white, 1.0));
    return lms + whiteUnitLms * whiteAdd;
}

vec3 psychoRestoreHueMB2(vec3 lmsSource, vec3 lmsTarget, float amount) {
    const float eps = 1.0e-6;
    float restore = clamp(amount, 0.0, 1.0);
    if (restore <= 0.0) {
        return lmsTarget;
    }

    vec3 mbSource = psychoMB2FromLMS(lmsSource);
    vec3 mbTarget = psychoMB2FromLMS(lmsTarget);
    vec2 mbWhite = psychoWhiteD65Chromaticity();

    vec2 sourceOffset = mbSource.xy - mbWhite;
    vec2 targetOffset = mbTarget.xy - mbWhite;
    float src2 = dot(sourceOffset, sourceOffset);
    float tgt2 = dot(targetOffset, targetOffset);
    if (src2 <= eps || tgt2 <= eps) {
        return lmsTarget;
    }

    vec2 sourceDir = sourceOffset * inversesqrt(src2);
    vec2 targetDir = targetOffset * inversesqrt(tgt2);
    vec2 blendedDir = mix(targetDir, sourceDir, restore);
    float blendedLen2 = dot(blendedDir, blendedDir);
    if (blendedLen2 <= eps) {
        blendedDir = targetDir;
    } else {
        blendedDir *= inversesqrt(blendedLen2);
    }

    float targetRadius = sqrt(tgt2);
    vec2 mbRestoredXy = mbWhite + blendedDir * targetRadius;
    return psychoLMSFromMB2(vec3(mbRestoredXy, mbTarget.z));
}

vec3 psychoScalePurityMB2(vec3 lms, float purityScale) {
    const float eps = 1.0e-6;
    if (abs(purityScale - 1.0) <= eps) {
        return lms;
    }
    vec3 mb = psychoMB2FromLMS(lms);
    vec2 mbWhite = psychoWhiteD65Chromaticity();
    vec2 mbOffset = mb.xy - mbWhite;
    vec2 direction = mbOffset;
    float offsetLen = length(direction);
    if (offsetLen < eps) {
        return lms;
    }

    float desiredLen = offsetLen * max(purityScale, 0.0);
    bool hasSolution;
    float tMax = psychoRayMaxTBT2020(mbWhite, direction / offsetLen, hasSolution);
    float maxLen = hasSolution ? tMax : offsetLen;
    float clampedLen = min(desiredLen, maxLen * 0.98);
    vec2 mbScaled = mbWhite + (direction / offsetLen) * clampedLen;
    return psychoLMSFromMB2(vec3(mbScaled, mb.z));
}

float psychoContrastSafe(float x, float contrast, float midGray) {
    float ratio = x / midGray;
    float signedPow = (ratio < 0.0 ? -1.0 : 1.0) * pow(abs(ratio), max(contrast, 1.0e-4));
    return signedPow * midGray;
}

float psychoHighlightsFn(float x, float highlights, float midGray) {
    if (highlights > 1.0) {
        return max(x, mix(x, midGray * pow(max(x / midGray, 0.0), highlights), x));
    }
    if (highlights < 1.0) {
        return min(x, x / (1.0 + midGray * pow(max(x / midGray, 0.0), 2.0 - highlights) - x));
    }
    return x;
}

float psychoShadowsFn(float x, float shadows, float midGray) {
    if (shadows > 1.0) {
        return max(x, x * (1.0 + (x * midGray / max(pow(max(x / midGray, 0.0), shadows), 1.0e-6))));
    }
    if (shadows < 1.0) {
        return clamp(x * (1.0 - (x * midGray / max(pow(max(x / midGray, 0.0), 2.0 - shadows), 1.0e-6))), 0.0, x);
    }
    return x;
}

float psychoNeutwo(float x, float peak) {
    return (peak * x) * inversesqrt(x * x + peak * peak);
}

float psychoNeutwoClip(float x, float peak, float clip) {
    float cc = clip * clip;
    float pp = peak * peak;
    float xx = x * x;
    float numerator = clip * peak * x;
    float denomSq = max(xx * (cc - pp) + cc * pp, 1.0e-12);
    return numerator * inversesqrt(denomSq);
}

vec3 psychoNeutwoPerChannel(vec3 color, vec3 peak) {
    return vec3(
        psychoNeutwo(color.r, peak.r),
        psychoNeutwo(color.g, peak.g),
        psychoNeutwo(color.b, peak.b));
}

vec3 psychoNeutwoClipPerChannel(vec3 color, vec3 peak, vec3 clip) {
    return vec3(
        psychoNeutwoClip(color.r, peak.r, clip.r),
        psychoNeutwoClip(color.g, peak.g, clip.g),
        psychoNeutwoClip(color.b, peak.b, clip.b));
}

vec3 psychoNakaRushton(vec3 x, vec3 peak, vec3 gray, float coneExp) {
    vec3 safePeak = max(peak, gray + vec3(1.0e-4));
    vec3 n = max(coneExp, 1.0e-4) * safePeak / (safePeak - gray);
    vec3 xN = psychoSignPow(x, n);
    vec3 num = safePeak * xN;
    vec3 den = pow(max(gray, vec3(1.0e-6)), n - 1.0) * (safePeak - gray) + xN;
    return num / max(den, vec3(1.0e-6));
}

#endif
