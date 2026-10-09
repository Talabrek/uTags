package com.blockworlds.utags.spi;

import org.bukkit.entity.Player;

/**
 * Lets a module open a player menu its own way (the Bedrock module shows Cumulus forms instead of chest menus).
 * Called on the main thread by the menu managers before they build a chest menu. Admin menus are never offered.
 */
public interface MenuOpenerOverride {

    /** @return true when this override opened the menu (the chest menu is then skipped), false to fall through. */
    boolean open(Player player, MenuRequest request);
}
