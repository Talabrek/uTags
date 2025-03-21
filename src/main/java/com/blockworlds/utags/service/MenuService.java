package com.blockworlds.utags.service;

import com.blockworlds.utags.model.Result;
import com.blockworlds.utags.model.TagType;
import org.bukkit.entity.Player;

/**
 * Service interface for menu-related operations.
 * Provides methods for opening various tag menus and handling menu interactions.
 */
public interface MenuService {
    
    /**
     * Opens the main tag menu for a player.
     *
     * @param player The player to open the menu for
     * @return Result containing success/failure status
     */
    Result<Boolean> openMainMenu(Player player);
    
    /**
     * Opens the tag selection menu for a player.
     *
     * @param player The player to open the menu for
     * @param page The page index to display
     * @param tagType The type of tags to display (PREFIX/SUFFIX)
     * @return Result containing success/failure status
     */
    Result<Boolean> openTagSelectionMenu(Player player, int page, TagType tagType);
    
    /**
     * Opens the tag requests menu for a player with admin privileges.
     *
     * @param player The player to open the menu for
     * @return Result containing success/failure status
     */
    Result<Boolean> openRequestsMenu(Player player);
    
    /**
     * Updates a player's view of the current menu.
     * Used when menu content changes but the menu type stays the same.
     *
     * @param player The player whose menu to update
     * @return Result containing success/failure status
     */
    Result<Boolean> refreshCurrentMenu(Player player);
    
    /**
     * Closes any open menus for a player.
     *
     * @param player The player whose menus to close
     */
    void closeMenus(Player player);
}
