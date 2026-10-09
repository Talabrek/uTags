package com.blockworlds.utags.bedrock;

import com.blockworlds.utags.PlayerTagColorPreference;
import com.blockworlds.utags.Tag;
import com.blockworlds.utags.TagType;
import com.blockworlds.utags.module.Extensions;
import com.blockworlds.utags.spi.MenuRequest;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FormsTest {

    /** Records what the forms ask of the services. */
    static final class FakeServices implements FormServices {
        final List<Tag> tags = new ArrayList<>();
        final Set<String> owned = new HashSet<>();
        boolean recolor = true;
        boolean showLocked = false;
        final List<String> calls = new ArrayList<>();
        final List<String> told = new ArrayList<>();
        Map<String, String> nameColors = new LinkedHashMap<>();
        Map<Integer, Tag> custom = Map.of();

        public List<Tag> tags(TagType type) {
            return tags.stream().filter(t -> t.getType() == type || t.getType() == TagType.BOTH).toList();
        }
        public Tag tag(String name) { return tags.stream().filter(t -> t.getName().equals(name)).findFirst().orElse(null); }
        public boolean owns(Player p, String n) { return owned.contains(n); }
        public boolean canRecolor(Player p) { return recolor; }
        public boolean showLocked(UUID p) { return showLocked; }
        public void toggleShowLocked(UUID p) { showLocked = !showLocked; calls.add("toggleLocked"); }
        public PlayerTagColorPreference preference(UUID p, String n) { return new PlayerTagColorPreference(p, n); }
        public void ownedCustomTags(Player p, Consumer<Map<Integer, Tag>> then) { then.accept(custom); }
        public void applyTag(Player p, String n, TagType t) { calls.add("apply " + n + " " + t); }
        public void removeTag(Player p, TagType t) { calls.add("remove " + t); }
        public void setTagColors(Player p, String n, ChatColor b, ChatColor c) { calls.add("colors " + n + " " + b + " " + c); }
        public Map<String, String> nameColors(Player p) { return nameColors; }
        public void setNameColor(Player p, String l, String c) { calls.add("name " + l + " " + c); }
        public void submitRequest(Player p, String d) { calls.add("request " + d); }
        public void tell(Player p, String m) { told.add(ChatColor.stripColor(m)); }
    }

    static final class RecordingSender implements FormSender {
        boolean bedrock = true;
        final List<BedrockMenu> sent = new ArrayList<>();
        public boolean isBedrock(Player p) { return bedrock; }
        public void send(Player p, BedrockMenu m) { sent.add(m); }
        BedrockMenu last() { return sent.get(sent.size() - 1); }
    }

    FakeServices services;
    RecordingSender sender;
    Forms forms;
    Player player;

    static Tag tag(String name, TagType type, boolean isPublic, boolean colorable) {
        return new Tag(name, "&a[" + name + "]", type, isPublic, false, colorable, null, 1);
    }

    @BeforeEach
    void setUp() {
        services = new FakeServices();
        sender = new RecordingSender();
        forms = new Forms(services, sender);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    @Test
    void javaPlayersFallThroughToChestMenus() {
        sender.bedrock = false;
        assertFalse(forms.open(player, new MenuRequest.Main()));
        assertTrue(sender.sent.isEmpty());
    }

    @Test
    void overrideRoutesBedrockToFormsAndJavaToChests() {
        Extensions ext = new Extensions();
        ext.add(forms);
        assertTrue(ext.openOverridden(player, new MenuRequest.Main()));
        assertEquals(1, sender.sent.size());
        sender.bedrock = false;
        assertFalse(ext.openOverridden(player, new MenuRequest.Main()));
        assertEquals(1, sender.sent.size());
    }

    @Test
    void mainMenuHasTheChoicesAndRoutesThem() {
        forms.open(player, new MenuRequest.Main());
        BedrockMenu m = sender.last();
        assertEquals(List.of("Choose a prefix", "Choose a suffix", "Change name color", "Remove prefix", "Remove suffix",
                "Request a custom tag"), m.buttons());
        m.select(3);
        m.select(4);
        assertEquals(List.of("remove PREFIX", "remove SUFFIX"), services.calls);
        m.select(1);
        assertTrue(sender.last().title().startsWith("Select Suffix"));
        m.select(5);
        assertTrue(services.told.get(0).contains("/tag request"));
    }

    @Test
    void pickerShowsOnlyOwnedTagsAndMarksLockedWhenShown() {
        services.tags.add(tag("a", TagType.PREFIX, true, false));
        services.tags.add(tag("b", TagType.PREFIX, true, false));
        services.tags.add(tag("hidden", TagType.PREFIX, false, false));
        services.owned.add("a");
        services.owned.add("hidden");
        BedrockMenu m = forms.selectionMenu(player, TagType.PREFIX, 0, Map.of());
        assertTrue(m.buttons().get(0).contains("[a]"));
        assertTrue(m.buttons().get(1).contains("[hidden]"));
        assertFalse(m.buttons().stream().anyMatch(b -> b.contains("[b]")));

        services.showLocked = true;
        m = forms.selectionMenu(player, TagType.PREFIX, 0, Map.of());
        assertTrue(m.buttons().stream().anyMatch(b -> b.contains("[b]") && b.contains("(locked)")));
        assertFalse(m.buttons().stream().anyMatch(b -> b.contains("hidden") && b.contains("(locked)")));
    }

    @Test
    void lockedTagClickTellsAndOwnedTagApplies() {
        services.tags.add(tag("a", TagType.PREFIX, true, false));
        services.tags.add(tag("b", TagType.PREFIX, true, false));
        services.owned.add("a");
        services.showLocked = true;
        forms.open(player, new MenuRequest.Selection(TagType.PREFIX, 0));
        BedrockMenu m = sender.last();
        m.select(0);
        assertEquals(List.of("apply a PREFIX"), services.calls);
        m.select(1);
        assertTrue(services.told.get(0).contains("locked"));
        assertEquals(2, sender.sent.size()); // the picker again after the locked click
    }

    @Test
    void colourableTagOpensOptionsFormWithUseAndChangeColors() {
        services.tags.add(tag("c", TagType.PREFIX, true, true));
        services.owned.add("c");
        forms.open(player, new MenuRequest.Selection(TagType.PREFIX, 0));
        sender.last().select(0);
        BedrockMenu opt = sender.last();
        assertEquals("Tag Options", opt.title());
        assertEquals(List.of("Use this tag", "Change colors", "Back"), opt.buttons());
        opt.select(0);
        assertEquals(List.of("apply c PREFIX"), services.calls);
        opt.select(1);
        assertTrue(sender.last().title().startsWith("Select Tag Color"));
    }

    @Test
    void changeColorsNeedsPermission() {
        Tag t = tag("c", TagType.PREFIX, true, true);
        services.recolor = false;
        forms.optionsMenu(player, t, "&a[c]", TagType.PREFIX, 0).select(1);
        assertTrue(services.told.get(0).contains("permission"));
        assertTrue(sender.sent.isEmpty());
    }

    @Test
    void tagColorFormSetsBracketThenContent() {
        Tag t = tag("c", TagType.PREFIX, true, true);
        services.owned.add("c");
        BedrockMenu m = forms.tagColorMenu(player, t, true);
        assertEquals(16 + 4, m.buttons().size()); // 16 colours, switch, reset, apply, back
        m.select(12); // RED
        assertEquals("colors c " + ChatColor.RED + " null", services.calls.get(0));
        BedrockMenu content = forms.tagColorMenu(player, t, false);
        content.select(10); // GREEN
        assertEquals("colors c null " + ChatColor.GREEN, services.calls.get(1));
        m.select(17); // reset
        assertEquals("colors c null null", services.calls.get(2));
        m.select(18); // apply
        assertEquals("apply c PREFIX", services.calls.get(3));
    }

    @Test
    void bothTypeTagOffersPrefixAndSuffixApply() {
        Tag t = tag("x", TagType.BOTH, true, true);
        BedrockMenu m = forms.tagColorMenu(player, t, true);
        assertTrue(m.buttons().contains("Apply as prefix"));
        m.select(m.buttons().indexOf("Apply as suffix"));
        assertEquals("apply x SUFFIX", services.calls.get(0));
    }

    @Test
    void nameColorFormMapsToSetNameColor() {
        services.nameColors.put("Red", "&c");
        services.nameColors.put("Green", "&a");
        forms.open(player, new MenuRequest.NameColors());
        BedrockMenu m = sender.last();
        assertEquals(3, m.buttons().size());
        m.select(1);
        assertEquals(List.of("name Green &a"), services.calls);
    }

    @Test
    void noNameColorsTellsInstead() {
        forms.open(player, new MenuRequest.NameColors());
        assertTrue(sender.sent.isEmpty());
        assertFalse(services.told.isEmpty());
    }

    @Test
    void requestConfirmIsModalAndSubmitsOrCancels() {
        forms.open(player, new MenuRequest.RequestConfirm("&d[Mine]"));
        BedrockMenu m = sender.last();
        assertTrue(m.modal());
        assertEquals(List.of("Confirm Request", "Cancel"), m.buttons());
        m.select(1);
        assertTrue(services.calls.isEmpty());
        m.select(0);
        assertEquals(List.of("request &d[Mine]"), services.calls);
    }

    @Test
    void pagingAddsPreviousAndNext() {
        for (int i = 0; i < 25; i++) {
            Tag t = tag("t" + i, TagType.PREFIX, true, false);
            services.tags.add(t);
            services.owned.add(t.getName());
        }
        BedrockMenu first = forms.selectionMenu(player, TagType.PREFIX, 0, Map.of());
        assertTrue(first.title().endsWith("(1/3)"));
        assertTrue(first.buttons().contains("Next page"));
        assertFalse(first.buttons().contains("Previous page"));
        BedrockMenu last = forms.selectionMenu(player, TagType.PREFIX, 9, Map.of());
        assertTrue(last.title().endsWith("(3/3)"));
        assertTrue(last.buttons().contains("Previous page"));
        assertFalse(last.buttons().contains("Next page"));
        first.select(first.buttons().indexOf("Next page"));
        assertTrue(sender.last().title().endsWith("(2/3)"));
    }

    @Test
    void toggleLockedReopensPicker() {
        BedrockMenu m = forms.selectionMenu(player, TagType.PREFIX, 0, Map.of());
        m.select(m.buttons().indexOf("Show locked tags"));
        assertEquals(List.of("toggleLocked"), services.calls);
        assertEquals(1, sender.sent.size());
    }
}
