package dev.comfyfluffy.caustica.rt.pipeline;

import java.util.ArrayList;
import java.util.List;

/** Chooses only DLSSD input extents that the runtime explicitly advertises as valid. */
public final class DlssdResolutionPlanner {
    private static final int[] ALL_QUALITIES = {3, 0, 1, 2, 5, 4};
    private static final int LOCAL_SEARCH_RADIUS = 48;

    private DlssdResolutionPlanner() {
    }

    @FunctionalInterface
    public interface SettingsQuery {
        Settings query(int quality, int outputWidth, int outputHeight);
    }

    public record Settings(int optimalWidth, int optimalHeight,
                           int minWidth, int minHeight, int maxWidth, int maxHeight) {
        public Settings {
            if (optimalWidth <= 0 || optimalHeight <= 0 || minWidth <= 0 || minHeight <= 0
                    || maxWidth < minWidth || maxHeight < minHeight) {
                throw new IllegalArgumentException("Invalid DLSSD optimal settings");
            }
        }

        boolean contains(int width, int height) {
            return width >= minWidth && width <= maxWidth && height >= minHeight && height <= maxHeight;
        }
    }

    public static DlssdResolutionPlan plan(int requestedWidth, int requestedHeight,
            int finalWidth, int finalHeight, int configuredQuality, SettingsQuery query) {
        if (requestedWidth <= 0 || requestedHeight <= 0 || finalWidth <= 0 || finalHeight <= 0) {
            throw new IllegalArgumentException("Resolution-plan dimensions must be positive");
        }
        List<Integer> qualities = qualityOrder(configuredQuality);
        Candidate best = null;

        for (int quality : qualities) {
            Settings settings = safeQuery(query, quality, finalWidth, finalHeight);
            Candidate direct = candidate(requestedWidth, requestedHeight, finalWidth, finalHeight,
                    finalWidth, finalHeight, quality, settings, true);
            best = better(best, direct, configuredQuality);
        }
        if (best != null && best.stageWidth == finalWidth && best.stageHeight == finalHeight) {
            return best.toPlan(requestedWidth, requestedHeight, finalWidth, finalHeight);
        }

        for (int quality : qualities) {
            Settings atFinal = safeQuery(query, quality, finalWidth, finalHeight);
            if (atFinal == null) continue;
            // DLSSD ranges scale with their declared output. The largest compatible output is where
            // one minimum-input boundary reaches the requested trace extent. Solving from the minimum,
            // rather than the optimal size, also handles modes with a real dynamic-resolution range.
            if (requestedWidth > atFinal.maxWidth() + 1 || requestedHeight > atFinal.maxHeight() + 1) {
                continue;
            }
            double maximumScale = Math.min(requestedWidth / (double)atFinal.minWidth(),
                    requestedHeight / (double)atFinal.minHeight());
            int estimatedHeight = Math.clamp(
                    (int)Math.floor(finalHeight * maximumScale), 1, finalHeight);
            for (int offset = -LOCAL_SEARCH_RADIUS; offset <= LOCAL_SEARCH_RADIUS; offset++) {
                int stageHeight = estimatedHeight + offset;
                if (stageHeight <= 0 || stageHeight > finalHeight) continue;
                int stageWidth = Math.clamp((int)Math.round(finalWidth
                        * (stageHeight / (double)finalHeight)), 1, finalWidth);
                Settings settings = safeQuery(query, quality, stageWidth, stageHeight);
                Candidate intermediate = candidate(requestedWidth, requestedHeight,
                        stageWidth, stageHeight, finalWidth, finalHeight, quality, settings, false);
                best = better(best, intermediate, configuredQuality);
            }
        }

        if (best != null) {
            return best.toPlan(requestedWidth, requestedHeight, finalWidth, finalHeight);
        }
        return new DlssdResolutionPlan(requestedWidth, requestedHeight,
                requestedWidth, requestedHeight, requestedWidth, requestedHeight,
                finalWidth, finalHeight, finalWidth, finalHeight, configuredQuality,
                requestedWidth, requestedHeight, requestedWidth, requestedHeight,
                DlssdResolutionPlan.Path.SPATIAL_FALLBACK,
                "no Streamline quality/output pair accepts the requested trace extent");
    }

    private static Candidate candidate(int requestedWidth, int requestedHeight,
            int stageWidth, int stageHeight, int finalWidth, int finalHeight,
            int quality, Settings settings, boolean direct) {
        if (settings == null) return null;
        for (int deltaY = 0; deltaY <= 1; deltaY++) {
            for (int signY : deltaY == 0 ? new int[] {0} : new int[] {-1, 1}) {
                int height = requestedHeight + signY * deltaY;
                if (height <= 0) continue;
                for (int deltaX = 0; deltaX <= 1; deltaX++) {
                    for (int signX : deltaX == 0 ? new int[] {0} : new int[] {-1, 1}) {
                        int width = requestedWidth + signX * deltaX;
                        if (width <= 0 || !settings.contains(width, height)) continue;
                        long area = (long)stageWidth * stageHeight;
                        int correction = Math.abs(width - requestedWidth) + Math.abs(height - requestedHeight);
                        return new Candidate(width, height, width, height,
                                stageWidth, stageHeight, quality, settings, area, correction,
                                direct && stageWidth == finalWidth && stageHeight == finalHeight);
                    }
                }
            }
        }
        return null;
    }

    private static Candidate better(Candidate current, Candidate candidate, int configuredQuality) {
        if (candidate == null) return current;
        if (current == null) return candidate;
        if (candidate.stageArea != current.stageArea) {
            return candidate.stageArea > current.stageArea ? candidate : current;
        }
        if (candidate.correction != current.correction) {
            return candidate.correction < current.correction ? candidate : current;
        }
        if (candidate.direct != current.direct) return candidate.direct ? candidate : current;
        if (candidate.quality == configuredQuality && current.quality != configuredQuality) return candidate;
        return current;
    }

    private static Settings safeQuery(SettingsQuery query, int quality, int width, int height) {
        try {
            return query.query(quality, width, height);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static List<Integer> qualityOrder(int configuredQuality) {
        List<Integer> result = new ArrayList<>(ALL_QUALITIES.length);
        if (configuredQuality >= 0 && configuredQuality <= 5) result.add(configuredQuality);
        for (int quality : ALL_QUALITIES) if (!result.contains(quality)) result.add(quality);
        return result;
    }

    private record Candidate(int traceWidth, int traceHeight,
                             int allocationWidth, int allocationHeight,
                             int stageWidth, int stageHeight, int quality, Settings settings,
                             long stageArea, int correction, boolean direct) {
        DlssdResolutionPlan toPlan(int requestedWidth, int requestedHeight,
                int finalWidth, int finalHeight) {
            DlssdResolutionPlan.Path path = direct
                    ? DlssdResolutionPlan.Path.DIRECT : DlssdResolutionPlan.Path.INTERMEDIATE;
            return new DlssdResolutionPlan(requestedWidth, requestedHeight, traceWidth, traceHeight,
                    allocationWidth, allocationHeight, stageWidth, stageHeight, finalWidth, finalHeight,
                    quality, settings.minWidth(), settings.minHeight(), settings.maxWidth(), settings.maxHeight(),
                    path, "None");
        }
    }
}
