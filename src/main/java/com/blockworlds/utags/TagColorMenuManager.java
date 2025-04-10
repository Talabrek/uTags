package com.blockworlds.utags;

import org.bukkit.Bukkit;
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
    private static final List<Pair<Material, ChatColor>> COLOR_MAP = Arrays.asList(
            new Pair<>(Material.WHITE_STAINED_GLASS_PANE, ChatColor.WHITE),
            new Pair<>(Material.ORANGE_STAINED_GLASS_PANE, ChatColor.GOLD), // Bukkit Orange -> ChatColor Gold
            new Pair<>(Material.MAGENTA_STAINED_GLASS_PANE, ChatColor.LIGHT_PURPLE), // Bukkit Magenta -> ChatColor Light Purple
            new Pair<>(Material.LIGHT_BLUE_STAINED_GLASS_PANE, ChatColor.AQUA), // Bukkit Light Blue -> ChatColor Aqua
            new Pair<>(Material.YELLOW_STAINED_GLASS_PANE, ChatColor.YELLOW),
            new Pair<>(Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN), // Bukkit Lime -> ChatColor Green
            new Pair<>(Material.PINK_STAINED_GLASS_PANE, ChatColor.RED), // Bukkit Pink -> ChatColor Red (closest vibrant)
            new Pair<>(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY),
            new Pair<>(Material.LIGHT_GRAY_STAINED_GLASS_PANE, ChatColor.GRAY),
            new Pair<>(Material.CYAN_STAINED_GLASS_PANE, ChatColor.DARK_AQUA),
            new Pair<>(Material.PURPLE_STAINED_GLASS_PANE, ChatColor.DARK_PURPLE),
            new Pair<>(Material.BLUE_STAINED_GLASS_PANE, ChatColor.BLUE), // Bukkit Blue -> ChatColor Blue (Dark Blue is closer but less distinct)
            new Pair<>(Material.BROWN_STAINED_GLASS_PANE, ChatColor.DARK_RED), // Bukkit Brown -> ChatColor Dark Red (no direct brown)
            new Pair<>(Material.GREEN_STAINED_GLASS_PANE, ChatColor.DARK_GREEN),
            new Pair<>(Material.RED_STAINED_GLASS_PANE, ChatColor.DARK_RED), // Bukkit Red -> ChatColor Dark Red
            new Pair<>(Material.BLACK_STAINED_GLASS_PANE, ChatColor.BLACK)
    );

    public TagColorMenuManager(uTags plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the color selection GUI for a specific tag.
     *
     * @param player The player opening the menu.
     * @param tag    The tag being customized.
     * @param editingBrackets True if currently editing bracket color, false for content color.
     */
    public void openColorSelectionMenu(Player player, Tag tag, boolean editingBrackets) {
        // Calculate size needed (16 colors + mode switch + reset = 18 items -> 2 rows minimum)
        int size = 27; // 3 rows to be safe and allow spacing
        Inventory inv = Bukkit.createInventory(null, size, COLOR_MENU_TITLE + " - " + tag.getName());

        // Add color selection panes
        int slot = 0;
        for (Pair<Material, ChatColor> colorPair : COLOR_MAP) {
            if (slot >= size - 2) break; // Leave space for control items
            ItemStack glassPane = new ItemStack(colorPair.getKey());
            ItemMeta meta = glassPane.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(colorPair.getValue() + colorPair.getValue().name().replace("_", " "));
                List<String> lore = new ArrayList<>();
                lore.add(editingBrackets ? BRACKET_MODE_LORE : CONTENT_MODE_LORE);
                meta.setLore(lore);
                glassPane.setItemMeta(meta);
            }
            inv.setItem(slot++, glassPane);
        }

        // Add Mode Switch Item (e.g., Ink Sac/Bone Meal)
        ItemStack modeSwitchItem = new ItemStack(editingBrackets ? Material.INK_SAC : Material.BONE_MEAL);
        ItemMeta modeMeta = modeSwitchItem.getItemMeta();
        if (modeMeta != null) {
            modeMeta.setDisplayName(MODE_SWITCH_ITEM_NAME + (editingBrackets ? ChatColor.GRAY + "Brackets []" : ChatColor.WHITE + "Content"));
            List<String> modeLore = new ArrayList<>();
            modeLore.add(ChatColor.GRAY + "Click to switch editing mode");
            modeLore.add(ChatColor.GRAY + "(Currently editing: " + (editingBrackets ? "Brackets" : "Content") + ")");
            // Add current colors for reference
            PlayerTagColorPreference currentPref = plugin.getPlayerTagColorPreference(player.getUniqueId(), tag.getName());
            ChatColor currentBracketColor = currentPref.getBracketColor();
            ChatColor currentContentColor = currentPref.getContentColor();
            modeLore.add("");
            modeLore.add(ChatColor.GRAY + "Current Bracket: " + (currentBracketColor != null ? currentBracketColor + "■" : "Default"));
            modeLore.add(ChatColor.GRAY + "Current Content: " + (currentContentColor != null ? currentContentColor + "■" : "Default"));

            modeMeta.setLore(modeLore);
            modeSwitchItem.setItemMeta(modeMeta);
        }
        inv.setItem(size - 2, modeSwitchItem); // Place towards the end

        // Add Reset Item (e.g., Barrier)
        ItemStack resetItem = new ItemStack(Material.BARRIER);
        ItemMeta resetMeta = resetItem.getItemMeta();
        if (resetMeta != null) {
            resetMeta.setDisplayName(RESET_ITEM_NAME);
            List<String> resetLore = new ArrayList<>();
            resetLore.add(ChatColor.GRAY + "Click to reset both colors");
            resetLore.add(ChatColor.GRAY + "to the tag's default.");
            resetMeta.setLore(resetLore);
            resetItem.setItemMeta(resetMeta);
        }
        inv.setItem(size - 1, resetItem); // Place at the very end

        // Add Back Button (Arrow)
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(BACK_BUTTON_NAME);
            backMeta.setLore(Arrays.asList(ChatColor.GRAY + "Return to tag selection"));
            backButton.setItemMeta(backMeta);
        }
        inv.setItem(size - 9, backButton); // Bottom-left corner

        // Add Preview Item (Name Tag)
        ItemStack previewItem = new ItemStack(Material.NAME_TAG);
        ItemMeta previewMeta = previewItem.getItemMeta();
        if (previewMeta != null) {
            PlayerTagColorPreference currentPrefPreview = plugin.getPlayerTagColorPreference(player.getUniqueId(), tag.getName());
            String formattedPreview = plugin.formatTagDisplayWithColor(tag.getDisplay(), currentPrefPreview);
            previewMeta.setDisplayName(PREVIEW_ITEM_NAME + ": " + ChatColor.translateAlternateColorCodes('&', formattedPreview));
            List<String> previewLore = new ArrayList<>();
            previewLore.add(ChatColor.GRAY + "Current custom colors applied.");
            previewLore.add(" ");
            previewLore.add(ChatColor.YELLOW + "Default: " + ChatColor.translateAlternateColorCodes('&', tag.getDisplay())); // Show default
            previewMeta.setLore(previewLore);
            previewItem.setItemMeta(previewMeta);
        }
        // Add Apply Button(s)
        if (tag.getType() == TagType.BOTH) {
            // Add Apply as Prefix Button
            ItemStack applyPrefixButton = new ItemStack(Material.LIME_WOOL);
            ItemMeta prefixMeta = applyPrefixButton.getItemMeta();
            if (prefixMeta != null) {
                prefixMeta.setDisplayName(APPLY_PREFIX_BUTTON_NAME);
                prefixMeta.setLore(Arrays.asList(ChatColor.GRAY + "Apply as prefix with selected colors."));
                applyPrefixButton.setItemMeta(prefixMeta);
            }
            inv.setItem(size - 5, applyPrefixButton); // Place where preview was

            // Add Apply as Suffix Button
            ItemStack applySuffixButton = new ItemStack(Material.YELLOW_WOOL);
            ItemMeta suffixMeta = applySuffixButton.getItemMeta();
            if (suffixMeta != null) {
                suffixMeta.setDisplayName(APPLY_SUFFIX_BUTTON_NAME);
                suffixMeta.setLore(Arrays.asList(ChatColor.GRAY + "Apply as suffix with selected colors."));
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
                acceptMeta.setDisplayName(ACCEPT_BUTTON_NAME); // Use the renamed constant
                acceptMeta.setLore(Arrays.asList(ChatColor.GRAY + "Apply this " + tag.getType().name().toLowerCase() + " with selected colors."));
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

    /**
     * Gets the ChatColor associated with a clicked ItemStack (Stained Glass Pane).
     * @param item The ItemStack that was clicked.
     * @return The corresponding ChatColor, or null if not found.
     */
    public static ChatColor getChatColorFromItem(ItemStack item) {
        if (item == null || !item.getType().name().endsWith("_STAINED_GLASS_PANE")) {
            return null;
        }
        Material material = item.getType();
        for (Pair<Material, ChatColor> pair : COLOR_MAP) {
            if (pair.getKey() == material) {
                return pair.getValue();
            }
        }
        return null;
    }
}