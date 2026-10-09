package com.blockworlds.utags.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Base of every uTags inventory menu. A menu is recognised by its holder class, never by its title, and each menu
 * answers its own clicks in {@link #onClick}. {@link MenuListener} cancels every click and drag in a menu before it
 * gets here, so no item can be taken out, and calls {@code onClick} only for a click on a non-empty slot of the menu
 * itself. Every action is one plain click (Bedrock players have no right or shift click).
 */
public abstract class MenuHolder implements InventoryHolder {

    private Inventory inventory;

    /** Creates this menu's inventory (call once, then fill it and open it for the player). */
    protected final Inventory create(int size, Component title) {
        inventory = Bukkit.createInventory(this, size, title);
        return inventory;
    }

    /** One click on a non-empty slot of this menu (main thread). */
    public abstract void onClick(MenuClick click);

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
