package com.blockworlds.utags.commands;

import com.blockworlds.utags.exceptions.MaxCustomTagsException;
import com.blockworlds.utags.exceptions.TagRequestException;
import com.blockworlds.utags.exceptions.ValidationException;
import com.blockworlds.utags.uTags;
import com.blockworlds.utags.utils.ErrorHandler;
import com.blockworlds.utags.utils.MessageUtils;
import com.blockworlds.utags.utils.PermissionUtils;
import com.blockworlds.utags.utils.ValidationUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Command handler for tag request commands in the uTags plugin.
 * Handles the "/tag request [tag]" command, which allows players to request custom tags.
 */
public class RequestCommandHandler extends AbstractCommandHandler {
    private final ErrorHandler errorHandler;

    /**
     * Creates a new RequestCommandHandler.
     *
     * @param plugin The uTags plugin instance
     */
    public RequestCommandHandler(uTags plugin) {
        super(plugin);
        this.errorHandler = new ErrorHandler(plugin);
    }

    @Override
    public boolean handleCommand(CommandSender sender, String[] args) {
        try {
            // Check if sender is a player
            Player player = errorHandler.checkPlayer(sender);
            if (player == null) {
                return false;
            }

            // Display help if no tag provided
            if (args.length < 1) {
                showTagRequestHelp(player);
                return true;
            }

            String requestedTag = args[0];
            
            // Check custom tag limit
            int customTagCount = plugin.countCustomTags(player.getName());
            int maxCustomTags = 5; // Maximum number of custom tags allowed
            String requiredPermission = "utags.custom" + (customTagCount + 1);
            
            // Verify permission for additional custom tag
            if (!player.hasPermission(requiredPermission)) {
                if (customTagCount >= maxCustomTags) {
                    throw new MaxCustomTagsException(player.getName(), maxCustomTags);
                } else {
                    throw new TagRequestException("You need the permission " + requiredPermission + " to request this custom tag.");
                }
            }
            
            // Validate tag format
            String validationResult = ValidationUtils.validateTagFormat(requestedTag);
            if (validationResult != null) {
                throw new ValidationException(validationResult);
            }
            
            // Process the tag preview
            requestedTag = ValidationUtils.normalizeTagString(requestedTag);
            
            // Log successful request attempt
            plugin.getLogger().log(Level.INFO, 
                "Player {0} requested a custom tag: {1}", 
                new Object[]{player.getName(), requestedTag});
            
            // Show tag preview and ask for confirmation
            MessageUtils.sendSuccess(player, "Tag request preview: " + MessageUtils.colorize(requestedTag));
            MessageUtils.sendInfo(player, "Type 'accept' to accept the tag or 'decline' to try again.");
            
            // Register the preview listener
            plugin.addPreviewTag(player, requestedTag);
            return true;
            
        } catch (MaxCustomTagsException e) {
            return errorHandler.handleException(e, sender, "requesting a custom tag");
        } catch (ValidationException e) {
            boolean isColorCodeIssue = e.getValidationMessage().contains("color code");
            
            // Show color codes if that's the issue
            if (isColorCodeIssue && sender instanceof Player) {
                MessageUtils.showColorCodes((Player) sender);
            }
            
            return errorHandler.handleException(e, sender, "validating tag format");
        } catch (TagRequestException e) {
            return errorHandler.handleException(e, sender, "requesting a custom tag");
        } catch (Exception e) {
            return errorHandler.handleException(e, sender, "processing tag request");
        }
    }

    /**
     * Shows tag request help to a player.
     *
     * @param player The player to show help to
     */
    private void showTagRequestHelp(Player player) {
        MessageUtils.sendInfo(player, "Usage: /tag request [YourNewTag]");
        MessageUtils.sendInfo(player, "You must follow these rules when requesting a custom tag:");
        MessageUtils.sendInfo(player, "1. The tag must start with a color code.");
        MessageUtils.sendInfo(player, "2. The tag must be surrounded by square brackets ([ and ]).");
        MessageUtils.sendInfo(player, "3. The tag must be a maximum of 15 characters long.");
        MessageUtils.sendInfo(player, "4. The tag must not contain any spaces.");
        MessageUtils.sendInfo(player, "5. The tag must not contain any formatting codes (&k, &l, etc).");
        MessageUtils.sendInfo(player, "6. The tag must not contain any invalid characters.");
        MessageUtils.sendInfo(player, "7. Everything after the final square bracket will be ignored.");
        MessageUtils.sendInfo(player, "A staff member will review your request and approve it if it meets the requirements.");
        MessageUtils.showColorCodes(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            return getEmptyTabCompletions();
        }
        
        Player player = (Player) sender;
        
        if (args.length == 1) {
            try {
                List<String> suggestions = new ArrayList<>();
                
                // Get next available custom slot
                int nextSlot = PermissionUtils.getNextAvailableCustomSlot(player);
                if (nextSlot > 0) {
                    suggestions.add("&c[YourTag]");
                    suggestions.add("&e[CustomTag]");
                    suggestions.add("&a[UniqueTag]");
                    suggestions.add("&b[CoolTag]");
                    suggestions.add("&d[AwesomeTag]");
                    return filterSuggestions(suggestions, args[0]);
                }
            } catch (Exception e) {
                // Log but don't interrupt tab completion
                plugin.getLogger().log(Level.WARNING, "Error during tab completion", e);
            }
        }
        
        return getEmptyTabCompletions();
    }
}
