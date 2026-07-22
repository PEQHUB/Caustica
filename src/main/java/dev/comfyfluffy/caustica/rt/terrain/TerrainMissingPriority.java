package dev.comfyfluffy.caustica.rt.terrain;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/** Tracks which missing sections were promoted by a dirty edit. */
final class TerrainMissingPriority {
    private final LongOpenHashSet interactive = new LongOpenHashSet();

    void mark(long key, boolean interactive) {
        if (interactive) {
            this.interactive.add(key);
        }
    }

    boolean contains(long key) {
        return interactive.contains(key);
    }

    void remove(long key) {
        interactive.remove(key);
    }

    void clear() {
        interactive.clear();
    }
}
