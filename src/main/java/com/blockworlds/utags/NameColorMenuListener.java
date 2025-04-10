package com.blockworlds.utags;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public class NameColorMenuListener implements Listener {

    private final uTags plugin;

    // Reverted constructor to match usage in uTags.java
    public NameColorMenuListener(uTags plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        ItemStack clickedItem = event.getCurrentItem();

        // Check if the clicked inventory is the Name Color Menu
        if (clickedInventory != null && event.getView().getTitle().equals(NameColorMenuManager.MENU_TITLE)) {
            // Prevent taking items from the menu
            event.setCancelled(true);

            if (clickedItem == null || clickedItem.getType() == Material.AIR || !clickedItem.hasItemMeta()) {
                return; // Ignore clicks on empty slots or items without meta
            }

            ItemMeta meta = clickedItem.getItemMeta();
            if (meta == null || !meta.hasDisplayName()) {
                return; // Ignore items without a display name
            }

            String displayName = meta.getDisplayName();
            String strippedName = ChatColor.stripColor(displayName); // Get the plain color name (e.g., "Light Blue")
            String colorCode = findColorCodeByName(strippedName);

            if (colorCode == null) {
                player.sendMessage(ChatColor.RED + "Could not determine the color code for the selected item.");
                plugin.getLogger().warning("Could not find color code for item display name: " + displayName + " (Stripped: " + strippedName + ")");
                return;
            }

            // Permission check (redundant if menu only shows permitted items, but good practice)
            String permissionNode = "utags.namecolor." + strippedName.toLowerCase().replace(" ", "_");
            if (!player.hasPermission(permissionNode)) {
                player.sendMessage(ChatColor.RED + "You do not have permission to use this name color.");
                return;
            }

            // Update player's preference and display name
            plugin.savePlayerNameColorCode(player.getUniqueId(), colorCode); // Persist preference
            plugin.updatePlayerDisplayName(player); // Apply change immediately

            player.sendMessage(ChatColor.GREEN + "Your name color has been updated to " + ChatColor.translateAlternateColorCodes('&', colorCode) + strippedName + ChatColor.GREEN + "!");
            player.closeInventory();
        }
    }

    /**
     * Finds the color code (&a, &c, etc.) based on the friendly color name.
     * This requires looking up the name in the config again.
     *
     * @param colorName The friendly name of the color (e.g., "Light Blue")
     * @return The color code (e.g., "&b") or null if not found.
     */
    private String findColorCodeByName(String colorName) {
        org.bukkit.configuration.ConfigurationSection nameColorSection = plugin.getConfig().getConfigurationSection("name-colors");
        if (nameColorSection == null) {
            return null;
        }

        for (String key : nameColorSection.getKeys(false)) {
            // Compare the config key (which should ideally match the friendly name)
            if (key.equalsIgnoreCase(colorName)) {
                return nameColorSection.getString(key);
            }
        }
        // Fallback: Check if the value matches the stripped name (less ideal)
         for (String key : nameColorSection.getKeys(false)) {
             String code = nameColorSection.getString(key);
             ChatColor chatColor = ChatColor.getByChar(code.replace("&", ""));
             if (chatColor != null && chatColor.name().replace("_", " ").equalsIgnoreCase(colorName)) {
                 return code;
             }
         }

        return null; // Not found
    }
}