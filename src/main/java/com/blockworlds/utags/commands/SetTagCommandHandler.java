package com.blockworlds.utags.commands;

import com.blockworlds.utags.Tag;
import com.blockworlds.utags.TagType;
import com.blockworlds.utags.exceptions.PermissionDeniedException;
import com.blockworlds.utags.exceptions.TagNotFoundException;
import com.blockworlds.utags.uTags;
import com.blockworlds.utags.utils.ErrorHandler;
import com.blockworlds.utags.utils.MessageUtils;
import com.blockworlds.utags.utils.PermissionUtils;
import com.blockworlds.utags.utils.Preconditions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Command handler for tag setting commands in the uTags plugin.
 * Handles "/tag set [tagname]" commands to directly set a player's tag.
 */
public class SetTagCommandHandler extends AbstractCommandHandler {
    
    private static final String COMMAND_USAGE = "Usage: /tag set [tagname]";
    private final ErrorHandler errorHandler;

    /**
     * Creates a new SetTagCommandHandler.
     *
     * @param plugin The uTags plugin instance
     */
    public SetTagCommandHandler(uTags plugin) {
        super(plugin);
        this.errorHandler = new ErrorHandler(plugin);
    }

    @Override
    public boolean handleCommand(CommandSender sender, String[] args) {
        try {
            // Verify sender is a player
            Player player = errorHandler.checkPlayer(sender);
            if (player == null) {
                return false;
            }
            
            // Verify command has the necessary arguments
            if (args.length < 1) {
                MessageUtils.sendError(player, COMMAND_USAGE);
                return false;
            }
            
            // Get and validate tag name
            String tagName = Preconditions.checkNotEmpty(args[0], "Tag name cannot be empty");
            
            // Check permission for this tag
            if (!PermissionUtils.hasTagPermission(player, tagName)) {
                throw new PermissionDeniedException("utags.tag." + tagName);
            }
            
            // Get tag display from name
            String tagDisplay = plugin.getTagDisplayByName(tagName);
            if (tagDisplay == null) {
                throw new TagNotFoundException(tagName);
            }
            
            // Log the tag set operation
            plugin.getLogger().log(Level.INFO, 
                "Player {0} is setting their tag to {1}", 
                new Object[]{player.getName(), tagName});
            
            // Set the tag as prefix (currently we only support prefixes via the set command)
            boolean success = plugin.setPlayerTag(player, tagDisplay, TagType.PREFIX);
            
            if (success) {
                MessageUtils.sendTagOperationMessage(player, "PREFIX", tagDisplay, true);
                return true;
            } else {
                MessageUtils.sendTagOperationMessage(player, "PREFIX", tagDisplay, false);
                return false;
            }
        } catch (TagNotFoundException | PermissionDeniedException e) {
            return errorHandler.handleException(e, sender, "setting tag");
        } catch (Exception e) {
            return errorHandler.handleException(e, sender, "processing tag command");
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
