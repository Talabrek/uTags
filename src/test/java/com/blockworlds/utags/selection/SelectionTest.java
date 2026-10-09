package com.blockworlds.utags.selection;

import com.blockworlds.utags.SqlDialect;
import com.blockworlds.utags.storage.Migrations;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionTest {

    // a fake LuckPerms lookup: tag display -> tag name
    private static final Function<String, String> TAGS = Map.of(
            "&8[&6VIP&8]", "vip",
            "&c[Staff]", "staff",
            "&7[Fan]", "fan")::get;

    private static Selection derive(String prefix, String suffix) {
        return Selection.deriveFromLuckPerms(new Selection.LuckPermsValues(prefix, suffix), TAGS);
    }

    @Test
    void thePrefixIsMatchedWithItsTrailingNameColourCut() {
        assertEquals("vip", derive("&8[&6VIP&8]§f", null).prefixTag());
        assertEquals("vip", derive("&8[&6VIP&8]&a", null).prefixTag());
        assertEquals("staff", derive("&c[Staff]§e", null).prefixTag());
    }

    @Test
    void aPrefixWithoutATrailingCodeIsMatchedWhole() {
        assertEquals("fan", derive("&7[Fan]", null).prefixTag());
    }

    @Test
    void unknownEmptyOrTooShortValuesGiveNoTag() {
        assertNull(derive("&9[Other]§f", null).prefixTag());
        assertNull(derive(null, null).prefixTag());
        assertNull(derive("", null).prefixTag());
        assertNull(derive("§f", null).prefixTag());
        assertEquals(Selection.NONE, Selection.deriveFromLuckPerms(null, TAGS));
    }

    @Test
    void theSuffixIsTheDisplayItself() {
        assertEquals("fan", derive(null, "&7[Fan]").suffixTag());
        assertNull(derive(null, "&7[Nope]").suffixTag());
        Selection both = derive("&8[&6VIP&8]§f", "&7[Fan]");
        assertEquals(new Selection("vip", "fan"), both);
    }

    @Test
    void theStoreCreatesUpdatesAndNeverDuplicatesARow() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            Migrations.run(c, SqlDialect.SQLITE, Migrations.core(), 0, Logger.getAnonymousLogger());
            UUID player = UUID.randomUUID();
            assertNull(SelectionStore.find(c, player));

            // the upgrade inserts once; a second server's insert is told the row exists
            assertTrue(SelectionStore.insertIfAbsent(c, player, new Selection("vip", null)));
            assertFalse(SelectionStore.insertIfAbsent(c, player, new Selection("staff", null)));
            assertEquals(new Selection("vip", null), SelectionStore.find(c, player));

            SelectionStore.set(c, player, false, "fan");
            assertEquals(new Selection("vip", "fan"), SelectionStore.find(c, player));
            SelectionStore.set(c, player, true, "staff");
            assertEquals(new Selection("staff", "fan"), SelectionStore.find(c, player));
            SelectionStore.set(c, player, true, null);
            assertEquals(new Selection(null, "fan"), SelectionStore.find(c, player));

            // set() on a player with no row creates it
            UUID other = UUID.randomUUID();
            SelectionStore.set(c, other, true, "vip");
            assertEquals(new Selection("vip", null), SelectionStore.find(c, other));
        }
    }
}
