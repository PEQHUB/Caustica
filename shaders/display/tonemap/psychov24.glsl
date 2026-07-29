#ifndef CAUSTICA_TONEMAP_PSYCHOV24_GLSL
#define CAUSTICA_TONEMAP_PSYCHOV24_GLSL

#include "psycho_common.glsl"

const float PSYCHO24_EPSILON = 1.0e-6;
const float PSYCHO24_TWO_PI = 6.2831853071795864769;
const float PSYCHO24_REFERENCE_SIMULTANEOUS_RANGE_LOG10 = 3.7;
const float PSYCHO24_REFERENCE_CENTERED_RANGE_SIDE_COUNT = 2.0;
const float PSYCHO24_HEADROOM_RATIO_FALLBACK = 1.0;
const float PSYCHO24_MIN_AUTO_COMPRESSION = 1.0;
const float PSYCHO24_MIN_MANUAL_COMPRESSION = 1.0e-6;
const float PSYCHO24_AUTO_COMPRESSION_SENTINEL = 0.0;
const float PSYCHO24_HIGHLIGHT_GRADE_REFERENCE_WHITE = 1.0;
const float PSYCHO24_SHADOW_GRADE_RANGE_STOPS = 4.0;

float psycho24_YfFromLMS(vec3 lms) {
    vec3 weighted = lms * PSYCHO_MB_WEIGHTS;
    return max(weighted.x + weighted.y, PSYCHO24_EPSILON);
}

