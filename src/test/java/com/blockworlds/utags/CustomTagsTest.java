package com.blockworlds.utags;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomTagsTest {

    private static final UUID OWNER = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
    // Floodgate's UUID scheme for Bedrock players: all-zero high bits plus the XUID
    private static final UUID BEDROCK = UUID.fromString("00000000-0000-0000-0009-01f64f65c7c3");

    // --- tagName / parseNewName ---

    @Test
    void tagNameIsLowerCaseUuidAndSlot() {
        assertEquals("c0f8fad5b-d9cb-469f-a165-70867728950e-2", CustomTags.tagName(OWNER, 2));
        assertEquals("utags.tag.c0f8fad5b-d9cb-469f-a165-70867728950e-2",
                CustomTags.permission(CustomTags.tagName(OWNER, 2)));
    }

    @Test
    void tagNameRoundTrips() {
        for (UUID uuid : new UUID[]{OWNER, BEDROCK, UUID.randomUUID()}) {
            for (int slot = 1; slot <= CustomTags.MAX_SLOTS; slot++) {
                CustomTags.Owned parsed = CustomTags.parseNewName(CustomTags.tagName(uuid, slot));
                assertNotNull(parsed, uuid + " slot " + slot);
                assertEquals(uuid, parsed.owner());
                assertEquals(slot, parsed.slot());
            }
        }
    }

    @Test
    void tagNameHasNoPermissionSeparatorOrPlayerName() {
        // Bedrock names start with "." which would split utags.tag.<name> into extra permission nodes
        String name = CustomTags.tagName(BEDROCK, 1);
        assertFalse(name.contains("."));
        assertTrue(name.matches("[0-9a-z-]+"));
    }

    @Test
    void parseNewNameAcceptsUpperCase() {
        CustomTags.Owned parsed = CustomTags.parseNewName("C0F8FAD5B-D9CB-469F-A165-70867728950E-3");
        assertNotNull(parsed);
        assertEquals(OWNER, parsed.owner());
        assertEquals(3, parsed.slot());
    }

    @ParameterizedTest
    @CsvSource({
            "Steve2",
            "member",
            "c0f8fad5b-d9cb-469f-a165-70867728950e",       // no slot
            "c0f8fad5b-d9cb-469f-a165-70867728950e-0",     // slot 0
            "c0f8fad5b-d9cb-469f-a165-70867728950e-100",   // three digits
            "0f8fad5b-d9cb-469f-a165-70867728950e-1",      // missing the c
            "c0f8fad5b-d9cb-469f-a165-70867728950-1",      // short uuid
            "xc0f8fad5b-d9cb-469f-a165-70867728950e-1",    // junk in front
    })
    void parseNewNameRejectsOtherNames(String name) {
        assertNull(CustomTags.parseNewName(name));
    }

    @Test
    void parseNewNameRejectsNull() {
        assertNull(CustomTags.parseNewName(null));
    }

    // --- legacySlot ---

    @Test
    void legacySlotMatchesNamePlusSlot() {
        assertEquals(1, CustomTags.legacySlot("Steve1", "Steve"));
        assertEquals(2, CustomTags.legacySlot("Steve2", "Steve"));
        assertEquals(9, CustomTags.legacySlot("Steve9", "Steve"));
    }

    @Test
    void legacySlotIgnoresCase() {
        assertEquals(2, CustomTags.legacySlot("steve2", "Steve"));
        assertEquals(2, CustomTags.legacySlot("STEVE2", "steve"));
    }

    @Test
    void legacySlotIsExactNotAPrefixMatch() {
        // The old LIKE 'Steve%' count matched all of these
        assertEquals(-1, CustomTags.legacySlot("Steve2", "Stev"));       // "e2" is not a slot
        assertEquals(-1, CustomTags.legacySlot("Steve2", "Steve2"));     // the player's own name, no slot
        assertEquals(-1, CustomTags.legacySlot("Steven1", "Steve"));
        assertEquals(-1, CustomTags.legacySlot("SteveX", "Steve"));
        assertEquals(-1, CustomTags.legacySlot("Steve", "Steve"));
        assertEquals(-1, CustomTags.legacySlot("Stev", "Steve"));
    }

    @Test
    void legacySlotForNamesEndingInDigits() {
        // Player "Steve2" owns "Steve21" (slot 1) and "Steve22" (slot 2)
        assertEquals(1, CustomTags.legacySlot("Steve21", "Steve2"));
        assertEquals(2, CustomTags.legacySlot("Steve22", "Steve2"));
        // ... which are not tags of "Steve" (21 and 22 are beyond the slots)
        assertEquals(-1, CustomTags.legacySlot("Steve21", "Steve"));
        assertEquals(-1, CustomTags.legacySlot("Steve22", "Steve"));
        // but "Steve2" itself is Steve's slot 2, not a tag of player "Steve2"
        assertEquals(2, CustomTags.legacySlot("Steve2", "Steve"));
        assertEquals(-1, CustomTags.legacySlot("Steve2", "Steve2"));
    }

    @Test
    void legacySlotRejectsSlotZeroAndBeyondMax() {
        assertEquals(-1, CustomTags.legacySlot("Steve0", "Steve"));
        assertEquals(-1, CustomTags.legacySlot("Steve01", "Steve"));
        assertEquals(-1, CustomTags.legacySlot("Steve10", "Steve"));
        assertEquals(-1, CustomTags.legacySlot("Steve123", "Steve"));
    }

    @Test
    void legacySlotHandlesBedrockNames() {
        assertEquals(1, CustomTags.legacySlot(".Bob1", ".Bob"));
        assertEquals(-1, CustomTags.legacySlot("Bob1", ".Bob"));
    }

    @Test
    void legacySlotRejectsMissingInput() {
        assertEquals(-1, CustomTags.legacySlot(null, "Steve"));
        assertEquals(-1, CustomTags.legacySlot("Steve1", null));
        assertEquals(-1, CustomTags.legacySlot("Steve1", ""));
    }

    // --- likeEscape ---

    @Test
    void likeEscapeEscapesWildcardsAndTheEscapeChar() {
        assertEquals("Steve", CustomTags.likeEscape("Steve"));
        assertEquals("Steve!_2", CustomTags.likeEscape("Steve_2"));
        assertEquals("!%x", CustomTags.likeEscape("%x"));
        assertEquals("a!!b", CustomTags.likeEscape("a!b"));
        // the escape char is escaped first, so an escaped wildcard isn't escaped twice
        assertEquals("!!!_", CustomTags.likeEscape("!_"));
        assertEquals(".Bob", CustomTags.likeEscape(".Bob"));
    }

    // --- nextFreeSlot ---

    @Test
    void nextFreeSlotIsOneWhenNothingIsOwned() {
        assertEquals(1, CustomTags.nextFreeSlot(Map.of()));
    }

    @Test
    void nextFreeSlotFillsGaps() {
        assertEquals(2, CustomTags.nextFreeSlot(owned(1, 3)));
        assertEquals(1, CustomTags.nextFreeSlot(owned(2, 3)));
        assertEquals(3, CustomTags.nextFreeSlot(owned(1, 2, 4)));
    }

    @Test
    void nextFreeSlotAfterAContiguousRun() {
        assertEquals(4, CustomTags.nextFreeSlot(owned(1, 2, 3)));
        assertEquals(5, CustomTags.nextFreeSlot(owned(1, 2, 3, 4)));
    }

    // --- isOwnCustom ---

    @Test
    void isOwnCustomByUuid() {
        Tag mine = tag(CustomTags.tagName(OWNER, 1), false, OWNER, 1);
        assertTrue(CustomTags.isOwnCustom(mine, OWNER, "Steve"));
        assertFalse(CustomTags.isOwnCustom(mine, BEDROCK, ".Steve"));
        // an owned tag stays the owner's even when another player has the matching legacy name
        Tag legacyOwned = tag("Steve1", false, OWNER, 1);
        assertFalse(CustomTags.isOwnCustom(legacyOwned, BEDROCK, "Steve"));
        assertTrue(CustomTags.isOwnCustom(legacyOwned, OWNER, "RenamedSteve"));
    }

    @Test
    void isOwnCustomLegacyNeedsANonPublicTag() {
        assertTrue(CustomTags.isOwnCustom(tag("Steve2", false, null, null), OWNER, "Steve"));
        assertFalse(CustomTags.isOwnCustom(tag("VIP2", true, null, null), OWNER, "VIP"));
        assertFalse(CustomTags.isOwnCustom(tag("Steve2", false, null, null), OWNER, "Stev"));
    }

    @Test
    void shownInTopRowOnlyForTheTagInSlotsOneToFour() {
        Map<Integer, Tag> owned = owned(1, 3, 5);
        assertTrue(CustomTags.shownInTopRow(tag(CustomTags.tagName(OWNER, 3), false, OWNER, 3), owned));
        // a second tag in slot 3 (made by a 1.1.0 server) isn't the one shown, so the main list keeps it
        assertFalse(CustomTags.shownInTopRow(tag("Steve3", false, OWNER, 3), owned));
        // the menu has four custom slots
        assertFalse(CustomTags.shownInTopRow(tag(CustomTags.tagName(OWNER, 5), false, OWNER, 5), owned));
    }

    @ParameterizedTest
    @CsvSource({
            "Steve2, false, false, true",
            ".Bedrock1, false, false, true",
            "VIP2, true, false, false",       // public: never a custom tag
            "Event2024, false, true, false",  // color = true: made by staff, not by an accepted request
            "member, false, false, false"     // no slot digit
    })
    void mayBeLegacyCustom(String name, boolean isPublic, boolean color, boolean expected) {
        assertEquals(expected, CustomTags.mayBeLegacyCustom(name, isPublic, color));
    }

    private static Map<Integer, Tag> owned(int... slots) {
        Map<Integer, Tag> map = new HashMap<>();
        for (int slot : slots) map.put(slot, tag(CustomTags.tagName(OWNER, slot), false, OWNER, slot));
        return map;
    }

    private static Tag tag(String name, boolean isPublic, UUID owner, Integer slot) {
        return new Tag(name, "&a[" + name + "]", TagType.PREFIX, isPublic, false, false, null, 1, owner, slot);
    }
}
