#ifndef CAUSTICA_TONEMAP_PSYCHOV11_GLSL
#define CAUSTICA_TONEMAP_PSYCHOV11_GLSL

#include "psycho_common.glsl"

vec3 psychoTonemapBT2020(vec3 inputColor, float peakValue, bool compressToBT2020) {
    const float kEps = 1.0e-6;
    vec3 lmsMidgrayRaw = psychoLMSFromBT2020(vec3(0.18));
    float lumMidgray = psychoLuminanceFromLMS(lmsMidgrayRaw);

    vec3 lmsColorRaw = psychoLMSFromBT2020(inputColor);

    float peak = max(peakValue, 0.25);
    vec3 lmsPeakUnit = psychoLMSFromBT2020(vec3(peak));
    vec3 lmsTonedUnit = psychoNakaRushton(lmsColorRaw, lmsPeakUnit, lmsMidgrayRaw, 1.0);

    if (compressToBT2020) {
        lmsTonedUnit = psychoGamutCompress(lmsTonedUnit);
    }
    vec3 mapped2020 = psychoBT2020FromLMS(lmsTonedUnit);
    return compressToBT2020 ? max(mapped2020, vec3(0.0)) : mapped2020;
}

vec3 tonemapHdrPsychoV(vec3 hdr, float exposure) {
    vec3 paperReferred709 = max(hdr * exposure, vec3(0.0));
    vec3 paperReferred2020 = max(BT709_TO_BT2020 * paperReferred709, vec3(0.0));
    vec3 mapped2020 = psychoTonemapBT2020(paperReferred2020, 1.0, true);
    return pqEncodeNits(mapped2020 * pc.paperWhiteNits);
}

vec3 tonemapSdrPsychoV(vec3 color) {
    vec3 color2020 = max(BT709_TO_BT2020 * color, vec3(0.0));
    vec3 mapped2020 = psychoTonemapBT2020(color2020, 1.0, false);
    vec3 mapped709 = BT2020_TO_BT709 * mapped2020;
    float luma = clamp(dot(mapped709, LUMA_BT709), 0.0, 1.0);
    vec3 chroma = mapped709 - vec3(luma);
    float scale = 1.0;
    for (int i = 0; i < 3; ++i) {
        if (chroma[i] < 0.0) {
            scale = min(scale, luma / -chroma[i]);
        } else if (chroma[i] > 0.0) {
            scale = min(scale, (1.0 - luma) / chroma[i]);
        }
    }
    return clamp(vec3(luma) + chroma * clamp(scale, 0.0, 1.0), 0.0, 1.0);
}

#endif
