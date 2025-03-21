package com.blockworlds.utags;

import com.blockworlds.utags.model.TagType;
import com.blockworlds.utags.utils.Utils;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TagMenuListener implements Listener {
    private final uTags plugin;
    private final Map<UUID, Long> lastInteractions = new HashMap<>();
    private static final long INTERACTION_COOLDOWN_MS = 250;
    private final Map<UUID, String> playerOpenMenus = new HashMap<>();

    public TagMenuListener(uTags plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        String inventoryTitle = event.getView().getTitle();
        
        if (!isUTagsMenu(inventoryTitle)) return;
        
        event.setCancelled(true);
        
        if (isInteractionTooFrequent(player.getUniqueId())) return;
        updateLastInteraction(player.getUniqueId());
        
        if (clickedItem == null || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) return;

        playerOpenMenus.put(player.getUniqueId(), inventoryTitle);
        
        if (inventoryTitle.contains("uTags Menu")) {
            handleMainMenuClick(player, clickedItem);
        } else if (inventoryTitle.contains("Select Prefix")) {
            handleTagSelectionClick(event, player, TagType.PREFIX, extractPageNumber(inventoryTitle));
        } else if (inventoryTitle.contains("Select Suffix")) {
            handleTagSelectionClick(event, player, TagType.SUFFIX, extractPageNumber(inventoryTitle));
        }
    }
    
    private boolean isUTagsMenu(String inventoryTitle) {
        return inventoryTitle.contains("uTags Menu")
                || inventoryTitle.contains("Select Prefix")
                || inventoryTitle.contains("Select Suffix")
                || inventoryTitle.contains("Tag Requests")
                || inventoryTitle.contains("Custom Tag Requests");
    }
    
    private int extractPageNumber(String title) {
        try {
            return Character.getNumericValue(title.charAt(title.length() - 1));
        } catch (Exception e) {
            return 0;
        }
    }
    
    private void handleMainMenuClick(Player player, ItemStack clickedItem) {
        String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

        if ("Change Prefix".equals(itemName)) {
            plugin.openTagSelection(player, 0, TagType.PREFIX);
        } else if ("Change Suffix".equals(itemName)) {
            plugin.openTagSelection(player, 0, TagType.SUFFIX);
        }
    }
    
    private void handleTagSelectionClick(InventoryClickEvent event, Player player, TagType tagType, int currentPage) {
        ItemStack clickedItem = event.getCurrentItem();
        String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

        // Handle navigation buttons
        if (itemName.equals("Previous Page")) {
            plugin.openTagSelection(player, currentPage - 1, tagType);
            return;
        } else if (itemName.equals("Next Page")) {
            plugin.openTagSelection(player, currentPage + 1, tagType);
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
            tagName = plugin.getTagNameByDisplay(tagDisplay);
        }
        
        if (tagName != null && Utils.hasTagPermission(player, tagName)) {
            plugin.setPlayerTag(player, tagDisplay, tagType);
            player.closeInventory();
            Utils.sendSuccess(player, "Your " + tagType + " has been updated to: " + 
                       ChatColor.translateAlternateColorCodes('&', tagDisplay));
        }
    }
    
    private boolean isInteractionTooFrequent(UUID playerId) {
        long now = System.currentTimeMillis();
        Long lastInteraction = lastInteractions.get(playerId);
        
        if (lastInteraction != null) {
            return (now - lastInteraction) < INTERACTION_COOLDOWN_MS;
        }
        
        return false;
    }
    
    private void updateLastInteraction(UUID playerId) {
        lastInteractions.put(playerId, System.currentTimeMillis());
    }
    
    public int getOpenMenuCount() {
        return playerOpenMenus.size();
    }
    
    public void cleanup() {
        playerOpenMenus.clear();
        lastInteractions.clear();
    }
}
