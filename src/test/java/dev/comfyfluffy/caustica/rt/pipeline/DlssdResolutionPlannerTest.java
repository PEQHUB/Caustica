package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class DlssdResolutionPlannerTest {
    @Test
    void fourKScaleSweepAlwaysProducesAContractValidPlan() {
        int[] percentages = {100, 99, 67, 59, 56, 50, 49, 42, 33, 32, 25, 10};
        for (int percentage : percentages) {
            int requestedWidth = Math.max(1, Math.round(3840 * percentage / 100.0f));
            int requestedHeight = Math.max(1, Math.round(2160 * percentage / 100.0f));
            DlssdResolutionPlan plan = DlssdResolutionPlanner.plan(
                    requestedWidth, requestedHeight, 3840, 2160, 3,
                    DlssdResolutionPlannerTest::fixedPresetQuery);
            assertTrue(plan.usesDlssd(), "expected a DLSSD plan at " + percentage + "%");
            assertTrue(plan.containsTraceExtent(), plan.describe());
            assertTrue(Math.abs(plan.traceWidth() - requestedWidth) <= 1, plan.describe());
            assertTrue(Math.abs(plan.traceHeight() - requestedHeight) <= 1, plan.describe());
            assertTrue(plan.dlssdOutputWidth() <= 3840, plan.describe());
            assertTrue(plan.dlssdOutputHeight() <= 2160, plan.describe());
        }
    }

    @Test
    void knownFourKCasesChooseDirectOrIntermediatePaths() {
        DlssdResolutionPlan fifty = plan4k(50);
        DlssdResolutionPlan fortyTwo = plan4k(42);
        DlssdResolutionPlan thirtyThree = plan4k(33);
        DlssdResolutionPlan twentyFive = plan4k(25);
        assertEquals(DlssdResolutionPlan.Path.DIRECT, fifty.path());
        assertEquals(DlssdResolutionPlan.Path.INTERMEDIATE, fortyTwo.path());
        assertEquals(DlssdResolutionPlan.Path.INTERMEDIATE, thirtyThree.path());
        assertEquals(DlssdResolutionPlan.Path.INTERMEDIATE, twentyFive.path());
        assertTrue(thirtyThree.dlssdOutputWidth() > thirtyThree.traceWidth());
        assertNotEquals(1280, thirtyThree.traceWidth());
    }

    @Test
    void oddPortraitOutputPreservesRequestedTraceWithinOnePixel() {
        DlssdResolutionPlan plan = DlssdResolutionPlanner.plan(
                481, 853, 1441, 2559, 3, DlssdResolutionPlannerTest::fixedPresetQuery);
        assertTrue(plan.usesDlssd(), plan.describe());
        assertTrue(plan.containsTraceExtent(), plan.describe());
        assertTrue(Math.abs(plan.traceWidth() - 481) <= 1);
        assertTrue(Math.abs(plan.traceHeight() - 853) <= 1);
    }

    @Test
    void unavailableRuntimeProducesExplicitSpatialFallback() {
        DlssdResolutionPlan plan = DlssdResolutionPlanner.plan(960, 540, 3840, 2160, 3,
                (quality, width, height) -> null);
        assertEquals(DlssdResolutionPlan.Path.SPATIAL_FALLBACK, plan.path());
        assertTrue(plan.reason().contains("no Streamline"));
    }

    private static DlssdResolutionPlan plan4k(int percentage) {
        return DlssdResolutionPlanner.plan(Math.round(3840 * percentage / 100.0f),
                Math.round(2160 * percentage / 100.0f), 3840, 2160, 3,
                DlssdResolutionPlannerTest::fixedPresetQuery);
    }

    private static DlssdResolutionPlanner.Settings fixedPresetQuery(
            int quality, int outputWidth, int outputHeight) {
        double ratio = switch (quality) {
            case 3 -> 3.0;
            case 0 -> 2.0;
            case 1 -> 1.7;
            case 2 -> 1.5;
            case 5, 4 -> 1.0;
            default -> throw new IllegalArgumentException("quality");
        };
        int width = Math.max(1, (int)Math.round(outputWidth / ratio));
        int height = Math.max(1, (int)Math.round(outputHeight / ratio));
        return new DlssdResolutionPlanner.Settings(width, height, width, height, width, height);
    }
}
