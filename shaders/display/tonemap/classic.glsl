#ifndef CAUSTICA_TONEMAP_CLASSIC_GLSL
#define CAUSTICA_TONEMAP_CLASSIC_GLSL

#include "common.glsl"

const mat3 AGX_INSET = mat3(
    0.842479062253094, 0.042328242261012, 0.042375654905705,
    0.078433599999999, 0.878468636469772, 0.078433600000000,
    0.079223745147764, 0.079166127460543, 0.879142973793104
);

const mat3 AGX_OUTSET = mat3(
    1.196879005120170, -0.052896851757456, -0.052971635514443,
    -0.098020881140137, 1.151903129904170, -0.098043450117124,
    -0.099029744079720, -0.098961176844843, 1.151073672641160
);

const float AGX_MIN_EV = -12.47393;
const float AGX_MAX_EV = 4.026069;

vec3 agxDefaultContrast(vec3 x) {
    vec3 x2 = x * x;
    vec3 x4 = x2 * x2;
    return 15.5 * x4 * x2
        - 40.14 * x4 * x
        + 31.96 * x4
        - 6.868 * x2 * x
        + 0.4298 * x2
        + 0.1191 * x
        - 0.00232;
}

vec3 agx(vec3 color) {
    color = AGX_INSET * max(color, vec3(0.0));
    color = clamp(log2(max(color, vec3(1.0e-10))), AGX_MIN_EV, AGX_MAX_EV);
    color = (color - AGX_MIN_EV) / (AGX_MAX_EV - AGX_MIN_EV);
    color = agxDefaultContrast(color);
    color = AGX_OUTSET * color;
    return clamp(color, 0.0, 1.0);
}

vec3 pbrNeutralTonemap(vec3 color) {
    float startCompression = 0.76;
    float desaturation = 0.15;

    color = max(color, vec3(0.0));
    float x = min(color.r, min(color.g, color.b));
    float offset = x < 0.08 ? x - 6.25 * x * x : 0.04;
    color -= offset;

    float peak = max(color.r, max(color.g, color.b));
    if (peak < startCompression) {
        return color;
    }

    float d = 1.0 - startCompression;
    float newPeak = 1.0 - d * d / max(peak + d - startCompression, 1.0e-6);
    color *= safeDiv(newPeak, peak, 1.0);

    float g = 1.0 - 1.0 / (desaturation * (peak - newPeak) + 1.0);
    return mix(color, vec3(newPeak), g);
}

vec3 luminanceTonemap(vec3 color, float lMapped, float lSource) {
    return color * safeDiv(lMapped, lSource, 1.0);
}

vec3 reinhardExtendedTonemap(vec3 color) {
    float l = luminanceBt709(color);
    if (l < 1.0e-6) {
        return color;
    }
    float lWhite = 4.0;
    float lWhite2 = lWhite * lWhite;
    float lm = l * (1.0 + l / lWhite2) / (1.0 + l);
    return luminanceTonemap(color, clamp(lm, 0.0, 1.0), l);
}

vec3 acesHillTonemap(vec3 color) {
    color *= 1.0;
    float l = luminanceBt709(color);
    if (l < 1.0e-6) {
        return color;
    }
    float lm = (l * (2.51 * l + 0.03)) / max(l * (2.43 * l + 0.59) + 0.14, 1.0e-6);
    return luminanceTonemap(color, clamp(lm, 0.0, 1.0), l);
}

vec3 lottesTonemap(vec3 color) {
    float l = luminanceBt709(color);
    if (l < 1.0e-6) {
        return color;
    }

    float a = 2.0;
    float d = 1.0;
    float hdrMax = 16.0;
    float midIn = 0.18;
    float midOut = 0.18;
    float hdrPow = pow(hdrMax, a * d);
    float midPow = pow(midIn, a * d);
    float denom = max((hdrPow - midPow) * midOut, 1.0e-6);
    float b = (-pow(midIn, a) + pow(hdrMax, a) * midOut) / denom;
    float c = (hdrPow * pow(midIn, a) - pow(hdrMax, a) * midPow * midOut) / denom;

    float lm = pow(l, a) / max(pow(l, a * d) * b + c, 1.0e-6);
    return luminanceTonemap(color, clamp(lm, 0.0, 1.0), l);
}

vec3 frostbiteTonemap(vec3 color) {
    float l = luminanceBt709(color);
    if (l < 1.0e-6) {
        return color;
    }

    float linearEnd = 0.25;
    float shoulderStrength = 2.0;
    float lm = l <= linearEnd
        ? l
        : linearEnd + (1.0 - linearEnd) * (1.0 - exp(-(l - linearEnd) * shoulderStrength));
    return luminanceTonemap(color, clamp(lm, 0.0, 1.0), l);
}

float hablePartial(float x, float a, float b, float c, float d, float e, float f) {
    return ((x * (a * x + c * b) + d * e) / max(x * (a * x + b) + d * f, 1.0e-6)) - e / f;
}

vec3 uncharted2Tonemap(vec3 color) {
    float l = luminanceBt709(color);
    if (l < 1.0e-6) {
        return color;
    }

    float a = 0.15;
    float b = 0.50;
    float c = 0.10;
    float d = 0.20;
    float e = 0.02;
    float f = 0.30;
    float w = 11.2;
    float whiteScale = max(hablePartial(w, a, b, c, d, e, f), 1.0e-6);
    float lm = hablePartial(l, a, b, c, d, e, f) / whiteScale;
    return luminanceTonemap(color, clamp(lm, 0.0, 1.0), l);
}

vec3 gtTonemap(vec3 color) {
    float l = luminanceBt709(color);
    if (l < 1.0e-6) {
        return color;
    }

    const float p = 1.0;
    float a = 1.0;
    float m = 0.22;
    float linearLength = 0.4;
    float c = 1.33;
    float b = 0.0;

    float l0 = ((p - m) * linearLength) / a;
    float s0 = m + l0;
    float s1 = m + a * l0;
    float c2 = (a * p) / max(p - s1, 1.0e-6);
    float cp = -c2 / p;

    float w0 = 1.0 - smoothstep(0.0, m, l);
    float w2 = step(m + l0, l);
    float w1 = 1.0 - w0 - w2;

    float toe = m * pow(max(l / m, 0.0), c) + b;
    float shoulder = p - (p - s1) * exp(cp * (l - s0));
    float lm = toe * w0 + l * w1 + shoulder * w2;
    return luminanceTonemap(color, clamp(lm, 0.0, 1.0), l);
}

#endif
