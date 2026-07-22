package dev.comfyfluffy.caustica.rt;

/** Bounded priority ordering for interactive terrain work without starving older streaming work. */
public final class TerrainJobOrder {
    static final long INTERACTIVE_SEQUENCE_BOOST = 64L;

    private TerrainJobOrder() {
    }

    public static long orderKey(boolean interactive, long sequence) {
        if (sequence <= 0L) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        return interactive ? Math.max(0L, sequence - INTERACTIVE_SEQUENCE_BOOST) : sequence;
    }

    public static int compare(boolean leftInteractive, long leftSequence,
                              boolean rightInteractive, long rightSequence) {
        int keyOrder = Long.compare(orderKey(leftInteractive, leftSequence),
                orderKey(rightInteractive, rightSequence));
        return keyOrder != 0 ? keyOrder : Long.compare(leftSequence, rightSequence);
    }
}
