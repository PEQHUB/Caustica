#ifndef CAUSTICA_TONEMAP_HDR_GLSL
#define CAUSTICA_TONEMAP_HDR_GLSL

#include "common.glsl"
#include "psycho_common.glsl"

vec3 tonemapHdrCaustica(vec3 hdr, float exposure) {
    vec3 c = max(hdr * exposure, vec3(0.0));
    vec3 lo = min(c, vec3(1.0));
    vec3 hi = max(c - vec3(1.0), vec3(0.0));
    float k = max(pc.headroom - 1.0, 0.0);
    vec3 rolled = (k > 0.0) ? (k * hi) / (k + hi) : vec3(0.0);
    vec3 paperReferred = lo + rolled;
    vec3 nits709 = paperReferred * pc.paperWhiteNits;
    vec3 nits2020 = BT709_TO_BT2020 * nits709;
    return vec3(pqEncode(nits2020.r), pqEncode(nits2020.g), pqEncode(nits2020.b));
}

vec3 bt2390EetfPq(vec3 hdr, float exposure) {
    vec3 hdrLinear = max(hdr * exposure, vec3(0.0));
    float maxNits = pc.paperWhiteNits * pc.headroom;
    vec3 hdrPq = pqEncodeNits(hdrLinear * 100.0);
    vec3 maxPq = pqEncodeNits(vec3(maxNits));
    vec3 pqScaled = hdrPq / max(maxPq, vec3(1.0e-6));
    vec3 eetf = pqScaled * (pqScaled * (pqScaled * 0.15 + 0.1) * 0.3 + 0.2);
    eetf = max(eetf, vec3(0.0));
    return eetf * max(maxPq, vec3(1.0e-6));
}

vec3 applyHdrToneMapper(int hdrMode, vec3 hdr, float exposure) {
    if (hdrMode == HDR_CAUSTICA) {
        return tonemapHdrCaustica(hdr, exposure);
    } else if (hdrMode == HDR_BT2390) {
        return bt2390EetfPq(hdr, exposure);
    } else if (hdrMode == HDR_PSYCHOV11) {
        return tonemapHdrPsychoV(hdr, exposure);
    } else if (hdrMode == HDR_PSYCHOV23) {
        return tonemapHdrPsychoV23(hdr, exposure);
    } else if (hdrMode == HDR_PSYCHOV24) {
        return tonemapHdrPsychoV24(hdr, exposure);
    }
    return tonemapHdrCaustica(hdr, exposure);
}

#endif
