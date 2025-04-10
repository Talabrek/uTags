package com.blockworlds.utags;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChangeNameColorCommand implements CommandExecutor {

    private final uTags plugin;
    private final NameColorMenuManager nameColorMenuManager;

    public ChangeNameColorCommand(uTags plugin, NameColorMenuManager nameColorMenuManager) {
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

        // Permission Check
        if (!player.hasPermission("utags.changenamecolor")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to change your name color.");
            return true;
        }

        // Open the name color selection menu
        nameColorMenuManager.openNameColorMenu(player);
        return true;
    }
}