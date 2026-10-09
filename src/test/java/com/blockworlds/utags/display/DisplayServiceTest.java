package com.blockworlds.utags.display;

import com.blockworlds.utags.PlayerTagColorPreference;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisplayServiceTest {

    private static final UUID PLAYER = UUID.randomUUID();
    /** The section-sign white code, as setPlayerTag in 1.3.0 appended it when a player had no name colour. */
    private static final String WHITE = ChatColor.WHITE.toString();
    private static final String S = String.valueOf(ChatColor.COLOR_CHAR);

    private static PlayerTagColorPreference pref(ChatColor bracket, ChatColor content) {
        return new PlayerTagColorPreference(PLAYER, "vip", bracket, content);
    }

    /** What 1.3.0 wrote into LuckPerms: the formatted display, then the name colour code, as plain strings. */
    @Test
    void legacyTagsGiveTheSameLuckPermsPrefixAsBefore() {
        PlayerTagColorPreference none = new PlayerTagColorPreference(PLAYER, "vip");
        assertEquals("&8[&6VIP&8]" + WHITE, DisplayService.lpPrefix("&8[&6VIP&8]", none, WHITE));
        assertEquals("&c[Staff]&a", DisplayService.lpPrefix("&c[Staff]", none, "&a"));
        assertEquals("&7[Fan]" + WHITE, DisplayService.lpPrefix("&7[Fan]", null, WHITE));
        // section-sign input stays as typed
        String sectionTag = S + "8[" + S + "6VIP" + S + "8]";
        assertEquals(sectionTag + WHITE, DisplayService.lpPrefix(sectionTag, none, WHITE));
    }

    @Test
    void colourPreferencesAreAppliedExactlyAsBefore() {
        // brackets red, text default: the bracket colour also colours the text (legacy & codes inside are kept as typed)
        assertEquals("&8" + ChatColor.RED + "[" + ChatColor.RED + "&6VIP&8" + ChatColor.RED + "]" + WHITE,
                DisplayService.lpPrefix("&8[&6VIP&8]", pref(ChatColor.RED, null), WHITE));
        // text blue, brackets default
        assertEquals("&8[" + ChatColor.BLUE + "&6VIP&8]" + WHITE,
                DisplayService.lpPrefix("&8[&6VIP&8]", pref(null, ChatColor.BLUE), WHITE));
        // no brackets: unchanged
        assertEquals("&aVIP" + WHITE, DisplayService.lpPrefix("&aVIP", pref(ChatColor.RED, ChatColor.BLUE), WHITE));
    }

    @Test
    void miniMessageTagsGiveTheSameStringAsTheEquivalentLegacyTag() {
        String legacy = "&8[&6VIP&8]";
        String mini = "<dark_gray>[<gold>VIP<dark_gray>]";
        assertEquals(legacy, DisplayService.toLegacy(mini));
        PlayerTagColorPreference none = new PlayerTagColorPreference(PLAYER, "vip");
        assertEquals(DisplayService.lpPrefix(legacy, none, WHITE), DisplayService.lpPrefix(mini, none, WHITE));
        // and with colour preferences
        PlayerTagColorPreference red = pref(ChatColor.RED, null);
        assertEquals(DisplayService.lpPrefix(legacy, red, "&a"), DisplayService.lpPrefix(mini, red, "&a"));
        assertEquals(DisplayService.formatTagDisplayWithColor("&c[Staff]", pref(null, ChatColor.GREEN)),
                DisplayService.formatTagDisplayWithColor("<red>[Staff]", pref(null, ChatColor.GREEN)));
    }

    @Test
    void legacyAndMiniMessageGiveTheSameComponentText() {
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
        String sectionTag = S + "8[" + S + "6VIP" + S + "8]";
        assertEquals("[VIP]", plain.serialize(DisplayService.component("&8[&6VIP&8]")));
        assertEquals("[VIP]", plain.serialize(DisplayService.component(sectionTag)));
        assertEquals("[VIP]", plain.serialize(DisplayService.component("<dark_gray>[<gold>VIP<dark_gray>]")));
        String expected = legacy.serialize(DisplayService.component("&8[&6VIP&8]"));
        assertEquals(expected, legacy.serialize(DisplayService.component("<dark_gray>[<gold>VIP<dark_gray>]")));
        assertEquals(expected, legacy.serialize(DisplayService.component(sectionTag)));
        assertEquals("[Plain]", plain.serialize(DisplayService.component("[Plain]")));
    }
}
