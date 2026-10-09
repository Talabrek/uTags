package com.blockworlds.utags.command;

import com.blockworlds.utags.*;
import com.blockworlds.utags.menu.*;

import org.bukkit.entity.Player;

/** /name: opens the name colour menu for players with utags.name.gui. */
public class NameCommand {

    private final uTags plugin;
    private final NameColorMenuManager nameColorMenuManager;

    public NameCommand(uTags plugin, NameColorMenuManager nameColorMenuManager) {
        this.plugin = plugin;
        this.nameColorMenuManager = nameColorMenuManager;
    }

    void open(Player player) {
        if (!player.hasPermission("utags.name.gui")) {
            player.sendMessage(plugin.display().message("no_permission")); // configurable message
            return;
        }
        nameColorMenuManager.openNameColorMenu(player);
    }
}
