package com.blockworlds.utags;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class NameColorMenuManager {

    private final uTags plugin;
    public static final String MENU_TITLE = ChatColor.DARK_AQUA + "Select Name Color";

    public NameColorMenuManager(uTags plugin) {
        this.plugin = plugin;
    }

    public void openNameColorMenu(Player player) {
        org.bukkit.configuration.ConfigurationSection nameColorSection = plugin.getConfig().getConfigurationSection("name-colors");

        if (nameColorSection == null || nameColorSection.getKeys(false).isEmpty()) {
            player.sendMessage(ChatColor.RED + "Name colors are not configured or enabled in config.yml.");
            return;
        }

        Map<String, String> availableColors = new HashMap<>();
        for (String key : nameColorSection.getKeys(false)) {
            String value = nameColorSection.getString(key);
            if (value != null && !value.isEmpty()) {
                 availableColors.put(key, value);
            } else {
                plugin.getLogger().warning("Invalid or empty value for name color key: " + key);
            }
        }

        if (availableColors.isEmpty()) {
             player.sendMessage(ChatColor.RED + "No valid name colors found in the configuration.");
             return;
        }

        int size = (int) Math.ceil(availableColors.size() / 9.0) * 9;
        size = Math.max(9, Math.min(size, 54)); // Ensure size is between 9 and 54
        Inventory menu = Bukkit.createInventory(null, size, MENU_TITLE);

        int slot = 0;
        for (Map.Entry<String, String> entry : availableColors.entrySet()) {
            String colorName = entry.getKey();
            String colorCode = entry.getValue();
            ChatColor chatColor = ChatColor.getByChar(colorCode.replace("&", ""));

            if (chatColor == null) {
                plugin.getLogger().warning("Invalid color code found in config for name color '" + colorName + "': " + colorCode);
                continue; // Skip invalid colors
            }

            // Basic permission check (can be expanded)
            if (!player.hasPermission("utags.namecolor." + colorName.toLowerCase().replace(" ", "_"))) {
                 continue; // Skip colors the player doesn't have permission for
            }

            ItemStack item = createColorItem(chatColor, colorName);
            menu.setItem(slot++, item);

            if (slot >= size) {
                plugin.getLogger().warning("Too many name colors defined to fit in the menu. Some colors might not be displayed.");
                break; // Stop if menu is full
            }
        }

        if (menu.firstEmpty() == -1 && slot < availableColors.size()) {
             plugin.getLogger().warning("Name color menu is full, but more colors were available. Consider increasing menu size or reducing colors.");
        } else if (slot == 0) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use any name colors, or none are configured.");
            return; // Don't open an empty menu
        }


        player.openInventory(menu);
    }

    private ItemStack createColorItem(ChatColor color, String colorName) {
        Material material = getMaterialForColor(color); // Get appropriate material (e.g., colored wool)
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(color + colorName);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to select this name color.");
            // Add permission info if needed: lore.add(ChatColor.DARK_GRAY + "Requires: utags.namecolor." + colorName.toLowerCase().replace(" ", "_"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // Helper to get a representative material for the color
    private Material getMaterialForColor(ChatColor color) {
        switch (color) {
            case BLACK: return Material.BLACK_WOOL;
            case DARK_BLUE: return Material.BLUE_WOOL;
            case DARK_GREEN: return Material.GREEN_WOOL;
            case DARK_AQUA: return Material.CYAN_WOOL;
            case DARK_RED: return Material.RED_WOOL;
            case DARK_PURPLE: return Material.PURPLE_WOOL;
            case GOLD: return Material.ORANGE_WOOL; // Often represented by Orange
            case GRAY: return Material.LIGHT_GRAY_WOOL;
            case DARK_GRAY: return Material.GRAY_WOOL;
            case BLUE: return Material.LIGHT_BLUE_WOOL;
            case GREEN: return Material.LIME_WOOL;
            case AQUA: return Material.LIGHT_BLUE_WOOL; // Often represented by Light Blue
            case RED: return Material.PINK_WOOL; // Often represented by Pink or Red
            case LIGHT_PURPLE: return Material.MAGENTA_WOOL;
            case YELLOW: return Material.YELLOW_WOOL;
            case WHITE: return Material.WHITE_WOOL;
            default: return Material.WHITE_WOOL; // Default fallback
        }
    }
}