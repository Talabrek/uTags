package com.blockworlds.utags.selection;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The join load against a pick made while it runs, and the two spellings of a colour code. */
class SelectionCacheTest {

    private final UUID player = UUID.randomUUID();
    private final SelectionCache cache = new SelectionCache();

    @Test
    void aLoadWithNoChangeMeanwhileIsStored() {
        long token = cache.beginLoad(player);
        assertEquals(new Selection("a", "b"), cache.completeLoad(player, token, new Selection("a", "b")));
        assertEquals(new Selection("a", "b"), cache.get(player));
    }

    @Test
    void aPickMadeWhileTheJoinLoadRunsIsNotOverwritten() {
        long token = cache.beginLoad(player);
        cache.setLocal(player, true, "new_prefix"); // the player picks while the database read is still running
        Selection cached = cache.completeLoad(player, token, new Selection("old_prefix", "stored_suffix"));
        assertEquals(new Selection("new_prefix", "stored_suffix"), cached, "the pick wins, the other field comes from the database");
        assertEquals(cached, cache.get(player));
    }

    @Test
    void aClearMadeWhileTheJoinLoadRunsIsNotUndone() {
        long token = cache.beginLoad(player);
        cache.setLocal(player, false, null);
        assertEquals(new Selection("p", null), cache.completeLoad(player, token, new Selection("p", "old_suffix")));
    }

    @Test
    void aLaterLoadIsNotBlockedByAnEarlierPick() {
        cache.setLocal(player, true, "x");
        long token = cache.beginLoad(player);
        assertEquals(new Selection("from_db", null), cache.completeLoad(player, token, new Selection("from_db", null)));
    }

    @Test
    void removeForgetsThePlayer() {
        cache.setLocal(player, true, "x");
        cache.remove(player);
        assertEquals(Selection.NONE, cache.get(player));
    }

    @Test
    void sectionAndAmpersandFormsOfAColourCodeAreTheSameValue() {
        assertTrue(SelectionService.sameValue("&a[Tag]&f", "§a[Tag]§f"));
        assertTrue(SelectionService.sameValue("§a[Tag]&f", "&a[Tag]§f"));
        assertFalse(SelectionService.sameValue("&a[Tag]&f", "&a[Tag]&e"));
        assertTrue(SelectionService.sameValue(null, null));
        assertFalse(SelectionService.sameValue(null, "&a"));
    }
}
