package com.blockworlds.utags.controller.impl;

import com.blockworlds.utags.model.TagType;
import com.blockworlds.utags.service.MenuService;
import com.blockworlds.utags.service.TagService;
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
    
    // Simple cooldown mechanism to prevent click spam
    private final Map<UUID, Long> lastInteractions = new HashMap<>();
    private static final long INTERACTION_COOLDOWN_MS = 250;

    /**
     * Creates a new MenuClickController with required dependencies.
     *
     * @param tagService The tag service for tag operations
     * @param menuService The menu service for menu operations
     */
    public MenuClickController(TagService tagService, MenuService menuService) {
        this.tagService = tagService;
        this.menuService = menuService;
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
        if (title.contains("uTags Menu")) {
            handleMainMenu(player, itemName);
        } else if (title.contains("Select Prefix")) {
            handleTagSelection(player, clickedItem, TagType.PREFIX, extractPageNumber(title));
        } else if (title.contains("Select Suffix")) {
            handleTagSelection(player, clickedItem, TagType.SUFFIX, extractPageNumber(title));
        } else if (title.contains("Custom Tag Requests")) {
            handleRequestsMenu(player, clickedItem, event.isLeftClick());
        }
    }
    
    private boolean isUTagsMenu(String title) {
        return title.contains("uTags Menu") || 
               title.contains("Select Prefix") || 
               title.contains("Select Suffix") || 
               title.contains("Custom Tag Requests");
    }
    
    private int extractPageNumber(String title) {
        try {
            return Character.getNumericValue(title.charAt(title.length() - 1));
        } catch (Exception e) {
            return 0;
        }
    }
    
    private void handleMainMenu(Player player, String itemName) {
        if ("Change Prefix".equals(itemName)) {
            menuService.openTagSelectionMenu(player, 0, TagType.PREFIX);
        } else if ("Change Suffix".equals(itemName)) {
            menuService.openTagSelectionMenu(player, 0, TagType.SUFFIX);
        }
    }
    
    private void handleTagSelection(Player player, ItemStack clickedItem, TagType tagType, int currentPage) {
        String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

        // Handle navigation buttons
        if ("Previous Page".equals(itemName)) {
            menuService.openTagSelectionMenu(player, currentPage - 1, tagType);
            return;
        } else if ("Next Page".equals(itemName)) {
            menuService.openTagSelectionMenu(player, currentPage + 1, tagType);
            return;
        } else if ("Return to Main Menu".equals(itemName)) {
            menuService.openMainMenu(player);
            return;
        }
        
        // Handle tag selection
        String tagDisplay = clickedItem.getItemMeta().getDisplayName();
        String tagName = null;
        
        // Handle custom tag slots
        if (itemName.startsWith("Custom Tag #")) {
            String[] parts = itemName.split(":");
            if (parts.length > 1) {
                tagName = parts[0].trim();
                tagDisplay = parts[1].trim();
            } else {
                return; // Invalid format
            }
        } else {
            // Get tag name from its display text
            tagName = tagService.getTagName(tagDisplay);
        }
        
        // Set the player's tag if valid
        if (tagName != null) {
            tagService.setPlayerTagByName(player, tagName, tagType);
            player.closeInventory();
        }
    }
    
    private void handleRequestsMenu(Player player, ItemStack clickedItem, boolean isLeftClick) {
        if (!clickedItem.getItemMeta().hasDisplayName()) return;
        
        String playerName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        
        if (isLeftClick) {
            tagService.acceptCustomTagRequest(playerName);
        } else {
            tagService.denyCustomTagRequest(playerName);
        }
        
        // Refresh the requests menu
        menuService.openRequestsMenu(player);
    }
    
    private boolean isOnCooldown(UUID playerId) {
        Long lastInteraction = lastInteractions.get(playerId);
        if (lastInteraction == null) return false;
        return (System.currentTimeMillis() - lastInteraction) < INTERACTION_COOLDOWN_MS;
    }
    
    private void updateCooldown(UUID playerId) {
        lastInteractions.put(playerId, System.currentTimeMillis());
    }
}
