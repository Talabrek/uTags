package com.blockworlds.utags;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TagMenuManager {
    private final uTags plugin;

    public TagMenuManager(uTags plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the main tag menu for a player
     *
     * @param player The player to open the menu for
     */
    public void openTagMenu(Player player) {
        Inventory tagMenu = Bukkit.createInventory(player, 9, "uTags Menu");

        // Create prefix item
        ItemStack changePrefixItem = new ItemStack(Material.NAME_TAG);
        ItemMeta changePrefixMeta = changePrefixItem.getItemMeta();
        changePrefixMeta.setDisplayName(ChatColor.GREEN + "Change Prefix");
        changePrefixItem.setItemMeta(changePrefixMeta);

        // Create suffix item
        ItemStack changeSuffixItem = new ItemStack(Material.NAME_TAG);
        ItemMeta changeSuffixMeta = changeSuffixItem.getItemMeta();
        changeSuffixMeta.setDisplayName(ChatColor.YELLOW + "Change Suffix");
        changeSuffixItem.setItemMeta(changeSuffixMeta);

        // Add items to menu
        tagMenu.setItem(2, changePrefixItem);
        tagMenu.setItem(6, changeSuffixItem);

        // Add player's head to middle location
        addPlayerHeadToMenu(player, tagMenu, 4);

        // Open the menu for the player
        player.openInventory(tagMenu);
    }

    /**
     * Adds a player's head to the menu with current tag information
     *
     * @param player The player
     * @param inventory The inventory to add the head to
     * @param slot The slot to place the head
     */
    private void addPlayerHeadToMenu(Player player, Inventory inventory, int slot) {
        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta playerHeadMeta = (SkullMeta) playerHead.getItemMeta();
        playerHeadMeta.setOwningPlayer(player);
        playerHeadMeta.setDisplayName(ChatColor.YELLOW + player.getName());

        String prefix = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId()).getCachedData().getMetaData().getPrefix();
        String suffix = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId()).getCachedData().getMetaData().getSuffix();

        String currentTitle = ChatColor.GRAY + "Current Title(s)";
        if (prefix != null) {
            currentTitle += "\n" + ChatColor.GRAY + "Prefix: " + ChatColor.translateAlternateColorCodes('&', prefix);
        }
        if (suffix != null) {
            currentTitle += "\n" + ChatColor.GRAY + "Suffix: " + ChatColor.translateAlternateColorCodes('&', suffix);
        }

        playerHeadMeta.setLore(Arrays.asList(currentTitle.split("\n")));
        playerHead.setItemMeta(playerHeadMeta);
        inventory.setItem(slot, playerHead);
    }

    /**
     * Opens the tag requests menu for a player
     *
     * @param player The player to open the menu for
     */
    public void openRequestsMenu(Player player) {
        List<CustomTagRequest> requests = plugin.getCustomTagRequests();
        int rows = Math.max(1, (int) Math.ceil(requests.size() / 9.0));
        Inventory requestsMenu = Bukkit.createInventory(player, rows * 9, "Tag Requests");

        for (CustomTagRequest request : requests) {
            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) playerHead.getItemMeta();

            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(request.getPlayerUuid()));
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', request.getTagDisplay()));
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GREEN + "Left-click to accept");
                lore.add(ChatColor.RED + "Right-click to deny");
                meta.setLore(lore);
                playerHead.setItemMeta(meta);
            }

            requestsMenu.addItem(playerHead);
        }

        player.openInventory(requestsMenu);
    }
}
