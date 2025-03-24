package com.blockworlds.utags.controller.impl;

import com.blockworlds.utags.model.Result;
import com.blockworlds.utags.model.TagType;
import com.blockworlds.utags.service.MenuService;
import com.blockworlds.utags.service.RequestService;
import com.blockworlds.utags.service.TagService;
import com.blockworlds.utags.util.MessageUtils;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for handling inventory click events related to uTags menus.
 */
public class MenuClickController implements Listener {
    private final TagService tagService;
    private final MenuService menuService;
    private final RequestService requestService;
    
    // Simple cooldown mechanism to prevent click spam
    private final Map<UUID, Long> lastInteractions = new HashMap<>();
    private static final long INTERACTION_COOLDOWN_MS = 250;

    /**
     * Creates a new MenuClickController with required dependencies.
     *
     * @param tagService The tag service for tag operations
     * @param menuService The menu service for menu operations
     * @param requestService The request service for request operations
     */
    public MenuClickController(TagService tagService, MenuService menuService, RequestService requestService) {
        this.tagService = tagService;
        this.menuService = menuService;
        this.requestService = requestService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        
        // Only handle uTags-related inventories
        if (!isUTagsMenu(title)) return;
        
        // Cancel the event to prevent item taking
        event.setCancelled(true);
        
        // Check for click cooldown
        if (isOnCooldown(player.getUniqueId())) return;
        updateCooldown(player.getUniqueId());
        
        // Get the clicked item
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) return;
        
