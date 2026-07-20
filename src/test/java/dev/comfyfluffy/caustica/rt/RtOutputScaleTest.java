package dev.comfyfluffy.caustica.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class RtOutputScaleTest {
    @Test
    void calculatesRequestedDimensionsWithNearestPixelRounding() {
        int width = 1919;
        int height = 1079;
        assertEquals(192, RtOutputScale.dimension(width, 10));
        assertEquals(108, RtOutputScale.dimension(height, 10));
        assertEquals(960, RtOutputScale.dimension(width, 50));
        assertEquals(540, RtOutputScale.dimension(height, 50));
        assertEquals(1900, RtOutputScale.dimension(width, 99));
        assertEquals(1068, RtOutputScale.dimension(height, 99));
        assertEquals(1919, RtOutputScale.dimension(width, 100));
        assertEquals(1090, RtOutputScale.dimension(height, 101));
        assertEquals(2879, RtOutputScale.dimension(width, 150));
        assertEquals(2158, RtOutputScale.dimension(height, 200));
    }

    @Test
    void clampsRangeAndKeepsAtLeastOnePixel() {
        assertEquals(1, RtOutputScale.dimension(1, 10));
        assertEquals(1, RtOutputScale.dimension(0, 100));
        assertEquals(10, RtOutputScale.clampPercent(-1));
        assertEquals(200, RtOutputScale.clampPercent(999));
    }

    @Test
    void reportsTruthfulPaths() {
        assertEquals("fsr1", RtOutputScale.path(99, false));
        assertEquals("native", RtOutputScale.path(100, false));
        assertEquals("downsample", RtOutputScale.path(101, false));
        assertEquals("linear-fallback", RtOutputScale.path(50, true));
    }

    @Test
    void qualityShortcutsSetAbsoluteInputFromTheOutputRatio() {
        assertEquals(67, RtResolutionScale.shortcutInputPercent(100, 2));
        assertEquals(133, RtResolutionScale.shortcutInputPercent(200, 2));
        assertEquals(50, RtResolutionScale.shortcutInputPercent(50, 5));
    }

    @Test
    void exposesCanonicalDlssPresetPercentages() {
        assertEquals(33, RtResolutionScale.presetPercent(3));
        assertEquals(50, RtResolutionScale.presetPercent(0));
        assertEquals(59, RtResolutionScale.presetPercent(1));
        assertEquals(67, RtResolutionScale.presetPercent(2));
        assertEquals(100, RtResolutionScale.presetPercent(5));
    }

    @Test
    void presetScaleUsesDisplayOverInputRatherThanItsReciprocal() {
        assertEquals(3.0, RtResolutionScale.presetUpscaleRatio(3));
        assertEquals(2.0, RtResolutionScale.presetUpscaleRatio(0));
        assertEquals(1.7, RtResolutionScale.presetUpscaleRatio(1));
        assertEquals(1.5, RtResolutionScale.presetUpscaleRatio(2));
        assertEquals(1.0, RtResolutionScale.presetUpscaleRatio(5));
        assertEquals(1280, RtResolutionScale.presetInputDimension(3840, 3));
        assertEquals(1920, RtResolutionScale.presetInputDimension(3840, 0));
        assertEquals(2560, RtResolutionScale.presetInputDimension(3840, 2));
    }
}
