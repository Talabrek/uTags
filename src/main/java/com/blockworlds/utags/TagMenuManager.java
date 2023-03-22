package com.blockworlds.utags;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;

public class TagMenuManager {
    private final uTags plugin;

    public TagMenuManager(uTags plugin) {
        this.plugin = plugin;
    }

    public void openTagMenu(Player player) {
        Inventory tagMenu = Bukkit.createInventory(player, 9,  "uTags Menu");

        ItemStack changePrefixItem = new ItemStack(Material.NAME_TAG);
        ItemMeta changePrefixMeta = changePrefixItem.getItemMeta();
        changePrefixMeta.setDisplayName(ChatColor.GREEN + "Change Prefix");
        changePrefixItem.setItemMeta(changePrefixMeta);

        ItemStack changeSuffixItem = new ItemStack(Material.NAME_TAG);
        ItemMeta changeSuffixMeta = changeSuffixItem.getItemMeta();
        changeSuffixMeta.setDisplayName(ChatColor.YELLOW + "Change Suffix");
        changeSuffixItem.setItemMeta(changeSuffixMeta);

        tagMenu.setItem(2, changePrefixItem);
        tagMenu.setItem(6, changeSuffixItem);

        // Add player's head to middle location
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
        tagMenu.setItem(4, playerHead);

        player.openInventory(tagMenu);
    }
}