float psycho24_QuinticUnitRamp(float t) {
    t = clamp(t, 0.0, 1.0);
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

float psycho24_HighlightsScalarV4(float x, float highlights, float adaptedAnchorYf) {
    if (highlights == 1.0) {
        return x;
    }
    float t = 0.0;
    if (x > adaptedAnchorYf) {
        float referenceRangeLog2 = log2(PSYCHO24_HIGHLIGHT_GRADE_REFERENCE_WHITE
                / max(adaptedAnchorYf, PSYCHO24_EPSILON));
        t = clamp(log2(x / max(adaptedAnchorYf, PSYCHO24_EPSILON))
                / max(referenceRangeLog2, PSYCHO24_EPSILON), 0.0, 1.0);
    }
    t = psycho24_QuinticUnitRamp(t);
    float ratio = max(x / max(adaptedAnchorYf, PSYCHO24_EPSILON), PSYCHO24_EPSILON);
    if (highlights > 1.0) {
        return mix(x, adaptedAnchorYf * pow(ratio, highlights), t);
    }
    float b = adaptedAnchorYf * pow(ratio, 2.0 - highlights);
    return safeDiv(x * x, mix(x, b, t), x);
}

float psycho24_ShadowsScalarV4(float x, float shadows, float adaptedAnchorYf) {
    if (shadows == 1.0) {
        return x;
    }
    float ratio = max(safeDiv(x, adaptedAnchorYf, 0.0), 0.0);
    float baseTerm = x * adaptedAnchorYf;
    float baseScale = safeDiv(baseTerm, ratio, 0.0);
    float shadowFloor = adaptedAnchorYf * exp2(-PSYCHO24_SHADOW_GRADE_RANGE_STOPS);
    float t = 1.0;
    if (x > shadowFloor) {
        t = clamp(log2(x / max(adaptedAnchorYf, PSYCHO24_EPSILON))
                / log2(shadowFloor / max(adaptedAnchorYf, PSYCHO24_EPSILON)), 0.0, 1.0);
    }
    t = psycho24_QuinticUnitRamp(t);
    if (shadows > 1.0) {
        float raised = x * (1.0 + safeDiv(baseTerm,
                pow(max(ratio, PSYCHO24_EPSILON), shadows), 0.0));
        float reference = x * (1.0 + baseScale);
        return x + (raised - reference) * t;
    }
    float lowered = x * (1.0 - safeDiv(baseTerm,
            pow(max(ratio, PSYCHO24_EPSILON), 2.0 - shadows), 0.0));
    float reference = x * (1.0 - baseScale);
    return x + (lowered - reference) * t;
}

float psycho24_AutoCompressionFromCenteredReferenceRange(float anchorOutYf, float peakYf) {
    float peakOverAnchor = safeDiv(max(peakYf, PSYCHO24_EPSILON),
            max(anchorOutYf, PSYCHO24_EPSILON), PSYCHO24_HEADROOM_RATIO_FALLBACK);
    peakOverAnchor = max(peakOverAnchor, 1.0 + PSYCHO24_EPSILON);
    float referenceOneSideRangeLog10 = PSYCHO24_REFERENCE_SIMULTANEOUS_RANGE_LOG10
            / PSYCHO24_REFERENCE_CENTERED_RANGE_SIDE_COUNT;
    float actualAboveAdaptationRangeLog10 = max(log(peakOverAnchor) / log(10.0), PSYCHO24_EPSILON);
    return max(referenceOneSideRangeLog10 / actualAboveAdaptationRangeLog10,
            PSYCHO24_MIN_AUTO_COMPRESSION);
}

vec3 psycho24_ToAdaptiveRelativeWeightedLMS(vec3 lmsInput, vec3 adaptiveStateLms) {
    return safeDiv(lmsInput * PSYCHO_MB_WEIGHTS, adaptiveStateLms, vec3(0.0));
}

vec3 psycho24_FromAdaptiveRelativeWeightedLMS(vec3 lmsWeightedRelative, vec3 adaptiveStateLms) {
    return lmsWeightedRelative * max(adaptiveStateLms, vec3(PSYCHO24_EPSILON));
}

vec3 psycho24_ApplyAdaptiveMBPurity(vec3 lmsInput, vec3 adaptiveNeutralLms, float purityDelta) {
    if (abs(purityDelta - 1.0) <= 1.0e-5) {
        return lmsInput;
    }
    vec3 relativeWeighted = psycho24_ToAdaptiveRelativeWeightedLMS(lmsInput, adaptiveNeutralLms);
    vec3 mb = psycho23MBFromWeighted(relativeWeighted);
    vec3 mbNeutral = psycho23MBFromWeighted(vec3(1.0) * PSYCHO_MB_WEIGHTS);
    vec2 mbScaledXy = mix(mbNeutral.xy, mb.xy, purityDelta);
    vec3 relativeWeightedOut = psycho23WeightedFromMB(vec3(mbScaledXy, mb.z));
    return psycho23UnweighLMS(
            psycho24_FromAdaptiveRelativeWeightedLMS(relativeWeightedOut, adaptiveNeutralLms));
}

const uint PSYCHO_MANUAL_HUE_COUNT = 23u;
const float PSYCHO_MANUAL_HUE_POSITION[PSYCHO_MANUAL_HUE_COUNT] = float[](
    0.01071375, 0.10705012, 0.12795984, 0.15335225, 0.18766853, 0.22076293,
    0.24936653, 0.27634237, 0.29474511, 0.31129214, 0.35078118, 0.39136371,
    0.47262991, 0.49426816, 0.54698948, 0.60705013, 0.68311772, 0.81129214,
    0.91306421, 0.93498424, 0.94625976, 0.96664602, 0.97262991
);
const float PSYCHO_MANUAL_HUE_X[PSYCHO_MANUAL_HUE_COUNT] = float[](
    0.517681, 0.675575, 0.691365, 0.691365, 0.665049, 0.680839,
    0.661513, 0.654523, 0.648355, 0.640461, 0.556250, 0.519408,
    0.450987, 0.435197, 0.424671, 0.464145, 0.516776, 0.608882,
    0.690461, 0.606250, 0.553618, 0.514145, 0.482566
);

float psycho24_SampleManualHueLinearity(float sourceHuePhase) {
    sourceHuePhase -= floor(sourceHuePhase);
    uint lowerIndex = PSYCHO_MANUAL_HUE_COUNT - 1u;
    for (uint i = 0u; i < PSYCHO_MANUAL_HUE_COUNT; ++i) {
        if (sourceHuePhase >= PSYCHO_MANUAL_HUE_POSITION[i]) {
            lowerIndex = i;
        }
    }
    uint upperIndex = (lowerIndex + 1u) % PSYCHO_MANUAL_HUE_COUNT;
    float lowerPosition = PSYCHO_MANUAL_HUE_POSITION[lowerIndex];
    float upperPosition = upperIndex == 0u
            ? PSYCHO_MANUAL_HUE_POSITION[0] + 1.0
            : PSYCHO_MANUAL_HUE_POSITION[upperIndex];
    if (upperIndex == 0u && sourceHuePhase < lowerPosition) {
        sourceHuePhase += 1.0;
    }
    float t = clamp(safeDiv(sourceHuePhase - lowerPosition,
            upperPosition - lowerPosition, 0.0), 0.0, 1.0);
    return mix(PSYCHO_MANUAL_HUE_X[lowerIndex], PSYCHO_MANUAL_HUE_X[upperIndex], t);
}

vec3 psycho24_ApplyManualHueDirection(vec3 compressedLms, vec3 directionSourceLms,
        vec3 adaptiveStateLms, float toWhiteProgress) {
    vec3 compressedRelativeWeighted = psycho24_ToAdaptiveRelativeWeightedLMS(
            compressedLms, adaptiveStateLms);
    vec3 sourceRelativeWeighted = psycho24_ToAdaptiveRelativeWeightedLMS(
            directionSourceLms, adaptiveStateLms);
    vec3 compressedMb = psycho23MBFromWeighted(compressedRelativeWeighted);
    vec3 sourceMb = psycho23MBFromWeighted(sourceRelativeWeighted);
    vec2 adaptedNeutralMb = psycho23MBFromWeighted(PSYCHO_MB_WEIGHTS).xy;
    vec2 compressedOffset = compressedMb.xy - adaptedNeutralMb;
    vec2 sourceOffset = sourceMb.xy - adaptedNeutralMb;
    float compressedRadius2 = dot(compressedOffset, compressedOffset);
    float sourceRadius2 = dot(sourceOffset, sourceOffset);
    if (compressedRadius2 <= PSYCHO24_EPSILON * PSYCHO24_EPSILON
            || sourceRadius2 <= PSYCHO24_EPSILON * PSYCHO24_EPSILON) {
        return compressedLms;
    }
    float sourceHuePhase = atan(sourceOffset.y, sourceOffset.x) / PSYCHO24_TWO_PI;
    sourceHuePhase -= floor(sourceHuePhase);
    float amount = mix(1.0, psycho24_SampleManualHueLinearity(sourceHuePhase),
            clamp(toWhiteProgress, 0.0, 1.0));
    float compressedRadius = sqrt(compressedRadius2);
    vec2 compressedDirection = compressedOffset / compressedRadius;
    vec2 sourceDirection = sourceOffset * inversesqrt(sourceRadius2);
    vec2 outputDirection = mix(compressedDirection, sourceDirection, amount);
    float outputDirection2 = dot(outputDirection, outputDirection);
    if (outputDirection2 <= PSYCHO24_EPSILON * PSYCHO24_EPSILON) {
        return compressedLms;
    }
    outputDirection *= inversesqrt(outputDirection2);
    vec3 restoredMb = vec3(adaptedNeutralMb + outputDirection * compressedRadius, compressedMb.z);
    vec3 restoredRelativeWeighted = psycho23WeightedFromMB(restoredMb);
    return psycho23UnweighLMS(
            psycho24_FromAdaptiveRelativeWeightedLMS(restoredRelativeWeighted, adaptiveStateLms));
}

vec3 psychov24_linear(vec3 bt709LinearInput, float peakValue, float exposure,
        float compression, float gamutCompression) {
    const float highlights = 1.0;
    const float shadows = 1.0;
    const float contrast = 1.0;
    const float purityScale = 1.0;
    const float hueRestore = 1.0;
    const float adaptationContrast = 1.0;
    const float coneResponseExponent = 1.0;

    float legacyResponseScale = coneResponseExponent * adaptationContrast;
    float adjContrast = contrast * legacyResponseScale;
    float adjPurity = purityScale * legacyResponseScale;

    vec3 lmsIn = psycho23LMSFromBT709(bt709LinearInput * exposure);
    vec3 lmsPeak = psycho23LMSFromBT709(vec3(peakValue));
    vec3 adaptiveStateLms = psycho23LMSFromBT709(vec3(0.18));
    vec3 desiredBackgroundStateLms = adaptiveStateLms;
    vec3 anchorIn = max(adaptiveStateLms, vec3(PSYCHO24_EPSILON));
    vec3 anchorOut = max(desiredBackgroundStateLms, vec3(PSYCHO24_EPSILON));
    float contrastPower = max(adjContrast, PSYCHO24_EPSILON);

    vec3 gradedLms = abs(lmsIn);
    float gradedYf = psycho24_YfFromLMS(gradedLms);
    float adaptedAnchorYf = psycho24_YfFromLMS(anchorIn);
    float gradedYfOut = psycho24_HighlightsScalarV4(gradedYf, highlights, adaptedAnchorYf);
    gradedYfOut = psycho24_ShadowsScalarV4(gradedYfOut, shadows, adaptedAnchorYf);
    gradedLms *= safeDiv(gradedYfOut, gradedYf, 1.0);

    float purityDelta = safeDiv(max(adjPurity, PSYCHO24_EPSILON), contrastPower, 1.0);
    vec3 contrastInput = psycho24_ApplyAdaptiveMBPurity(gradedLms, anchorIn, purityDelta);
    vec3 contrastRatio = max(contrastInput / anchorIn, vec3(PSYCHO24_EPSILON));
    vec3 contrastLms = anchorOut * pow(contrastRatio, vec3(contrastPower));

    float compressionPower = compression;
    if (compression == PSYCHO24_AUTO_COMPRESSION_SENTINEL) {
        compressionPower = psycho24_AutoCompressionFromCenteredReferenceRange(
                psycho24_YfFromLMS(anchorOut), psycho24_YfFromLMS(lmsPeak));
    }
    compressionPower = max(compressionPower, PSYCHO24_MIN_MANUAL_COMPRESSION);

    vec3 anchorOverPeak = anchorOut / max(lmsPeak, vec3(PSYCHO24_EPSILON));
    vec3 compressionSlopeNorm = 1.0
            - pow(max(anchorOverPeak, vec3(PSYCHO24_EPSILON)), vec3(compressionPower));
    vec3 compressionInput = pow(max(contrastLms / anchorOut, vec3(PSYCHO24_EPSILON)),
            vec3(compressionPower) / max(compressionSlopeNorm, vec3(PSYCHO24_EPSILON)));
    vec3 compressionWhiteOffset = pow(max(lmsPeak / anchorOut, vec3(PSYCHO24_EPSILON)),
            vec3(compressionPower)) - vec3(1.0);
    vec3 compressionRolloff = pow(
            compressionInput / max(compressionInput + compressionWhiteOffset,
                    vec3(PSYCHO24_EPSILON)), vec3(1.0 / compressionPower));
    vec3 compressedLms = lmsPeak * compressionRolloff;
    float toWhiteProgress = max(compressionRolloff.x, max(compressionRolloff.y, compressionRolloff.z));

    vec3 hueRestoredLms = psycho24_ApplyManualHueDirection(
            compressedLms, contrastInput, adaptiveStateLms, toWhiteProgress);
    vec3 displayScaled = psycho23CopySign(hueRestoredLms, lmsIn);
    vec3 displayScaledRelativeWeighted = psycho24_ToAdaptiveRelativeWeightedLMS(
            displayScaled, adaptiveStateLms);
    displayScaledRelativeWeighted = psycho23GamutCompressAdaptive(
            displayScaledRelativeWeighted, adaptiveStateLms, gamutCompression);

    vec3 outputLms = psycho23UnweighLMS(
            psycho24_FromAdaptiveRelativeWeightedLMS(displayScaledRelativeWeighted, adaptiveStateLms));
    return psycho23BT709FromLMS(outputLms);
}

vec3 tonemapSdrPsychoV24(vec3 color) {
    return clamp(psychov24_linear(color, 1.0, 1.0, 1.2, 0.0), 0.0, 1.0);
}

vec3 tonemapHdrPsychoV24(vec3 hdr, float exposure) {
    vec3 paperReferred709 = max(hdr * exposure, vec3(0.0));
    vec3 mapped709 = max(psychov24_linear(paperReferred709, 1.0, 1.0, 1.5, 1.0), vec3(0.0));
    vec3 mapped2020 = max(BT709_TO_BT2020 * mapped709, vec3(0.0));
    return pqEncodeNits(mapped2020 * pc.paperWhiteNits);
}

#endif
