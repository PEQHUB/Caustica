#ifndef CAUSTICA_TONEMAP_COMMON_GLSL
#define CAUSTICA_TONEMAP_COMMON_GLSL

const vec3 LUMA_BT709 = vec3(0.2126, 0.7152, 0.0722);

const mat3 BT709_TO_BT2020 = mat3(
    0.6274039, 0.0690973, 0.0163916,
    0.3292830, 0.9195406, 0.0880132,
    0.0433131, 0.0113612, 0.8955953
);

const mat3 BT2020_TO_BT709 = mat3(
     1.6604910, -0.1245505, -0.0181508,
    -0.5876411,  1.1328999, -0.1005789,
    -0.0728499, -0.0083494,  1.1187297
);

const float PQ_M1 = 0.1593017578125;
const float PQ_M2 = 78.84375;
const float PQ_C1 = 0.8359375;
const float PQ_C2 = 18.8515625;
const float PQ_C3 = 18.6875;

float pqEncode(float nits) {
    float y = pow(max(nits, 0.0) / 10000.0, PQ_M1);
    return pow((PQ_C1 + PQ_C2 * y) / (1.0 + PQ_C3 * y), PQ_M2);
}

vec3 pqEncodeNits(vec3 nits) {
    return vec3(pqEncode(nits.r), pqEncode(nits.g), pqEncode(nits.b));
}

float safeDiv(float a, float b, float fallback) {
    return abs(b) <= 1.0e-12 ? fallback : a / b;
}

vec3 safeDiv(vec3 a, vec3 b, vec3 fallback) {
    vec3 denomOk = vec3(greaterThan(abs(b), vec3(1.0e-12)));
    return mix(fallback, a / max(abs(b), vec3(1.0e-12)), denomOk);
}

float luminanceBt709(vec3 color) {
    return dot(color, LUMA_BT709);
}

#endif
