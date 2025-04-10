package com.blockworlds.utags;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// Renamed class
public class NameCommand implements CommandExecutor {

    private final uTags plugin;
    private final NameColorMenuManager nameColorMenuManager;

    // Renamed constructor
    public NameCommand(uTags plugin, NameColorMenuManager nameColorMenuManager) {
        this.plugin = plugin;
        this.nameColorMenuManager = nameColorMenuManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Updated Permission Check
        if (!player.hasPermission("utags.name.gui")) { // Changed permission node
            player.sendMessage(plugin.getMessage("no_permission")); // Use configurable message
            return true;
        }

        // Open the name color selection menu
        nameColorMenuManager.openNameColorMenu(player);
        return true;
    }
}