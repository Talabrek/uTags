package com.blockworlds.utags.service.impl;

import com.blockworlds.utags.model.CustomTagRequest;
import com.blockworlds.utags.model.Result;
import com.blockworlds.utags.model.Tag;
import com.blockworlds.utags.model.TagType;
import com.blockworlds.utags.repository.RequestRepository;
import com.blockworlds.utags.repository.TagRepository;
import com.blockworlds.utags.service.MenuService;
import com.blockworlds.utags.view.InventoryFactory;
import com.blockworlds.utags.view.MenuBuilder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of the MenuService interface.
 * Manages menu creation and interaction for the uTags plugin.
 */
public class MenuServiceImpl implements MenuService {
    
    private final JavaPlugin plugin;
    private final TagRepository tagRepository;
    private final RequestRepository requestRepository;
    private final MenuBuilder menuBuilder;
    private final InventoryFactory inventoryFactory;
    
    // Track player menu states
    private final Map<UUID, MenuState> playerMenuStates = new HashMap<>();
    
    /**
     * Creates a new MenuServiceImpl with dependencies.
     *
     * @param plugin The JavaPlugin instance
     * @param tagRepository The repository for tag data
     * @param requestRepository The repository for request data
     * @param menuBuilder The builder for creating menus
     * @param inventoryFactory The factory for creating inventories
     */
    public MenuServiceImpl(
            JavaPlugin plugin,
            TagRepository tagRepository,
            RequestRepository requestRepository,
            MenuBuilder menuBuilder,
            InventoryFactory inventoryFactory) {
        this.plugin = plugin;
        this.tagRepository = tagRepository;
        this.requestRepository = requestRepository;
        this.menuBuilder = menuBuilder;
        this.inventoryFactory = inventoryFactory;
    }
    
    @Override
    public Result<Boolean> openMainMenu(Player player) {
        try {
            Inventory menu = menuBuilder.buildMainMenu(player);
            player.openInventory(menu);
            
            // Track menu state
            playerMenuStates.put(player.getUniqueId(), new MenuState(MenuType.MAIN, 0, null));
            
            return Result.success(true);
        } catch (Exception e) {
            return Result.error("Failed to open main menu: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Result<Boolean> openTagSelectionMenu(Player player, int page, TagType tagType) {
        try {
            // Get available tags
            Result<List<Tag>> tagsResult = tagRepository.getAvailableTags(tagType);
            
            if (!tagsResult.isSuccess()) {
                return Result.failure("Failed to retrieve tags: " + tagsResult.getMessage());
            }
            
            List<Tag> tags = tagsResult.getValue();
            
            // Validate page number
            int totalPages = calculateTotalPages(tags.size(), 28);
            if (page < 0) page = 0;
            if (page >= totalPages) page = totalPages - 1;
            
            // Create and open menu
            Inventory menu = menuBuilder.buildTagSelectionMenu(player, tags, page, tagType);
            player.openInventory(menu);
            
            // Track menu state
            playerMenuStates.put(player.getUniqueId(), new MenuState(MenuType.TAG_SELECTION, page, tagType));
            
            return Result.success(true);
        } catch (Exception e) {
            return Result.error("Failed to open tag selection menu: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Result<Boolean> openRequestsMenu(Player player) {
        try {
            // Check permission
            if (!player.hasPermission("utags.admin")) {
                return Result.failure("You don't have permission to view tag requests");
            }
            
            // Get pending requests
            Result<List<CustomTagRequest>> requestsResult = requestRepository.getAllRequests();
            
            if (!requestsResult.isSuccess()) {
                return Result.failure("Failed to retrieve requests: " + requestsResult.getMessage());
            }
            
            List<CustomTagRequest> requests = requestsResult.getValue();
            
            // Create and open menu
            Inventory menu = menuBuilder.buildRequestsMenu(player, requests);
            player.openInventory(menu);
            
            // Track menu state
            playerMenuStates.put(player.getUniqueId(), new MenuState(MenuType.REQUESTS, 0, null));
            
            return Result.success(true);
        } catch (Exception e) {
            return Result.error("Failed to open requests menu: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Result<Boolean> refreshCurrentMenu(Player player) {
        try {
            UUID playerId = player.getUniqueId();
            MenuState state = playerMenuStates.get(playerId);
            
            if (state == null) {
                return Result.failure("No menu state found for player");
            }
            
            // Recreate the current menu based on state
            switch (state.type) {
                case MAIN:
                    return openMainMenu(player);
                case TAG_SELECTION:
                    return openTagSelectionMenu(player, state.page, state.tagType);
                case REQUESTS:
                    return openRequestsMenu(player);
                default:
                    return Result.failure("Unknown menu type");
            }
        } catch (Exception e) {
            return Result.error("Failed to refresh menu: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void closeMenus(Player player) {
        // Remove menu state
        playerMenuStates.remove(player.getUniqueId());
        
        // Close inventory if player is online
        if (player.isOnline()) {
            player.closeInventory();
        }
    }
    
    /**
     * Calculates the total number of pages needed.
     *
     * @param totalItems The total number of items
     * @param itemsPerPage The number of items per page
     * @return The total number of pages
     */
    private int calculateTotalPages(int totalItems, int itemsPerPage) {
        return Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));
    }
    
    /**
     * Gets the current menu state for a player.
     *
     * @param playerId The UUID of the player
     * @return The menu state, or null if not found
     */
    public MenuState getPlayerMenuState(UUID playerId) {
        return playerMenuStates.get(playerId);
    }
    
    /**
     * Enum representing the type of menu a player has open.
     */
    private enum MenuType {
        MAIN,
        TAG_SELECTION,
        REQUESTS
    }
    
    /**
     * Class representing the state of a player's menu.
     */
    public static class MenuState {
        private final MenuType type;
        private final int page;
        private final TagType tagType;
        
        public MenuState(MenuType type, int page, TagType tagType) {
            this.type = type;
            this.page = page;
            this.tagType = tagType;
        }
        
        public MenuType getType() {
            return type;
        }
        
        public int getPage() {
            return page;
        }
        
        public TagType getTagType() {
            return tagType;
        }
    }
}
