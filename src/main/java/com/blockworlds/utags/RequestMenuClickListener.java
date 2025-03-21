package com.blockworlds.utags;

import com.blockworlds.utags.model.CustomTagRequest;
import com.blockworlds.utags.utils.Utils;
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
        
        if (inventory == null || !event.getView().getTitle().contains("Custom Tag Requests")) {
            return;
        }

        event.setCancelled(true);
        
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) {
            return;
        }

        String playerName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        CustomTagRequest request = plugin.getCustomTagRequestByPlayerName(playerName);

        if (request == null) {
            Utils.sendError(player, "An error occurred. Please try again.");
            return;
        }

        if (event.isLeftClick()) {
            plugin.acceptCustomTagRequest(request);
            Utils.sendSuccess(player, "Custom tag request accepted.");
        } else if (event.isRightClick()) {
            plugin.denyCustomTagRequest(request);
            Utils.sendSuccess(player, "Custom tag request denied.");
        }

        plugin.openRequestsMenu(player);
    }
}
