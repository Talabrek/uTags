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
        
        // Check if the click is valid and in the correct inventory
        if (inventory == null || !event.getView().getTitle().contains("Custom Tag Requests")) {
            return;
        }

        // Cancel the click to prevent item movement
        event.setCancelled(true);
        
        // Ensure that the event is triggered by a player
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        // Check if a valid item was clicked
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) {
            return;
        }

        // Extract player name from the clicked item
        String playerName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        CustomTagRequest request = plugin.getCustomTagRequestByPlayerName(playerName);

        if (request == null) {
            player.sendMessage(ChatColor.RED + "An error occurred. Please try again.");
            return;
        }

        // Handle left and right clicks
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
