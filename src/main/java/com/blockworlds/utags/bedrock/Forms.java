package com.blockworlds.utags.bedrock;

import com.blockworlds.utags.CustomTags;
import com.blockworlds.utags.PlayerTagColorPreference;
import com.blockworlds.utags.Tag;
import com.blockworlds.utags.TagType;
import com.blockworlds.utags.display.DisplayService;
import com.blockworlds.utags.spi.MenuOpenerOverride;
import com.blockworlds.utags.spi.MenuRequest;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The player menus as Bedrock forms: the same choices, permissions and service calls as the chest menus. For a Bedrock
 * player {@link #open} builds and sends the form for the requested menu; for anyone else it answers false and the
 * chest menu opens. Admin menus are never offered here.
 */
public final class Forms implements MenuOpenerOverride {

    /** Tags per form page (buttons beyond that page on). */
    public static final int PAGE_SIZE = 10;

    /** The 16 chat colours, in the chest menu's order. */
    static final List<ChatColor> COLORS = List.of(ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN,
            ChatColor.DARK_AQUA, ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.GRAY,
            ChatColor.DARK_GRAY, ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA, ChatColor.RED, ChatColor.LIGHT_PURPLE,
            ChatColor.YELLOW, ChatColor.WHITE);

    private final FormServices services;
    private final FormSender sender;

    public Forms(FormServices services, FormSender sender) {
        this.services = services;
        this.sender = sender;
    }

    @Override
    public boolean open(Player player, MenuRequest request) {
        if (!sender.isBedrock(player)) return false;
        switch (request) {
            case MenuRequest.Main m -> main(player);
            case MenuRequest.Selection s -> selection(player, s.type(), s.page());
            case MenuRequest.Options o -> options(player, o.tag(), o.display(), o.type(), o.returnPage());
            case MenuRequest.TagColors c -> tagColors(player, c.tag(), c.editingBrackets());
            case MenuRequest.NameColors n -> nameColors(player);
            case MenuRequest.RequestConfirm r -> requestConfirm(player, r.display());
        }
        return true;
    }

    // --- main menu ---

    BedrockMenu mainMenu(Player player) {
        return new BedrockMenu.Builder("Tags")
                .content("Choose what to change.")
                .button("Choose a prefix", () -> selection(player, TagType.PREFIX, 0))
                .button("Choose a suffix", () -> selection(player, TagType.SUFFIX, 0))
                .button("Change name color", () -> nameColors(player))
                .button("Remove prefix", () -> services.removeTag(player, TagType.PREFIX))
                .button("Remove suffix", () -> services.removeTag(player, TagType.SUFFIX))
                .button("Request a custom tag", () -> customTagHelp(player))
                .build();
    }

    private void main(Player player) {
        sender.send(player, mainMenu(player));
    }

    private void customTagHelp(Player player) {
        services.tell(player, ChatColor.YELLOW + "To request a custom tag, use: " + ChatColor.WHITE + "/tag request <YourDesiredTagDisplay>");
        services.tell(player, ChatColor.GRAY + "(Example: /tag request &d[MyTag])");
    }

    // --- prefix / suffix picker ---

    private void selection(Player player, TagType type, int page) {
        if (type == TagType.PREFIX) {
            services.ownedCustomTags(player, custom -> sender.send(player, selectionMenu(player, type, page, custom)));
        } else {
            sender.send(player, selectionMenu(player, type, page, Map.of()));
        }
    }

    /** One page of the picker: this page's tags (locked ones marked), then paging and shortcut buttons. */
    BedrockMenu selectionMenu(Player player, TagType type, int page, Map<Integer, Tag> ownedCustom) {
        boolean showLocked = services.showLocked(player.getUniqueId());
        List<Tag> all = new ArrayList<>();
        if (type == TagType.PREFIX) all.addAll(ownedCustom.values());
        for (Tag tag : services.tags(type)) {
            if (CustomTags.shownInTopRow(tag, ownedCustom)) continue;
            if (services.owns(player, tag.getName()) || (showLocked && tag.isPublic())) all.add(tag);
        }
        int total = all.size();
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));

        String word = type == TagType.PREFIX ? "prefix" : "suffix";
        BedrockMenu.Builder b = new BedrockMenu.Builder("Select " + (type == TagType.PREFIX ? "Prefix" : "Suffix")
                + " (" + (current + 1) + "/" + pages + ")")
                .content(total == 0 ? "No tags to show." : "Pick a tag to wear as your " + word + ".");
        for (int i = current * PAGE_SIZE; i < Math.min(total, (current + 1) * PAGE_SIZE); i++) {
            Tag tag = all.get(i);
            boolean locked = !services.owns(player, tag.getName());
            String text = locked
                    ? ChatColor.GRAY + ChatColor.stripColor(DisplayService.toLegacy(tag.getDisplay())) + ChatColor.RED + " (locked)"
                    : colored(player, tag);
            b.button(text, () -> tagClicked(player, tag, type, current, locked));
        }
        if (current > 0) b.button("Previous page", () -> selection(player, type, current - 1));
        if (current < pages - 1) b.button("Next page", () -> selection(player, type, current + 1));
        TagType other = type == TagType.PREFIX ? TagType.SUFFIX : TagType.PREFIX;
        b.button("Switch to " + (other == TagType.PREFIX ? "prefixes" : "suffixes"), () -> selection(player, other, 0));
        b.button(showLocked ? "Hide locked tags" : "Show locked tags", () -> {
            services.toggleShowLocked(player.getUniqueId());
            selection(player, type, current);
        });
        b.button("Remove current " + word, () -> services.removeTag(player, type));
        b.button("Change name color", () -> nameColors(player));
        if (type == TagType.PREFIX) b.button("Request a custom tag", () -> customTagHelp(player));
        b.button("Back", () -> main(player));
        return b.build();
    }

    private String colored(Player player, Tag tag) {
        PlayerTagColorPreference pref = services.preference(player.getUniqueId(), tag.getName());
        return DisplayService.toLegacy(DisplayService.formatTagDisplayWithColor(tag.getDisplay(), pref));
    }

    /** A click on a tag: the chest menu's rules (locked message, direct use, or the options form for a colourable tag). */
    private void tagClicked(Player player, Tag tag, TagType type, int page, boolean locked) {
        if (locked || !services.owns(player, tag.getName())) {
            services.tell(player, ChatColor.RED + "This tag is locked. You need the required permission to use it.");
            selection(player, type, page);
            return;
        }
        if (!services.canRecolor(player) || !tag.isColorable()) {
            services.applyTag(player, tag.getName(), type);
            return;
        }
        options(player, tag, colored(player, tag), type, page);
    }

    // --- tag options ---

    BedrockMenu optionsMenu(Player player, Tag tag, String display, TagType type, int returnPage) {
        return new BedrockMenu.Builder("Tag Options")
                .content(DisplayService.toLegacy(display))
                .button("Use this tag", () -> {
                    if (!services.owns(player, tag.getName())) {
                        services.tell(player, ChatColor.RED + "You don't have permission to use this tag.");
                        return;
                    }
                    services.applyTag(player, tag.getName(), type);
                })
                .button("Change colors", () -> {
                    if (!services.canRecolor(player)) {
                        services.tell(player, ChatColor.RED + "You do not have permission to change tag colors.");
                        return;
                    }
                    tagColors(player, tag, true);
                })
                .button("Back", () -> selection(player, type, returnPage))
                .build();
    }

    private void options(Player player, Tag tag, String display, TagType type, int returnPage) {
        sender.send(player, optionsMenu(player, tag, display, type, returnPage));
    }

    // --- tag colours ---

    private static String label(ChatColor c) {
        return c.name().replace('_', ' ');
    }

    BedrockMenu tagColorMenu(Player player, Tag tag, boolean editingBrackets) {
        PlayerTagColorPreference pref = services.preference(player.getUniqueId(), tag.getName());
        BedrockMenu.Builder b = new BedrockMenu.Builder("Select Tag Color - " + tag.getName())
                .content("Editing the " + (editingBrackets ? "brackets []" : "content") + "\n"
                        + "Preview: " + DisplayService.toLegacy(DisplayService.formatTagDisplayWithColor(tag.getDisplay(), pref)));
        for (ChatColor color : COLORS) {
            b.button(color + label(color), () -> {
                if (!checkRecolor(player, tag)) return;
                PlayerTagColorPreference current = services.preference(player.getUniqueId(), tag.getName());
                ChatColor bracket = editingBrackets ? color : current.getBracketColor();
                ChatColor content = editingBrackets ? current.getContentColor() : color;
                services.setTagColors(player, tag.getName(), bracket, content);
                services.tell(player, ChatColor.GREEN + "Set " + (editingBrackets ? "bracket" : "content") + " color to " + color + color.name());
                tagColors(player, tag, editingBrackets);
            });
        }
        b.button("Editing: " + (editingBrackets ? "Brackets" : "Content") + " (tap to switch)", () -> {
            if (checkRecolor(player, tag)) tagColors(player, tag, !editingBrackets);
        });
        b.button("Reset to default colors", () -> {
            if (!checkRecolor(player, tag)) return;
            services.setTagColors(player, tag.getName(), null, null);
            services.tell(player, ChatColor.GREEN + "Colors for tag '" + tag.getName() + "' reset to default.");
            tagColors(player, tag, true);
        });
        if (tag.getType() == TagType.BOTH) {
            b.button("Apply as prefix", () -> applyColored(player, tag, TagType.PREFIX));
            b.button("Apply as suffix", () -> applyColored(player, tag, TagType.SUFFIX));
        } else {
            b.button("Apply tag", () -> applyColored(player, tag, tag.getType()));
        }
        b.button("Back", () -> selection(player, tag.getType() == TagType.SUFFIX ? TagType.SUFFIX : TagType.PREFIX, 0));
        return b.build();
    }

    private boolean checkRecolor(Player player, Tag tag) {
        if (!tag.isColorable()) {
            services.tell(player, ChatColor.RED + "This tag does not support color customization.");
            return false;
        }
        if (!services.canRecolor(player)) {
            services.tell(player, ChatColor.RED + "You do not have permission to change tag colors.");
            return false;
        }
        return true;
    }

    private void applyColored(Player player, Tag tag, TagType type) {
        if (!checkRecolor(player, tag)) return;
        services.applyTag(player, tag.getName(), type);
    }

    private void tagColors(Player player, Tag tag, boolean editingBrackets) {
        sender.send(player, tagColorMenu(player, tag, editingBrackets));
    }

    // --- name colours ---

    BedrockMenu nameColorMenu(Player player, Map<String, String> colors) {
        BedrockMenu.Builder b = new BedrockMenu.Builder("Select Name Color").content("Pick the color of your name.");
        for (Map.Entry<String, String> e : colors.entrySet()) {
            ChatColor c = ChatColor.getByChar(e.getValue().replace("&", ""));
            b.button((c == null ? "" : c.toString()) + e.getKey(), () -> services.setNameColor(player, e.getKey(), e.getValue()));
        }
        b.button("Back", () -> main(player));
        return b.build();
    }

    private void nameColors(Player player) {
        Map<String, String> colors = services.nameColors(player);
        if (colors.isEmpty()) {
            services.tell(player, ChatColor.RED + "You do not have permission to use any name colors, or none are configured.");
            return;
        }
        sender.send(player, nameColorMenu(player, colors));
    }

    // --- custom tag request ---

    BedrockMenu requestMenu(Player player, String display) {
        return new BedrockMenu.Builder("Confirm Tag Request")
                .content("Preview: " + DisplayService.toLegacy(display) + "\n\nIs this the tag you want to request?")
                .button("Confirm Request", () -> {
                    String trimmed = display;
                    int end = trimmed.indexOf(']') + 1;
                    if (end > 0 && end < trimmed.length()) trimmed = trimmed.substring(0, end);
                    services.submitRequest(player, trimmed);
                })
                .button("Cancel", () -> services.tell(player, ChatColor.YELLOW + "Tag request cancelled."))
                .buildModal();
    }

    private void requestConfirm(Player player, String display) {
        sender.send(player, requestMenu(player, display));
    }
}
