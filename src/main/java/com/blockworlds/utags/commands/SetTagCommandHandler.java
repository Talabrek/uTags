package com.blockworlds.utags.commands;

import com.blockworlds.utags.Tag;
import com.blockworlds.utags.TagType;
import com.blockworlds.utags.uTags;
import com.blockworlds.utags.utils.MessageUtils;
import com.blockworlds.utags.utils.PermissionUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Command handler for tag setting commands in the uTags plugin.
 * Handles "/tag set [tagname]" commands to directly set a player's tag.
 */
public class SetTagCommandHandler extends AbstractCommandHandler {

    /**
     * Creates a new SetTagCommandHandler.
     *
     * @param plugin The uTags plugin instance
     */
    public SetTagCommandHandler(uTags plugin) {
        super(plugin);
    }

    @Override
    public boolean handleCommand(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        
        if (player == null) {
            return false;
        }
        
        if (args.length < 1) {
            MessageUtils.sendError(player, "Usage: /tag set [tagname]");
            return false;
        }
        
        String tagName = args[0];
        
        // Check permission for this tag
        if (!PermissionUtils.hasTagPermission(player, tagName)) {
            MessageUtils.sendError(player, "You don't have permission to use this tag.");
            return false;
        }
        
        // Get tag display from name
        String tagDisplay = plugin.getTagDisplayByName(tagName);
        if (tagDisplay == null) {
            MessageUtils.sendError(player, "Tag not found: " + tagName);
            return false;
        }
        
        // Set the tag as prefix (currently we only support prefixes via the set command)
        boolean success = plugin.setPlayerTag(player, tagDisplay, TagType.PREFIX);
        
        if (success) {
            MessageUtils.sendTagOperationMessage(player, "PREFIX", tagDisplay, true);
            return true;
        } else {
            MessageUtils.sendTagOperationMessage(player, "PREFIX", tagDisplay, false);
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> suggestions = new ArrayList<>();
        
        if (!(sender instanceof Player)) {
            return suggestions;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 1) {
            // Suggest available tags this player has permission to use
            for (Tag tag : plugin.getAvailableTags(TagType.PREFIX)) {
                if (PermissionUtils.hasTagPermission(player, tag.getName())) {
                    suggestions.add(tag.getName());
                }
            }
            
            // Add player's custom tags
            for (int i = 1; i <= 5; i++) {
                if (PermissionUtils.hasCustomTag(player, i)) {
                    suggestions.add(player.getName() + i);
                }
            }
            
            return filterSuggestions(suggestions, args[0]);
        }
        
        return suggestions;
    }
}
