package com.blockworlds.utags.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/** A click on a uTags menu, already cancelled. {@code slot} is the raw slot, {@code item} the non-empty clicked item. */
public record MenuClick(Player player, InventoryClickEvent event, int slot, ItemStack item) {
}
