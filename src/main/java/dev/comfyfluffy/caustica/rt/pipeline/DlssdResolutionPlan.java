package dev.comfyfluffy.caustica.rt.pipeline;

import java.util.Objects;

/** Exact trace, DLSSD-stage, and final-output dimensions for one reconstruction configuration. */
public record DlssdResolutionPlan(
        int requestedTraceWidth,
        int requestedTraceHeight,
        int traceWidth,
        int traceHeight,
        int traceAllocationWidth,
        int traceAllocationHeight,
        int dlssdOutputWidth,
        int dlssdOutputHeight,
        int finalOutputWidth,
        int finalOutputHeight,
        int quality,
        int renderWidthMin,
        int renderHeightMin,
        int renderWidthMax,
        int renderHeightMax,
        Path path,
        String reason) {

    public enum Path {
        DIRECT,
        INTERMEDIATE,
        SPATIAL_FALLBACK
    }

    public DlssdResolutionPlan {
        if (requestedTraceWidth <= 0 || requestedTraceHeight <= 0
                || traceWidth <= 0 || traceHeight <= 0
                || traceAllocationWidth < traceWidth || traceAllocationHeight < traceHeight
                || dlssdOutputWidth <= 0 || dlssdOutputHeight <= 0
                || finalOutputWidth <= 0 || finalOutputHeight <= 0) {
            throw new IllegalArgumentException("DLSSD resolution plan dimensions must be positive and contained");
        }
        Objects.requireNonNull(path, "path");
        reason = reason == null || reason.isBlank() ? "None" : reason;
    }

    public boolean usesDlssd() {
        return path != Path.SPATIAL_FALLBACK;
    }

    public boolean needsFinalUpscale() {
        return dlssdOutputWidth != finalOutputWidth || dlssdOutputHeight != finalOutputHeight;
    }

    public boolean containsTraceExtent() {
        return traceWidth >= renderWidthMin && traceWidth <= renderWidthMax
                && traceHeight >= renderHeightMin && traceHeight <= renderHeightMax;
    }

    public String describe() {
        return switch (path) {
            case DIRECT -> "DLSSD direct: " + traceWidth + "x" + traceHeight + " -> "
                    + finalOutputWidth + "x" + finalOutputHeight;
            case INTERMEDIATE -> "DLSSD intermediate: " + traceWidth + "x" + traceHeight + " -> "
                    + dlssdOutputWidth + "x" + dlssdOutputHeight + " -> "
                    + finalOutputWidth + "x" + finalOutputHeight;
            case SPATIAL_FALLBACK -> "Spatial fallback: " + traceWidth + "x" + traceHeight + " -> "
                    + finalOutputWidth + "x" + finalOutputHeight + "; " + reason;
        };
    }
}