        String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        
        // Handle menu interactions based on inventory title
        if (title.equals("uTags Menu")) {
            handleMainMenu(player, itemName);
        } else if (title.contains("Select Prefix")) {
            handleTagSelection(player, clickedItem, TagType.PREFIX, extractPageNumber(title));
        } else if (title.contains("Select Suffix")) {
            handleTagSelection(player, clickedItem, TagType.SUFFIX, extractPageNumber(title));
        } else if (title.equals("Tag Requests") || title.equals("Custom Tag Requests")) {
            handleRequestsMenu(player, clickedItem, event.isLeftClick());
        }
    }
    
    /**
     * Checks if the inventory title belongs to a uTags menu.
     *
     * @param title The inventory title
     * @return True if this is a uTags menu
     */
    private boolean isUTagsMenu(String title) {
        return title.equals("uTags Menu") || 
               title.contains("Select Prefix") || 
               title.contains("Select Suffix") || 
               title.equals("Tag Requests") ||
               title.equals("Custom Tag Requests");
    }
    
    /**
     * Extracts the page number from a menu title.
     *
     * @param title The menu title
     * @return The page number, or 0 if not found
     */
    private int extractPageNumber(String title) {
        try {
            String[] parts = title.split(" ");
            return Integer.parseInt(parts[parts.length - 1]);
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Handles main menu interactions.
     *
     * @param player The player interacting with the menu
     * @param itemName The name of the clicked item
     */
    private void handleMainMenu(Player player, String itemName) {
        if ("Change Prefix".equals(itemName)) {
            Result<Boolean> result = menuService.openTagSelectionMenu(player, 0, TagType.PREFIX);
            if (!result.isSuccess()) {
                MessageUtils.sendError(player, "Failed to open prefix menu: " + result.getMessage());
            }
        } else if ("Change Suffix".equals(itemName)) {
            Result<Boolean> result = menuService.openTagSelectionMenu(player, 0, TagType.SUFFIX);
            if (!result.isSuccess()) {
                MessageUtils.sendError(player, "Failed to open suffix menu: " + result.getMessage());
            }
        }
    }
    
    /**
     * Handles tag selection menu interactions.
     *
     * @param player The player interacting with the menu
     * @param clickedItem The clicked item
     * @param tagType The type of tag being selected
     * @param currentPage The current page index
     */
    private void handleTagSelection(Player player, ItemStack clickedItem, TagType tagType, int currentPage) {
        String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

        // Handle navigation buttons
        if ("Previous Page".equals(itemName)) {
            Result<Boolean> result = menuService.openTagSelectionMenu(player, currentPage - 1, tagType);
            if (!result.isSuccess()) {
                MessageUtils.sendError(player, "Failed to navigate: " + result.getMessage());
            }
            return;
        } else if ("Next Page".equals(itemName)) {
            Result<Boolean> result = menuService.openTagSelectionMenu(player, currentPage + 1, tagType);
            if (!result.isSuccess()) {
                MessageUtils.sendError(player, "Failed to navigate: " + result.getMessage());
            }
            return;
        } else if ("Return to Main Menu".equals(itemName)) {
            Result<Boolean> result = menuService.openMainMenu(player);
            if (!result.isSuccess()) {
                MessageUtils.sendError(player, "Failed to return to main menu: " + result.getMessage());
            }
            return;
        }
        
        // Handle tag selection
        String tagDisplay = clickedItem.getItemMeta().getDisplayName();
        
        // Handle custom tag slots
        if (itemName.startsWith("Custom Tag #")) {
            String[] parts = itemName.split(":");
            if (parts.length > 1) {
                String tagName = player.getName() + itemName.charAt(itemName.length() - 1);
                Result<Boolean> result = tagService.setPlayerTagByName(player, tagName, tagType);
                if (result.isSuccess() && result.getValue()) {
                    MessageUtils.sendSuccess(player, "Your tag has been updated.");
                    player.closeInventory();
                } else {
                    MessageUtils.sendError(player, "Failed to update your tag: " + 
                        (result.getMessage() != null ? result.getMessage() : "Unknown error"));
                }
            }
        } else {
            // Get tag name from its display text
            Result<String> tagNameResult = tagService.getTagNameByDisplay(tagDisplay);
            if (!tagNameResult.isSuccess()) {
                MessageUtils.sendError(player, "Failed to find tag: " + tagNameResult.getMessage());
                return;
            }
            
            String tagName = tagNameResult.getValue();
            
            // Set the player's tag if valid
            if (tagName != null) {
                Result<Boolean> result = tagService.setPlayerTagByName(player, tagName, tagType);
                if (result.isSuccess() && result.getValue()) {
                    MessageUtils.sendSuccess(player, "Your tag has been updated.");
                    player.closeInventory();
                } else {
                    MessageUtils.sendError(player, "Failed to update your tag: " + 
                        (result.getMessage() != null ? result.getMessage() : "Unknown error"));
                }
            }
        }
    }
    
    /**
     * Handles request menu interactions.
     *
     * @param player The player interacting with the menu
     * @param clickedItem The clicked item
     * @param isLeftClick Whether the interaction was a left click
     */
    private void handleRequestsMenu(Player player, ItemStack clickedItem, boolean isLeftClick) {
        if (!clickedItem.getItemMeta().hasDisplayName()) return;
        
        String playerName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        
        Result<CustomTagRequest> requestResult = requestService.getCustomTagRequestByPlayerName(playerName);
        if (!requestResult.isSuccess()) {
            MessageUtils.sendError(player, "Error finding request: " + requestResult.getMessage());
            return;
        }
        
        if (isLeftClick) {
            Result<Boolean> result = requestService.acceptCustomTagRequest(requestResult.getValue());
            if (result.isSuccess() && result.getValue()) {
                MessageUtils.sendSuccess(player, "Custom tag request accepted.");
            } else {
                MessageUtils.sendError(player, "Failed to accept request: " + 
                    (result.getMessage() != null ? result.getMessage() : "Unknown error"));
            }
        } else {
            Result<Boolean> result = requestService.denyCustomTagRequest(requestResult.getValue());
            if (result.isSuccess() && result.getValue()) {
                MessageUtils.sendSuccess(player, "Custom tag request denied.");
            } else {
                MessageUtils.sendError(player, "Failed to deny request: " + 
                    (result.getMessage() != null ? result.getMessage() : "Unknown error"));
            }
        }
        
        // Refresh the menu
        Result<Boolean> refreshResult = menuService.openRequestsMenu(player);
        if (!refreshResult.isSuccess()) {
            MessageUtils.sendError(player, "Failed to refresh menu: " + refreshResult.getMessage());
        }
    }
    
    /**
     * Checks if a player is on interaction cooldown.
     *
     * @param playerId The UUID of the player
     * @return True if the player is on cooldown
     */
    private boolean isOnCooldown(UUID playerId) {
        Long lastInteraction = lastInteractions.get(playerId);
        if (lastInteraction == null) return false;
        return (System.currentTimeMillis() - lastInteraction) < INTERACTION_COOLDOWN_MS;
    }
    
    /**
     * Updates a player's interaction cooldown timestamp.
     *
     * @param playerId The UUID of the player
     */
    private void updateCooldown(UUID playerId) {
        lastInteractions.put(playerId, System.currentTimeMillis());
    }
}
