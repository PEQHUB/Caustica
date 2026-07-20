package dev.comfyfluffy.caustica.rt.terrain;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Render-thread-owned admission tracker for terrain work.
 *
 * <p>A ticket has two deliberately separate lifetimes: cancellation immediately revokes its authority to
 * publish, while retirement releases its admission slot only after the terminal result has been drained.
 * This prevents dirty churn from exceeding the configured outstanding-work ceiling.</p>
 */
final class TerrainTaskTracker {
    static final class Ticket {
        private final long key;
        private final long token;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private boolean retired;

        private Ticket(long key, long token) {
            this.key = key;
            this.token = token;
        }

        long key() {
            return key;
        }

        long token() {
            return token;
        }

        boolean cancelled() {
            return cancelled.get();
        }

        private void cancel() {
            cancelled.set(true);
        }
    }

    private final Long2ObjectOpenHashMap<Ticket> current = new Long2ObjectOpenHashMap<>();
    private int outstanding;

    Ticket accept(long key, long token) {
        if (current.containsKey(key)) {
            throw new IllegalStateException("Terrain section already has current work: " + key);
        }
        Ticket ticket = new Ticket(key, token);
        current.put(key, ticket);
        outstanding++;
        return ticket;
    }

    boolean containsCurrent(long key) {
        return current.containsKey(key);
    }

    boolean isCurrent(Ticket ticket) {
        return !ticket.cancelled() && current.get(ticket.key) == ticket;
    }

    Ticket cancelCurrent(long key) {
        Ticket ticket = current.remove(key);
        if (ticket != null) {
            ticket.cancel();
        }
        return ticket;
    }

    LongArrayList cancelNotIn(LongSet keep) {
        LongArrayList cancelledKeys = new LongArrayList();
        for (LongIterator it = current.keySet().iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            if (!keep.contains(key)) {
                Ticket ticket = current.get(key);
                it.remove();
                ticket.cancel();
                cancelledKeys.add(key);
            }
        }
        return cancelledKeys;
    }

    void cancelAllCurrent() {
        for (Ticket ticket : current.values()) {
            ticket.cancel();
        }
        current.clear();
    }

    /** Render-thread only; returns false when a duplicate terminal path attempted to retire the ticket. */
    boolean retire(Ticket ticket) {
        if (ticket.retired) {
            return false;
        }
        ticket.retired = true;
        if (current.get(ticket.key) == ticket) {
            current.remove(ticket.key);
        }
        if (--outstanding < 0) {
            throw new IllegalStateException("RT terrain outstanding-task underflow");
        }
        return true;
    }

    int outstanding() {
        return outstanding;
    }
}
