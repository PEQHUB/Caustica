package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RtExposurePercentileTest {

    private record Percentiles(float low, float high) {}

    private static float sanitizePercentile(float value, float fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return (float) Math.clamp(value, 0.0, 1.0);
    }

    private static Percentiles sanitize(float low, float high) {
        low = sanitizePercentile(low, 0.50f);
        high = sanitizePercentile(high, 0.95f);
        if (high < low) {
            float tmp = low;
            low = high;
            high = tmp;
        }
        if (high <= low) {
            high = Math.min(low + 1.0f, 1.0f);
        }
        return new Percentiles(low, high);
    }

    @Test
    void defaultsStay050And095() {
        var p = sanitize(0.50f, 0.95f);
        assertEquals(0.50f, p.low(), 1.0e-6f);
        assertEquals(0.95f, p.high(), 1.0e-6f);
    }

    @Test
    void valuesClampTo01() {
        var p = sanitize(-0.5f, 2.0f);
        assertEquals(0.0f, p.low(), 1.0e-6f);
        assertEquals(1.0f, p.high(), 1.0e-6f);
    }

    @Test
    void reversedValuesReorder() {
        var p = sanitize(0.90f, 0.10f);
        assertEquals(0.10f, p.low(), 1.0e-6f);
        assertEquals(0.90f, p.high(), 1.0e-6f);
    }

    @Test
    void equalValuesFormNonemptyWindow() {
        var p = sanitize(0.50f, 0.50f);
        assertEquals(0.50f, p.low(), 1.0e-6f);
        assertTrue(p.high() > p.low(), "high must exceed low when inputs are equal");
        assertTrue(p.high() <= 1.0f, "high must stay within [0,1]");
    }

    @Test
    void nanInputsFallBackToDefaults() {
        var p = sanitize(Float.NaN, Float.NaN);
        assertEquals(0.50f, p.low(), 1.0e-6f, "NaN low falls back to default 0.50");
        assertEquals(0.95f, p.high(), 1.0e-6f, "NaN high falls back to default 0.95");
    }

    @Test
    void infiniteInputsFallBackToDefaults() {
        var p = sanitize(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
        assertEquals(0.50f, p.low(), 1.0e-6f, "+Inf low falls back to default 0.50");
        assertEquals(0.95f, p.high(), 1.0e-6f, "-Inf high falls back to default 0.95");
    }
}
