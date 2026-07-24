package dev.comfyfluffy.caustica.rt;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Vanilla dimensions with first-class atmosphere editor profiles.
 *
 * A null result from {@link #resolve(Level)} or {@link #resolve(ResourceKey)}
 * means the current dimension is custom/unsupported and should retain the
 * renderer's existing fallback behavior.
 */
public enum AtmosphereDimension {
    OVERWORLD("overworld"),
    NETHER("nether"),
    END("end");

    private final String id;

    AtmosphereDimension(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "caustica.options.atmosphere.dimension." + id;
    }

    public String tooltipKey() {
        return translationKey() + ".tooltip";
    }

    public boolean isSimpleGradient() {
        return this == NETHER || this == END;
    }

    public static AtmosphereDimension resolve(Level level) {
        return level == null ? null : resolve(level.dimension());
    }

    static AtmosphereDimension resolve(ResourceKey<Level> dimensionKey) {
        if (dimensionKey == null) {
            return null;
        }
        if (Level.OVERWORLD.equals(dimensionKey)) {
            return OVERWORLD;
        }
        if (Level.NETHER.equals(dimensionKey)) {
            return NETHER;
        }
        if (Level.END.equals(dimensionKey)) {
            return END;
        }
        return null;
    }

    /**
     * Chooses the initial editor tab. A menu opened outside a world or from a
     * custom dimension starts on the Overworld profile.
     */
    public static AtmosphereDimension editorDefault(Level level) {
        AtmosphereDimension resolved = resolve(level);
        return resolved == null ? OVERWORLD : resolved;
    }
}
