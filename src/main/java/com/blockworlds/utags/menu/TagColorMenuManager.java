package com.blockworlds.utags.menu;

import com.blockworlds.utags.spi.MenuRequest;
import com.blockworlds.utags.*;

import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TagColorMenuManager {

    private final uTags plugin;
    public static final String COLOR_MENU_TITLE = ChatColor.BLUE + "Select Tag Color";
    public static final String BRACKET_MODE_LORE = ChatColor.YELLOW + "Click a color for the brackets []";
    public static final String CONTENT_MODE_LORE = ChatColor.YELLOW + "Click a color for the content";
    public static final String RESET_ITEM_NAME = ChatColor.RED + "Reset to Default Color";
    public static final String MODE_SWITCH_ITEM_NAME = ChatColor.AQUA + "Editing: "; // Append Bracket/Content
    public static final String BACK_BUTTON_NAME = ChatColor.GRAY + "Back";
    public static final String PREVIEW_ITEM_NAME = ChatColor.YELLOW + "Preview";
    public static final String ACCEPT_BUTTON_NAME = ChatColor.GREEN + "Apply Tag"; // Renamed for clarity
    public static final String APPLY_PREFIX_BUTTON_NAME = ChatColor.GREEN + "Apply as Prefix";
    public static final String APPLY_SUFFIX_BUTTON_NAME = ChatColor.YELLOW + "Apply as Suffix";

    // Map Bukkit Material (Stained Glass Pane) to ChatColor
    // Reordered COLOR_MAP based on standard Minecraft color codes (0-f)
    // Using specific Stained Glass Pane mappings as requested
    private static final List<Pair<Material, ChatColor>> COLOR_MAP = Arrays.asList(
            new Pair<>(Material.BLACK_STAINED_GLASS_PANE, ChatColor.BLACK),           // &0
            new Pair<>(Material.BLUE_STAINED_GLASS_PANE, ChatColor.DARK_BLUE),        // &1
            new Pair<>(Material.GREEN_STAINED_GLASS_PANE, ChatColor.DARK_GREEN),      // &2
            new Pair<>(Material.CYAN_STAINED_GLASS_PANE, ChatColor.DARK_AQUA),       // &3
            new Pair<>(Material.RED_STAINED_GLASS_PANE, ChatColor.DARK_RED),         // &4
            new Pair<>(Material.PURPLE_STAINED_GLASS_PANE, ChatColor.DARK_PURPLE),    // &5
            new Pair<>(Material.ORANGE_STAINED_GLASS_PANE, ChatColor.GOLD),           // &6
            new Pair<>(Material.LIGHT_GRAY_STAINED_GLASS_PANE, ChatColor.GRAY),        // &7
            new Pair<>(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY),      // &8
            new Pair<>(Material.LIGHT_BLUE_STAINED_GLASS_PANE, ChatColor.BLUE),         // &9
            new Pair<>(Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN),          // &a
            new Pair<>(Material.CYAN_STAINED_GLASS_PANE, ChatColor.AQUA),           // &b
            new Pair<>(Material.RED_STAINED_GLASS_PANE, ChatColor.RED),            // &c
            new Pair<>(Material.MAGENTA_STAINED_GLASS_PANE, ChatColor.LIGHT_PURPLE), // &d
            new Pair<>(Material.YELLOW_STAINED_GLASS_PANE, ChatColor.YELLOW),        // &e
            new Pair<>(Material.WHITE_STAINED_GLASS_PANE, ChatColor.WHITE)          // &f
    );

    private final TagColorMenuClicks clicks;

    public TagColorMenuManager(uTags plugin) {
        this.plugin = plugin;
        this.clicks = new TagColorMenuClicks(plugin, this);
    }

    /** Menu size and the slots of its controls; the 16 colour panes fill slots 0-15. */
    static final int SIZE = 27;
    static final int MODE_SLOT = SIZE - 2;
    static final int RESET_SLOT = SIZE - 1;
    static final int BACK_SLOT = SIZE - 9;
    /** The apply button (single-type tags) and the suffix button of BOTH tags; the prefix button is one before. */
    static final int APPLY_SLOT = SIZE - 4;
    static final int APPLY_PREFIX_SLOT = SIZE - 5;

    /** The colour-picking menu of one tag; remembers the tag and whether the brackets or the content are edited. */
    final class ColorHolder extends MenuHolder {
        final String tagName;
        final boolean editingBrackets;

        ColorHolder(String tagName, boolean editingBrackets) {
            this.tagName = tagName;
            this.editingBrackets = editingBrackets;
        }

        Inventory createInventory(Component title) {
            return create(SIZE, title);
        }

        @Override
        public void onClick(MenuClick click) {
            clicks.handle(click, this);
        }
    }

    /**
     * Opens the color selection GUI for a specific tag.
     *
     * @param player The player opening the menu.
     * @param tag    The tag being customized.
     * @param editingBrackets True if currently editing bracket color, false for content color.
     */
    public void openColorSelectionMenu(Player player, Tag tag, boolean editingBrackets) {
        if (plugin.extensions().openOverridden(player, new MenuRequest.TagColors(tag, editingBrackets))) return;
        // Calculate size needed (16 colors + mode switch + reset = 18 items -> 2 rows minimum)
        int size = SIZE; // 3 rows to be safe and allow spacing
        Inventory inv = new ColorHolder(tag.getName(), editingBrackets)
                .createInventory(Text.c(COLOR_MENU_TITLE + " - " + tag.getName()));

        // Add color selection panes
        int slot = 0;
        for (Pair<Material, ChatColor> colorPair : COLOR_MAP) {
            if (slot >= size - 2) break; // Leave space for control items
            ItemStack glassPane = new ItemStack(colorPair.getKey());
            ItemMeta meta = glassPane.getItemMeta();
            if (meta != null) {
                meta.displayName(Text.c(colorPair.getValue() + colorPair.getValue().name().replace("_", " ")));
                List<String> lore = new ArrayList<>();
                lore.add(editingBrackets ? BRACKET_MODE_LORE : CONTENT_MODE_LORE);
                meta.lore(Text.cl(lore));
                glassPane.setItemMeta(meta);
            }
            inv.setItem(slot++, glassPane);
        }

        // Add Mode Switch Item (e.g., Ink Sac/Bone Meal)
        ItemStack modeSwitchItem = new ItemStack(editingBrackets ? Material.INK_SAC : Material.BONE_MEAL);
        ItemMeta modeMeta = modeSwitchItem.getItemMeta();
        if (modeMeta != null) {
            modeMeta.displayName(Text.c(MODE_SWITCH_ITEM_NAME + (editingBrackets ? ChatColor.GRAY + "Brackets []" : ChatColor.WHITE + "Content")));
            List<String> modeLore = new ArrayList<>();
            modeLore.add(ChatColor.GRAY + "Click to switch editing mode");
            modeLore.add(ChatColor.GRAY + "(Currently editing: " + (editingBrackets ? "Brackets" : "Content") + ")");
            // Add current colors for reference
            PlayerTagColorPreference currentPref = plugin.colors().tagPreference(player.getUniqueId(), tag.getName());
            ChatColor currentBracketColor = currentPref.getBracketColor();
            ChatColor currentContentColor = currentPref.getContentColor();
            modeLore.add("");
            modeLore.add(ChatColor.GRAY + "Current Bracket: " + (currentBracketColor != null ? currentBracketColor + "■" : "Default"));
            modeLore.add(ChatColor.GRAY + "Current Content: " + (currentContentColor != null ? currentContentColor + "■" : "Default"));

            modeMeta.lore(Text.cl(modeLore));
            modeSwitchItem.setItemMeta(modeMeta);
        }
        inv.setItem(size - 2, modeSwitchItem); // Place towards the end

        // Add Reset Item (e.g., Barrier)
        ItemStack resetItem = new ItemStack(Material.BARRIER);
        ItemMeta resetMeta = resetItem.getItemMeta();
        if (resetMeta != null) {
            resetMeta.displayName(Text.c(RESET_ITEM_NAME));
            List<String> resetLore = new ArrayList<>();
            resetLore.add(ChatColor.GRAY + "Click to reset both colors");
            resetLore.add(ChatColor.GRAY + "to the tag's default.");
            resetMeta.lore(Text.cl(resetLore));
            resetItem.setItemMeta(resetMeta);
        }
        inv.setItem(size - 1, resetItem); // Place at the very end

        // Add Back Button (Arrow)
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.displayName(Text.c(BACK_BUTTON_NAME));
            backMeta.lore(Text.cl(Arrays.asList(ChatColor.GRAY + "Return to tag selection")));
            backButton.setItemMeta(backMeta);
        }
        inv.setItem(size - 9, backButton); // Bottom-left corner

        // Add Preview Item (Name Tag)
        ItemStack previewItem = new ItemStack(Material.NAME_TAG);
        ItemMeta previewMeta = previewItem.getItemMeta();
        if (previewMeta != null) {
            PlayerTagColorPreference currentPrefPreview = plugin.colors().tagPreference(player.getUniqueId(), tag.getName());
            String formattedPreview = com.blockworlds.utags.display.DisplayService.formatTagDisplayWithColor(tag.getDisplay(), currentPrefPreview);
            previewMeta.displayName(Text.c(PREVIEW_ITEM_NAME + ": " + ChatColor.translateAlternateColorCodes('&', formattedPreview)));
            List<String> previewLore = new ArrayList<>();
            previewLore.add(ChatColor.GRAY + "Current custom colors applied.");
            previewLore.add(" ");
            previewLore.add(ChatColor.YELLOW + "Default: " + ChatColor.translateAlternateColorCodes('&', tag.getDisplay())); // Show default
            previewMeta.lore(Text.cl(previewLore));
            previewItem.setItemMeta(previewMeta);
        }
        // Add Apply Button(s)
        if (tag.getType() == TagType.BOTH) {
            // Add Apply as Prefix Button
            ItemStack applyPrefixButton = new ItemStack(Material.LIME_WOOL);
            ItemMeta prefixMeta = applyPrefixButton.getItemMeta();
            if (prefixMeta != null) {
                prefixMeta.displayName(Text.c(APPLY_PREFIX_BUTTON_NAME));
                prefixMeta.lore(Text.cl(Arrays.asList(ChatColor.GRAY + "Apply as prefix with selected colors.")));
                applyPrefixButton.setItemMeta(prefixMeta);
            }
            inv.setItem(size - 5, applyPrefixButton); // Place where preview was

            // Add Apply as Suffix Button
            ItemStack applySuffixButton = new ItemStack(Material.YELLOW_WOOL);
            ItemMeta suffixMeta = applySuffixButton.getItemMeta();
            if (suffixMeta != null) {
                suffixMeta.displayName(Text.c(APPLY_SUFFIX_BUTTON_NAME));
                suffixMeta.lore(Text.cl(Arrays.asList(ChatColor.GRAY + "Apply as suffix with selected colors.")));
                applySuffixButton.setItemMeta(suffixMeta);
            }
            inv.setItem(size - 4, applySuffixButton); // Place where accept was

            // Move Preview Item for BOTH tags
            inv.setItem(size - 6, previewItem); // Shift preview left

        } else {
            // Single Apply Button for PREFIX or SUFFIX tags
            ItemStack acceptButton = new ItemStack(Material.LIME_WOOL);
            ItemMeta acceptMeta = acceptButton.getItemMeta();
            if (acceptMeta != null) {
                acceptMeta.displayName(Text.c(ACCEPT_BUTTON_NAME)); // Use the renamed constant
                acceptMeta.lore(Text.cl(Arrays.asList(ChatColor.GRAY + "Apply this " + tag.getType().name().toLowerCase() + " with selected colors.")));
                acceptButton.setItemMeta(acceptMeta);
            }
            inv.setItem(size - 4, acceptButton); // Original position
            inv.setItem(size - 5, previewItem); // Original preview position
        }

        player.openInventory(inv);
    }

    // Simple Pair class for mapping Material to ChatColor
    private static class Pair<K, V> {
        private final K key;
        private final V value;

        public Pair(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }
    }

    /** The colour of the pane in this slot (the panes are laid out in COLOR_MAP order from slot 0), or null. */
    public static ChatColor colorAtSlot(int slot) {
        return slot >= 0 && slot < COLOR_MAP.size() ? COLOR_MAP.get(slot).getValue() : null;
    }
}
