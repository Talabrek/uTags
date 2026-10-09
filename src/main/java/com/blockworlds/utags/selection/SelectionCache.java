package com.blockworlds.utags.selection;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The in-memory selection of each online player. A join load reads the database in the background; if the player
 * picks a tag before that read finishes, the load must not overwrite the pick. Every local change bumps the player's
 * version and marks the field it touched; {@link #completeLoad} keeps the touched fields when the version moved since
 * {@link #beginLoad}. All methods are synchronized (a few map operations, called off and on the main thread).
 */
public final class SelectionCache {

    private static final class State {
        Selection selection = Selection.NONE;
        long version;
        boolean prefixTouched;
        boolean suffixTouched;
    }

    private final Map<UUID, State> states = new HashMap<>();

    public synchronized Selection get(UUID player) {
        State s = states.get(player);
        return s == null ? Selection.NONE : s.selection;
    }

    /** Call before reading the database; pass the result to {@link #completeLoad}. */
    public synchronized long beginLoad(UUID player) {
        return states.computeIfAbsent(player, k -> new State()).version;
    }

    /** Stores a loaded selection unless the player changed a field meanwhile; returns what is cached now. */
    public synchronized Selection completeLoad(UUID player, long token, Selection loaded) {
        State s = states.computeIfAbsent(player, k -> new State());
        if (s.version == token) {
            s.selection = loaded;
        } else {
            s.selection = new Selection(
                    s.prefixTouched ? s.selection.prefixTag() : loaded.prefixTag(),
                    s.suffixTouched ? s.selection.suffixTag() : loaded.suffixTag());
        }
        s.prefixTouched = false;
        s.suffixTouched = false;
        return s.selection;
    }

    /** A change the player just made (tag may be null to clear). Its database write has completed. */
    public synchronized void setLocal(UUID player, boolean prefix, String tag) {
        State s = states.computeIfAbsent(player, k -> new State());
        s.version++;
        if (prefix) {
            s.prefixTouched = true;
            s.selection = new Selection(tag, s.selection.suffixTag());
        } else {
            s.suffixTouched = true;
            s.selection = new Selection(s.selection.prefixTag(), tag);
        }
    }

    public synchronized void remove(UUID player) {
        states.remove(player);
    }
}
