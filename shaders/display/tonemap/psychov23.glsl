#ifndef CAUSTICA_TONEMAP_PSYCHOV23_GLSL
#define CAUSTICA_TONEMAP_PSYCHOV23_GLSL

#include "psycho_common.glsl"

const float PSYCHO23_EPSILON = 1.0e-6;
const float PSYCHO23_REFERENCE_SIMULTANEOUS_RANGE_LOG10 = 3.7;
const float PSYCHO23_REFERENCE_CENTERED_RANGE_SIDE_COUNT = 2.0;
const float PSYCHO23_HIGHLIGHT_GRADE_REFERENCE_WHITE = 1.0;
const float PSYCHO23_SHADOW_GRADE_RANGE_STOPS = 4.0;
const float PSYCHO23_RED_RETENTION = 1.5;
const float PSYCHO23_GREEN_RETENTION = 2.0;
const float PSYCHO23_BLUE_RETENTION = 1.0;
const float PSYCHO23_YELLOW_RETENTION = 3.0;

float psycho23YfFromLMS(vec3 lms) {
    vec3 weighted = lms * PSYCHO_MB_WEIGHTS;
    return max(weighted.x + weighted.y, PSYCHO23_EPSILON);
}

float psycho23QuinticUnitRamp(float t) {
    t = clamp(t, 0.0, 1.0);
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

float psycho23HighlightsScalarV4(float x, float highlights, float adaptedAnchorYf) {
    if (highlights == 1.0) {
        return x;
    }
    float t = 0.0;
    if (x > adaptedAnchorYf) {
        float referenceRangeLog2 = log2(PSYCHO23_HIGHLIGHT_GRADE_REFERENCE_WHITE
                / max(adaptedAnchorYf, PSYCHO23_EPSILON));
        t = clamp(log2(x / max(adaptedAnchorYf, PSYCHO23_EPSILON))
                / max(referenceRangeLog2, PSYCHO23_EPSILON), 0.0, 1.0);
    }
    t = psycho23QuinticUnitRamp(t);
    float ratio = max(x / max(adaptedAnchorYf, PSYCHO23_EPSILON), PSYCHO23_EPSILON);
    if (highlights > 1.0) {
        return mix(x, adaptedAnchorYf * pow(ratio, highlights), t);
    }
    float b = adaptedAnchorYf * pow(ratio, 2.0 - highlights);
    return safeDiv(x * x, mix(x, b, t), x);
}

float psycho23ShadowsScalarV4(float x, float shadows, float adaptedAnchorYf) {
    if (shadows == 1.0) {
        return x;
    }
    float ratio = max(safeDiv(x, adaptedAnchorYf, 0.0), 0.0);
    float baseTerm = x * adaptedAnchorYf;
    float baseScale = safeDiv(baseTerm, ratio, 0.0);
    float shadowFloor = adaptedAnchorYf * exp2(-PSYCHO23_SHADOW_GRADE_RANGE_STOPS);
    float t = 1.0;
    if (x > shadowFloor) {
        t = clamp(log2(x / max(adaptedAnchorYf, PSYCHO23_EPSILON))
                / log2(shadowFloor / max(adaptedAnchorYf, PSYCHO23_EPSILON)), 0.0, 1.0);
    }
    t = psycho23QuinticUnitRamp(t);
    if (shadows > 1.0) {
        float raised = x * (1.0 + safeDiv(baseTerm,
                pow(max(ratio, PSYCHO23_EPSILON), shadows), 0.0));
        float reference = x * (1.0 + baseScale);
        return x + (raised - reference) * t;
    }
    float lowered = x * (1.0 - safeDiv(baseTerm,
            pow(max(ratio, PSYCHO23_EPSILON), 2.0 - shadows), 0.0));
    float reference = x * (1.0 - baseScale);
    return x + (lowered - reference) * t;
}

float psycho23AutoCompression(float anchorOutYf, float peakYf) {
    float peakOverAnchor = safeDiv(max(peakYf, PSYCHO23_EPSILON),
            max(anchorOutYf, PSYCHO23_EPSILON), 1.0);
    peakOverAnchor = max(peakOverAnchor, 1.0 + PSYCHO23_EPSILON);
    float referenceOneSideRangeLog10 = PSYCHO23_REFERENCE_SIMULTANEOUS_RANGE_LOG10
            / PSYCHO23_REFERENCE_CENTERED_RANGE_SIDE_COUNT;
    float actualAboveAdaptationRangeLog10 = max(log(peakOverAnchor) / log(10.0), PSYCHO23_EPSILON);
    return max(referenceOneSideRangeLog10 / actualAboveAdaptationRangeLog10, 1.0);
}

vec3 psycho23ToAdaptiveRelativeWeightedLMS(vec3 lmsInput, vec3 adaptiveStateLms) {
    return (lmsInput * PSYCHO_MB_WEIGHTS) / max(adaptiveStateLms, vec3(PSYCHO23_EPSILON));
}

vec3 psycho23FromAdaptiveRelativeWeightedLMS(vec3 lmsWeightedRelative, vec3 adaptiveStateLms) {
    return lmsWeightedRelative * max(adaptiveStateLms, vec3(PSYCHO23_EPSILON));
}

vec3 psycho23MBFromWeighted(vec3 weightedLms) {
    float y = weightedLms.x + weightedLms.y;
    if (y <= PSYCHO23_EPSILON) {
        return vec3(0.0);
    }
    return vec3(weightedLms.x / y, weightedLms.z / y, y);
}

vec3 psycho23WeightedFromMB(vec3 mb) {
    return vec3(mb.x, 1.0 - mb.x, mb.y) * mb.z;
}

vec3 psycho23UnweighLMS(vec3 weightedLms) {
    return weightedLms / max(PSYCHO_MB_WEIGHTS, vec3(PSYCHO23_EPSILON));
}

vec3 psycho23AdaptiveRelativeWeightedNeutral() {
    return PSYCHO_MB_WEIGHTS;
}

vec3 psycho23OpponentACCFromWeightedDelta(vec3 deltaWeightedLms) {
    vec3 neutralWeighted = psycho23AdaptiveRelativeWeightedNeutral();
    float mToL = safeDiv(neutralWeighted.x, neutralWeighted.y, 0.0);
    float sToLm = safeDiv(neutralWeighted.x + neutralWeighted.y, neutralWeighted.z, 0.0);
    return vec3(deltaWeightedLms.x + deltaWeightedLms.y,
            deltaWeightedLms.x - mToL * deltaWeightedLms.y,
            -deltaWeightedLms.x - deltaWeightedLms.y + sToLm * deltaWeightedLms.z);
}

vec3 psycho23WeightedDeltaFromOpponentACC(vec3 acc) {
    vec3 neutralWeighted = psycho23AdaptiveRelativeWeightedNeutral();
    float mToL = safeDiv(neutralWeighted.x, neutralWeighted.y, 0.0);
    float sToLm = safeDiv(neutralWeighted.x + neutralWeighted.y, neutralWeighted.z, 0.0);
    float deltaM = safeDiv(acc.x - acc.y, 1.0 + mToL, 0.0);
    float deltaL = acc.x - deltaM;
    float deltaS = safeDiv(acc.z + acc.x, sToLm, 0.0);
    return vec3(deltaL, deltaM, deltaS);
}

float psycho23CompressionProgressFromYf(vec3 contrastLms, vec3 anchorOut,
        vec3 peakLms, float compressionPower) {
    float contrastYf = psycho23YfFromLMS(contrastLms);
    float anchorYf = psycho23YfFromLMS(anchorOut);
    float peakYf = psycho23YfFromLMS(peakLms);
    float anchorOverPeak = safeDiv(anchorYf, peakYf, 1.0);
    float slopeNormalization = 1.0 - pow(max(anchorOverPeak, PSYCHO23_EPSILON), compressionPower);
    float u = pow(max(safeDiv(contrastYf, anchorYf, 1.0), PSYCHO23_EPSILON),
            compressionPower / max(slopeNormalization, PSYCHO23_EPSILON));
    float whiteOffset = pow(max(safeDiv(peakYf, anchorYf, 1.0), PSYCHO23_EPSILON),
            compressionPower) - 1.0;
    return clamp((u - 1.0) / max(u + whiteOffset, PSYCHO23_EPSILON), 0.0, 1.0);
}

float psycho23SignedOpponentRetention(float whiteProgress, float retentionExponent) {
    return 1.0 - pow(clamp(whiteProgress, 0.0, 1.0),
            max(retentionExponent, PSYCHO23_EPSILON));
}

vec3 psycho23ApplySignedOpponentRetention(vec3 compressedLms, vec3 sourceLms,
        vec3 adaptiveStateLms, vec3 peakLms, float whiteProgress) {
    if (whiteProgress <= 0.0 || min(sourceLms.x, min(sourceLms.y, sourceLms.z)) <= 0.0) {
        return compressedLms;
    }
    vec3 sourceWeighted = psycho23ToAdaptiveRelativeWeightedLMS(sourceLms, adaptiveStateLms);
    vec3 adaptedNeutral = psycho23AdaptiveRelativeWeightedNeutral();
    float adaptedNeutralYf = adaptedNeutral.x + adaptedNeutral.y;
    float sourceYf = sourceWeighted.x + sourceWeighted.y;
    if (sourceYf <= PSYCHO23_EPSILON || adaptedNeutralYf <= PSYCHO23_EPSILON) {
        return compressedLms;
    }
    vec3 sourceNeutral = adaptedNeutral * safeDiv(sourceYf, adaptedNeutralYf, 1.0);
    vec3 sourceDelta = sourceWeighted - sourceNeutral;
    vec3 sourceAcc = psycho23OpponentACCFromWeightedDelta(sourceDelta) / sourceYf;
    float redRetention = psycho23SignedOpponentRetention(whiteProgress, PSYCHO23_RED_RETENTION);
    float greenRetention = psycho23SignedOpponentRetention(whiteProgress, PSYCHO23_GREEN_RETENTION);
    float blueRetention = psycho23SignedOpponentRetention(whiteProgress, PSYCHO23_BLUE_RETENTION);
    float yellowRetention = psycho23SignedOpponentRetention(whiteProgress, PSYCHO23_YELLOW_RETENTION);
    float rgOut = max(sourceAcc.y, 0.0) * redRetention
            - max(-sourceAcc.y, 0.0) * greenRetention;
    float yvOut = max(sourceAcc.z, 0.0) * blueRetention
            - max(-sourceAcc.z, 0.0) * yellowRetention;
    vec3 compressedWeighted = psycho23ToAdaptiveRelativeWeightedLMS(compressedLms, adaptiveStateLms);
    float targetYf = compressedWeighted.x + compressedWeighted.y;
    if (targetYf <= PSYCHO23_EPSILON) {
        return compressedLms;
    }
    vec3 peakWeighted = psycho23ToAdaptiveRelativeWeightedLMS(peakLms, adaptiveStateLms);
    float peakWeightedYf = peakWeighted.x + peakWeighted.y;
    if (peakWeightedYf <= PSYCHO23_EPSILON) {
        return compressedLms;
    }
    vec3 targetNeutral = peakWeighted * safeDiv(targetYf, peakWeightedYf, 1.0);
    vec3 targetDelta = psycho23WeightedDeltaFromOpponentACC(
            vec3(0.0, rgOut * targetYf, yvOut * targetYf));
    vec3 outputWeighted = targetNeutral + targetDelta;
    vec3 outputLms = psycho23UnweighLMS(
            psycho23FromAdaptiveRelativeWeightedLMS(outputWeighted, adaptiveStateLms));
    float compressedYf = psycho23YfFromLMS(compressedLms);
    float outputYf = psycho23YfFromLMS(outputLms);
    if (outputYf <= PSYCHO23_EPSILON) {
        return compressedLms;
    }
    return outputLms * safeDiv(compressedYf, outputYf, 1.0);
}

vec3 psycho23ApplyAdaptiveMBPurity(vec3 lmsInput, vec3 adaptiveNeutralLms,
        float purityDelta) {
    if (abs(purityDelta - 1.0) <= 1.0e-5) {
        return lmsInput;
    }
    vec3 relativeWeighted = psycho23ToAdaptiveRelativeWeightedLMS(lmsInput, adaptiveNeutralLms);
    vec3 mb = psycho23MBFromWeighted(relativeWeighted);
    vec3 mbNeutral = psycho23MBFromWeighted(vec3(1.0) * PSYCHO_MB_WEIGHTS);
    vec2 mbScaledXy = mix(mbNeutral.xy, mb.xy, purityDelta);
    vec3 relativeWeightedOut = psycho23WeightedFromMB(vec3(mbScaledXy, mb.z));
    return psycho23UnweighLMS(
            psycho23FromAdaptiveRelativeWeightedLMS(relativeWeightedOut, adaptiveNeutralLms));
}

vec3 psycho23GamutCompressAdaptive(vec3 relativeWeighted, vec3 adaptiveStateLms, float strength) {
    if (strength == 0.0) {
        return relativeWeighted;
    }
    vec3 weightedAbsolute = psycho23FromAdaptiveRelativeWeightedLMS(relativeWeighted, adaptiveStateLms);
    vec3 absoluteLms = psycho23UnweighLMS(weightedAbsolute);
    vec3 compressedLms = psychoGamutCompress(absoluteLms);
    vec3 compressedRelative = (compressedLms * PSYCHO_MB_WEIGHTS)
            / max(adaptiveStateLms, vec3(PSYCHO23_EPSILON));
    return mix(relativeWeighted, compressedRelative, clamp(strength, 0.0, 1.0));
}

vec3 psycho23LMSFromBT709(vec3 bt709) {
    return psychoLMSFromBT2020(BT709_TO_BT2020 * bt709);
}

vec3 psycho23BT709FromLMS(vec3 lms) {
    return BT2020_TO_BT709 * psychoBT2020FromLMS(lms);
}

vec3 psycho23CopySign(vec3 magnitude, vec3 source) {
    return vec3(source.x < 0.0 ? -abs(magnitude.x) : abs(magnitude.x),
            source.y < 0.0 ? -abs(magnitude.y) : abs(magnitude.y),
            source.z < 0.0 ? -abs(magnitude.z) : abs(magnitude.z));
}

vec3 psychoV23Linear(vec3 color, float peakValue, float compression, float gamutCompression) {
    const float highlights = 1.0;
    const float shadows = 1.0;
    const float contrast = 1.0;
    const float purityScale = 1.0;
    const float hueRestore = 1.0;
    const float adaptationContrast = 1.0;
    const float coneResponseExponent = 1.0;

    float legacyResponseScale = max(coneResponseExponent * adaptationContrast, 0.0);
    float adjContrast = contrast * legacyResponseScale;
    float adjPurity = purityScale * legacyResponseScale;

    vec3 lmsIn = psycho23LMSFromBT709(color);
    vec3 lmsPeak = psycho23LMSFromBT709(vec3(peakValue));
    vec3 adaptiveStateLms = psycho23LMSFromBT709(vec3(0.18));
    vec3 anchorIn = max(adaptiveStateLms, vec3(PSYCHO23_EPSILON));
    vec3 anchorOut = max(adaptiveStateLms, vec3(PSYCHO23_EPSILON));

    vec3 gradedLms = abs(lmsIn);
    float gradedYf = psycho23YfFromLMS(gradedLms);
    float adaptedAnchorYf = psycho23YfFromLMS(anchorIn);
    float gradedYfOut = psycho23HighlightsScalarV4(gradedYf, highlights, adaptedAnchorYf);
    gradedYfOut = psycho23ShadowsScalarV4(gradedYfOut, shadows, adaptedAnchorYf);
    gradedLms *= safeDiv(gradedYfOut, gradedYf, 1.0);

    float contrastPower = max(adjContrast, PSYCHO23_EPSILON);
    float purityDelta = safeDiv(max(adjPurity, PSYCHO23_EPSILON), contrastPower, 1.0);
    vec3 contrastInput = psycho23ApplyAdaptiveMBPurity(gradedLms, anchorIn, purityDelta);
    vec3 contrastRatio = max(contrastInput / anchorIn, vec3(PSYCHO23_EPSILON));
    vec3 contrastLms = anchorOut * pow(contrastRatio, vec3(contrastPower));

    float compressionPower = compression;
    if (compression <= PSYCHO23_EPSILON) {
        compressionPower = psycho23AutoCompression(
                psycho23YfFromLMS(anchorOut), psycho23YfFromLMS(lmsPeak));
    }
    compressionPower = max(compressionPower, PSYCHO23_EPSILON);
    vec3 anchorOverPeak = anchorOut / max(lmsPeak, vec3(PSYCHO23_EPSILON));
    vec3 compressionSlopeNorm = 1.0
            - pow(max(anchorOverPeak, vec3(PSYCHO23_EPSILON)), vec3(compressionPower));
    vec3 compressionInput = pow(max(contrastLms / anchorOut, vec3(PSYCHO23_EPSILON)),
            vec3(compressionPower) / max(compressionSlopeNorm, vec3(PSYCHO23_EPSILON)));
    vec3 compressionWhiteOffset = pow(max(lmsPeak / anchorOut, vec3(PSYCHO23_EPSILON)),
            vec3(compressionPower)) - vec3(1.0);
    vec3 compressedLms = lmsPeak * pow(
            compressionInput / max(compressionInput + compressionWhiteOffset,
                    vec3(PSYCHO23_EPSILON)), vec3(1.0 / compressionPower));

    float whiteProgress = psycho23CompressionProgressFromYf(
            contrastLms, anchorOut, lmsPeak, compressionPower);
    vec3 opponentRetainedLms = psycho23ApplySignedOpponentRetention(
            compressedLms, contrastLms, adaptiveStateLms, lmsPeak, whiteProgress);
    vec3 hueRestoredLms = mix(compressedLms, opponentRetainedLms, clamp(hueRestore, 0.0, 1.0));
    vec3 displayScaled = psycho23CopySign(hueRestoredLms, lmsIn);
    vec3 displayScaledRelativeWeighted = psycho23ToAdaptiveRelativeWeightedLMS(
            displayScaled, adaptiveStateLms);
    displayScaledRelativeWeighted = psycho23GamutCompressAdaptive(
            displayScaledRelativeWeighted, adaptiveStateLms, gamutCompression);

    vec3 outputLms = psycho23UnweighLMS(
            psycho23FromAdaptiveRelativeWeightedLMS(displayScaledRelativeWeighted, adaptiveStateLms));
    return psycho23BT709FromLMS(outputLms);
}

vec3 tonemapSdrPsychoV23(vec3 color) {
    return clamp(psychoV23Linear(color, 1.0, 1.2, 1.0), 0.0, 1.0);
}

vec3 tonemapHdrPsychoV23(vec3 hdr, float exposure) {
    vec3 paperReferred709 = max(hdr * exposure, vec3(0.0));
    vec3 mapped709 = max(psychoV23Linear(paperReferred709, 1.0, 1.5, 1.0), vec3(0.0));
    vec3 mapped2020 = max(BT709_TO_BT2020 * mapped709, vec3(0.0));
    return pqEncodeNits(mapped2020 * pc.paperWhiteNits);
}

#endif
