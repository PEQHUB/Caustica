#ifndef CAUSTICA_TONEMAP_DISPATCH_GLSL
#define CAUSTICA_TONEMAP_DISPATCH_GLSL

const int SDR_AGX = 0;
const int SDR_PBR_NEUTRAL = 1;
const int SDR_REINHARD = 2;
const int SDR_ACES = 3;
const int SDR_LOTTES = 4;
const int SDR_FROSTBITE = 5;
const int SDR_UNCHARTED_2 = 6;
const int SDR_GT = 7;
const int SDR_PSYCHOV11 = 8;
const int SDR_PSYCHOV23 = 9;
const int SDR_PSYCHOV24 = 10;

const int HDR_CAUSTICA = 0;
const int HDR_BT2390 = 1;
const int HDR_PSYCHOV11 = 2;
const int HDR_PSYCHOV23 = 3;
const int HDR_PSYCHOV24 = 4;

#include "common.glsl"
#include "classic.glsl"
#include "psycho_common.glsl"
#include "psychov11.glsl"
#include "psychov23.glsl"
#include "psychov24.glsl"
#include "hdr.glsl"

vec3 applySdrToneMapper(int sdrMode, vec3 hdr, float exposure) {
    vec3 color = max(hdr * exposure, vec3(0.0));
    vec3 mapped;

    if (sdrMode == SDR_AGX) {
        mapped = agx(color);
    } else if (sdrMode == SDR_PBR_NEUTRAL) {
        mapped = pbrNeutralTonemap(color);
    } else if (sdrMode == SDR_REINHARD) {
        mapped = reinhardExtendedTonemap(color);
    } else if (sdrMode == SDR_ACES) {
        mapped = acesHillTonemap(color);
    } else if (sdrMode == SDR_LOTTES) {
        mapped = lottesTonemap(color);
    } else if (sdrMode == SDR_FROSTBITE) {
        mapped = frostbiteTonemap(color);
    } else if (sdrMode == SDR_UNCHARTED_2) {
        mapped = uncharted2Tonemap(color);
    } else if (sdrMode == SDR_GT) {
        mapped = gtTonemap(color);
    } else if (sdrMode == SDR_PSYCHOV11) {
        mapped = tonemapSdrPsychoV(color);
    } else if (sdrMode == SDR_PSYCHOV23) {
        mapped = tonemapSdrPsychoV23(color);
    } else if (sdrMode == SDR_PSYCHOV24) {
        mapped = tonemapSdrPsychoV24(color);
    } else {
        mapped = agx(color);
    }
    return mapped;
}

#endif
