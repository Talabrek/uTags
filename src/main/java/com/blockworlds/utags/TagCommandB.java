package com.blockworlds.utags;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;

/*public class TagCommandB implements CommandExecutor {
    private final uTags plugin;

    public TagCommandB(uTags plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            plugin.getTagMenuManager().openTagMenu(player);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            if (player.hasPermission("utags.admin")) {
                if (args.length >= 2 && args[1].equalsIgnoreCase("create")) {
                    createTag(player, Arrays.copyOfRange(args, 2, args.length));
                    return true;
                } else if (args.length >= 2 && args[1].equalsIgnoreCase("delete")) {
                    deleteTag(player, Arrays.copyOfRange(args, 2, args.length));
                    return true;
                }
                else if (args.length >= 2 && args[1].equalsIgnoreCase("purge")) {
                        plugin.purgeTable();
                        player.sendMessage(ChatColor.RED + "All data has been purged from the tags table.");
                        return true;
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /tag admin create [name] [display] [type] [public] [color]");
                    player.sendMessage(ChatColor.RED + "Usage: /tag admin delete [name]");
                    player.sendMessage(ChatColor.RED + "Usage: /tag admin purge");
                    return true;
                }
            } else {
                player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
        }

        if (args.length == 2 && "request".equalsIgnoreCase(args[0])) {
            String tagRequest = args[1];
            handleTagRequest(player, tagRequest);
            return true;
        }

        player.sendMessage(ChatColor.RED + "Invalid usage. Use /tag help for a list of available commands.");
        return true;
    }

    private void createTag(Player player, String[] args) {
        if (args.length != 5) {
            player.sendMessage(ChatColor.RED + "Usage: /tag admin create [name] [display] [type] [public] [color]");
            return;
        }

        String name = args[0];
        String display = ChatColor.translateAlternateColorCodes('&', args[1]);
        String typeString = args[2].toUpperCase();
        String isPublicString = args[3];
        String isColorString = args[4];
        ItemStack material = player.getInventory().getItemInMainHand();

        if (!name.matches("^[a-zA-Z0-9_-]+$")) {
            player.sendMessage(ChatColor.RED + "Invalid tag [name]. It should contain only letters, numbers, underscores, and hyphens.");
            return;
        }
        TagType type;
        try {
            type = TagType.valueOf(typeString);
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Invalid tag [type]. It should be one of 'PREFIX', 'SUFFIX', or 'BOTH'.");
            return;
        }

        // Check if the boolean values are valid
        if (!isValidBoolean(isPublicString)) {
            player.sendMessage(ChatColor.RED + "Invalid [public] value. It should be either 'true' or 'false'.");
            return;
        }

        if (!isValidBoolean(isColorString)) {
            player.sendMessage(ChatColor.RED + "Invalid [color] value. It should be either 'true' or 'false'.");
            return;
        }

        // If the player is holding a player head, get the player head with custom texture
        if (material.getType() == Material.PLAYER_HEAD && material.getItemMeta() instanceof SkullMeta) {
            SkullMeta skullMeta = (SkullMeta) material.getItemMeta();
            if (skullMeta.hasOwner()) {
                material.setItemMeta(skullMeta);
            }
        }

        if (material.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Invalid item for tag display. Hold an item in your hand.");
            return;
        }

        // Add the new tag to the database
        plugin.addTagToDatabase(new Tag(name, display, type, Boolean.parseBoolean(isPublicString), Boolean.parseBoolean(isColorString), material));

        player.sendMessage(ChatColor.GREEN + "Tag '" + name + "' - " + display +  " &ahas been created.");
    }

    private void deleteTag(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: /tag admin delete [name]");
            return;
        }

        String name = args[0];

        // Delete the tag from the database
        plugin.deleteTagFromDatabase(name);

        player.sendMessage(ChatColor.RED + "Tag '" + name + "' &chas been deleted.");
    }

    private void handleTagRequest(Player player, String tagRequest) {
        // Handle custom tag request here. You can send the request to an admin or store it in the database for later review.
        player.sendMessage("&eYour custom tag request has been sent: " + tagRequest);
    }

    private boolean isValidBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }
}
*/