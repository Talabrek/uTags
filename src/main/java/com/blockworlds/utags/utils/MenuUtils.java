package com.blockworlds.utags.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;

/**
 * Utility class for menu-related operations in the uTags plugin.
 * Provides methods for creating and manipulating inventory menus and their items.
 */
public class MenuUtils {

    /**
     * Creates a framed inventory with borders made of a specified material.
     *
     * @param size The size of the inventory (must be a multiple of 9)
     * @param title The title of the inventory
     * @param frameMaterial The material to use for the frame
     * @param player The player who will view the inventory
     * @return The created inventory with frame
     */
    public static Inventory createInventoryFrame(int size, String title, Material frameMaterial, Player player) {
        Inventory inventory = Bukkit.createInventory(player, size, title);

        ItemStack frameItem = createSimpleItem(frameMaterial, " ", null);

        // Add frame border to inventory
        for (int i = 0; i < size; i++) {
            if (i < 9 || i > size - 10 || i % 9 == 0 || (i + 1) % 9 == 0) {
                inventory.setItem(i, frameItem);
            }
        }

        return inventory;
    }

    /**
     * Creates a simple ItemStack with custom name and lore.
     *
     * @param material The material for the item
     * @param displayName The display name for the item (color codes supported with &)
     * @param lore The lore for the item (color codes supported with &)
     * @return The created ItemStack
     */
    public static ItemStack createSimpleItem(Material material, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (displayName != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
        }
        
        if (lore != null && !lore.isEmpty()) {
            // Translate color codes in lore
            List<String> coloredLore = MessageUtils.colorizeStringList(lore);
            meta.setLore(coloredLore);
        }
        
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Creates a player head item with custom name and lore.
     *
     * @param player The player whose head to create
     * @param displayName The display name for the item
     * @param lore The lore for the item
     * @return The created player head ItemStack
     */
    public static ItemStack createPlayerHead(Player player, String displayName, List<String> lore) {
        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta playerHeadMeta = (SkullMeta) playerHead.getItemMeta();
        
        playerHeadMeta.setOwningPlayer(player);
        
        if (displayName != null) {
            playerHeadMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
        }
        
        if (lore != null && !lore.isEmpty()) {
            // Translate color codes in lore
            List<String> coloredLore = MessageUtils.colorizeStringList(lore);
            playerHeadMeta.setLore(coloredLore);
        }
        
        playerHead.setItemMeta(playerHeadMeta);
        return playerHead;
    }

    /**
     * Creates a navigation button (typically an arrow for next/previous page).
     *
     * @param displayName The display name for the button
     * @return The created navigation button ItemStack
     */
    public static ItemStack createNavigationButton(String displayName) {
        return createSimpleItem(Material.ARROW, displayName, null);
    }

    /**
     * Adds the player's head to the inventory with current tag information.
     *
     * @param player The player whose head to add
     * @param inventory The inventory to add the head to
     * @param slot The slot to place the head in
     * @param prefix The player's current prefix
     * @param suffix The player's current suffix
     */
    public static void addPlayerHeadWithTags(Player player, Inventory inventory, int slot, String prefix, String suffix) {
        List<String> lore = generatePlayerTagLore(prefix, suffix);
        ItemStack playerHead = createPlayerHead(player, ChatColor.YELLOW + player.getName(), lore);
        inventory.setItem(slot, playerHead);
    }

    /**
     * Generates lore for a player's head showing their current tags.
     *
     * @param prefix The player's current prefix
     * @param suffix The player's current suffix
     * @return A list of strings for the lore
     */
    private static List<String> generatePlayerTagLore(String prefix, String suffix) {
        String currentTitle = ChatColor.GRAY + "Current Title(s)";
        
        if (prefix != null) {
            currentTitle += "\n" + ChatColor.GRAY + "Prefix: " + ChatColor.translateAlternateColorCodes('&', prefix);
        }
        
        if (suffix != null) {
            currentTitle += "\n" + ChatColor.GRAY + "Suffix: " + ChatColor.translateAlternateColorCodes('&', suffix);
        }
        
        return Arrays.asList(currentTitle.split("\n"));
    }
}
