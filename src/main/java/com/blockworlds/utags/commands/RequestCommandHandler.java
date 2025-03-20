package com.blockworlds.utags.commands;

import com.blockworlds.utags.uTags;
import com.blockworlds.utags.utils.MessageUtils;
import com.blockworlds.utags.utils.PermissionUtils;
import com.blockworlds.utags.utils.ValidationUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Command handler for tag request commands in the uTags plugin.
 * Handles the "/tag request [tag]" command, which allows players to request custom tags.
 */
public class RequestCommandHandler extends AbstractCommandHandler {

    /**
     * Creates a new RequestCommandHandler.
     *
     * @param plugin The uTags plugin instance
     */
    public RequestCommandHandler(uTags plugin) {
        super(plugin);
    }

    @Override
    public boolean handleCommand(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        
        if (player == null) {
            return false;
        }

        if (args.length < 1) {
            showTagRequestHelp(player);
            return true;
        }

        String requestedTag = args[0];
        
        // Count existing custom tags and determine which slot we're using
        int customTagCount = plugin.countCustomTags(player.getName());
        String requiredPermission = "utags.custom" + (customTagCount + 1);
        
        // Check if player has permission for an additional custom tag
        if (!player.hasPermission(requiredPermission)) {
            if (!requiredPermission.equalsIgnoreCase("utags.custom5")) {
                MessageUtils.sendError(player, "You can't request any more custom tags, unlock more custom tag slots every month as a premium subscriber.");
            } else {
                MessageUtils.sendError(player, "You have reached the maximum number of custom tags.");
            }
            return false;
        }
        
        // Validate tag format
        String validationResult = ValidationUtils.validateTagFormat(requestedTag);
        if (validationResult != null) {
            MessageUtils.sendError(player, validationResult);
            if (validationResult.contains("color code")) {
                showColorCodes(player);
            }
            return false;
        }
        
        // Process the tag preview
        requestedTag = ValidationUtils.normalizeTagString(requestedTag);
        
        // Show tag preview and ask for confirmation
        MessageUtils.sendSuccess(player, "Tag request preview: " + MessageUtils.colorize(requestedTag));
        MessageUtils.sendInfo(player, "Type 'accept' to accept the tag or 'decline' to try again.");
        
        // Register the preview listener
        plugin.addPreviewTag(player, requestedTag);
        return true;
    }

    /**
     * Shows available color codes to a player.
     *
     * @param player The player to show color codes to
     */
    private void showColorCodes(Player player) {
        MessageUtils.showColorCodes(player);
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
        showColorCodes(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            return getEmptyTabCompletions();
        }
        
        Player player = (Player) sender;
        
        if (args.length == 1) {
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
        }
        
        return getEmptyTabCompletions();
    }
}
