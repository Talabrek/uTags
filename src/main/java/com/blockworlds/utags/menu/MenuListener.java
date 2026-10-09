package com.blockworlds.utags.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The one listener for every uTags menu. Anything that touches a window whose top inventory is a
 * {@link MenuHolder} is cancelled (clicks in either inventory, shift-clicks, number keys, double-clicks, drags), so
 * nothing can be taken out or put in. Only a click on a filled slot of the menu is routed to the holder.
 */
public final class MenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null || item.isEmpty()) {
            return;
        }
        holder.onClick(new MenuClick(player, event, event.getRawSlot(), item));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }
}
