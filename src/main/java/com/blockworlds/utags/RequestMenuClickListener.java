package com.blockworlds.utags;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class RequestMenuClickListener implements Listener {

    private final uTags plugin;

    public RequestMenuClickListener(uTags plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getClickedInventory();
        Player player = (Player) event.getWhoClicked();

        if (inventory == null || !event.getView().getTitle().contains("Custom Tag Requests")) {
            return;
        }

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) {
            return;
        }

        String playerName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        CustomTagRequest request = plugin.getCustomTagRequestByPlayerName(playerName);

        if (request == null) {
            player.sendMessage(ChatColor.RED + "An error occurred. Please try again.");
            return;
        }

        if (event.isLeftClick()) {
            // Accept the custom tag request
            plugin.acceptCustomTagRequest(request);
            player.sendMessage(ChatColor.GREEN + "Custom tag request accepted.");

        } else if (event.isRightClick()) {
            // Deny the custom tag request
            plugin.denyCustomTagRequest(request);
            player.sendMessage(ChatColor.RED + "Custom tag request denied.");
        }

        // Update the requests menu
        plugin.openRequestsMenu(player);
    }
}