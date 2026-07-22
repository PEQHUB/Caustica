package dev.comfyfluffy.caustica.rt.terrain;

import java.util.Queue;

/** Limits interactive completion publication to preserve ordinary terrain progress. */
final class TerrainCompletionFairness<T> {
    static final int MAX_INTERACTIVE_STREAK = 4;
    private int interactiveStreak;

    T poll(Queue<T> interactiveQueue, Queue<T> normalQueue) {
        boolean normalAvailable = !normalQueue.isEmpty();
        if (!interactiveQueue.isEmpty()
                && (!normalAvailable || interactiveStreak < MAX_INTERACTIVE_STREAK)) {
            T interactive = interactiveQueue.poll();
            if (interactive != null) {
                interactiveStreak = Math.min(MAX_INTERACTIVE_STREAK, interactiveStreak + 1);
                return interactive;
            }
        }
        T normal = normalQueue.poll();
        if (normal != null) {
            interactiveStreak = 0;
            return normal;
        }
        T interactive = interactiveQueue.poll();
        if (interactive != null) {
            interactiveStreak = Math.min(MAX_INTERACTIVE_STREAK, interactiveStreak + 1);
        }
        return interactive;
    }

    void reset() {
        interactiveStreak = 0;
    }
}
