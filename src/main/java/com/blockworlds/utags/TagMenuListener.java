package com.blockworlds.utags;

import com.blockworlds.utags.services.SecurityService;
import com.blockworlds.utags.utils.ErrorHandler;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Listener for tag menu interactions with enhanced security
 */
public class TagMenuListener implements Listener {

    private final uTags plugin;
    private final SecurityService securityService;
    private final ErrorHandler errorHandler;
    
    // Keep track of player interactions for rate limiting
    private final Map<UUID, Long> lastInteractions = new HashMap<>();
    private static final long INTERACTION_COOLDOWN_MS = 250; // Anti-spam protection
    
    // Track which menus are open for which players
    private final Map<UUID, String> playerOpenMenus = new HashMap<>();

    public TagMenuListener(uTags plugin, SecurityService securityService, ErrorHandler errorHandler) {
        this.plugin = plugin;
        this.securityService = securityService;
        this.errorHandler = errorHandler;
    }

    /**
     * Handles inventory click events with enhanced security
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        try {
            if (!(event.getWhoClicked() instanceof Player)) {
                return;
            }

            Player player = (Player) event.getWhoClicked();
            Inventory inventory = event.getInventory();
            ItemStack clickedItem = event.getCurrentItem();

            String inventoryTitle = event.getView().getTitle();
            if (!isUTagsMenu(inventoryTitle)) {
                return;
            }

            // Always cancel the click to prevent item theft/duplication
            event.setCancelled(true);

            // Check for anti-spam
            if (isInteractionTooFrequent(player.getUniqueId())) {
                return;
            }
            updateLastInteraction(player.getUniqueId());

            // Protect against null/air clicks
            if (event.getSlot() == -999 || event.getRawSlot() > inventory.getSize()) {
                return;
            }
            
            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }

            // Record the menu interaction attempt
            playerOpenMenus.put(player.getUniqueId(), inventoryTitle);
            
            // Handle menu interaction with proper error handling
            handleMenuInteraction(event, inventoryTitle);
        } catch (Exception e) {
            errorHandler.logError("Error handling inventory click", e);
        }
    }
    
    /**
     * Prevents drag events in uTags menus
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        String inventoryTitle = event.getView().getTitle();
        if (isUTagsMenu(inventoryTitle)) {
            // Cancel drags in our menus to prevent item duplication/movement
            event.setCancelled(true);
        }
    }
    
    /**
     * Tracks menu closing for security monitoring
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        String inventoryTitle = event.getView().getTitle();
        
        if (isUTagsMenu(inventoryTitle)) {
            // Remove from tracking when menu is closed
            playerOpenMenus.remove(player.getUniqueId());
        }
    }

    /**
     * Checks if an inventory title belongs to a uTags menu
     */
    private boolean isUTagsMenu(String inventoryTitle) {
        return inventoryTitle.contains("uTags Menu")
                || inventoryTitle.contains("Select Prefix")
                || inventoryTitle.contains("Select Suffix")
                || inventoryTitle.contains("Tag Requests")
                || inventoryTitle.contains("Custom Tag Requests");
    }

    /**
     * Handles menu interaction with security checks
     */
    private void handleMenuInteraction(InventoryClickEvent event, String inventoryTitle) {
        if (inventoryTitle.contains("uTags Menu")) {
            handleTagMenuInteraction(event);
        } else if (inventoryTitle.contains("Select Prefix")) {
            handleTagSelection(event, TagType.PREFIX, Character.getNumericValue(inventoryTitle.charAt(inventoryTitle.length() - 1)));
        } else if (inventoryTitle.contains("Select Suffix")) {
            handleTagSelection(event, TagType.SUFFIX, Character.getNumericValue(inventoryTitle.charAt(inventoryTitle.length() - 1)));
        } else if (inventoryTitle.contains("Tag Requests") || inventoryTitle.contains("Custom Tag Requests")) {
            // Ensure player has admin permissions for request management
            Player player = (Player) event.getWhoClicked();
            if (!securityService.checkAdmin(player, "manage tag requests")) {
                return;
            }
        }
    }
    
    /**
     * Opens tag selection menu with security checks
     */
    public void openTagSelection(Player player, int pageIndex, TagType selectionType) {
        try {
            // Make sure the player is still online
            if (!player.isOnline()) {
                return;
            }
            
            List<Tag> tags = plugin.getAvailableTags(selectionType);
            List<Tag> availableTags = tags.stream()
                    .filter(tag -> tag.getType() == selectionType || tag.getType() == TagType.BOTH)
                    .collect(Collectors.toList());

            String inventoryTitle = selectionType == TagType.PREFIX ? "Select Prefix" : "Select Suffix";
            Inventory inventory = createInventoryFrame(54, inventoryTitle + " " + pageIndex,
                    Material.valueOf(plugin.getConfig().getString("frame-material", "BLACK_STAINED_GLASS_PANE")), player);

            populateTagSelectionInventory(player, inventory, availableTags, pageIndex);

            // Add custom tag slots
            if (selectionType == TagType.PREFIX) {
                int[] itemSlots = {1, 3, 5, 7};

                for (int i = 0; i < 4; i++) {
                    ItemStack customTagItem = createCustomTagMenuItem(player, i);
                    inventory.setItem(itemSlots[i], customTagItem);
                }
            }
            
            // Log the menu open event
            securityService.logSecurityEvent(Level.FINE, player, "MENU_OPEN", 
                "Player opened " + inventoryTitle + " menu (page " + pageIndex + ")");
            
            // Record this menu as open for the player
            playerOpenMenus.put(player.getUniqueId(), inventoryTitle + " " + pageIndex);
            
            player.openInventory(inventory);
        } catch (Exception e) {
            errorHandler.logError("Error opening tag selection menu", e);
        }
    }

    /**
     * Populates tag selection inventory securely
     */
    private void populateTagSelectionInventory(Player player, Inventory inventory, List<Tag> availableTags, int pageIndex) {
        try {
            int itemsPerPage = 28;
            int startIndex = pageIndex * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, availableTags.size());
            int[] itemSlots = {10, 11, 12, 13, 14, 15, 16,
                    19, 20, 21, 22, 23, 24, 25,
                    28, 29, 30, 31, 32, 33, 34,
                    37, 38, 39, 40, 41, 42, 43};

            int slotIndex = 0;
            for (int i = startIndex; i < endIndex; i++) {
                Tag tag = availableTags.get(i);
                // Check if the player has permission for the tag or if the tag is public
                if (tag.isPublic() && securityService.checkTagAccess(player, tag.getName())) {
                    ItemStack prefixItem = tag.getMaterial().clone(); // Clone to prevent modifying the original
                    ItemMeta prefixMeta = prefixItem.getItemMeta();
                    List<String> lore;
                    // Apply the display attribute of the tag to the item
                    prefixMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', tag.getDisplay()));
                    if (prefixMeta.hasLore()) {
                        lore = prefixMeta.getLore();
                        lore.add(" ");
                        lore.add(ChatColor.YELLOW + "Click to Select");
                        lore.add(ChatColor.WHITE + "You may also use:");
                        lore.add(ChatColor.YELLOW + "/tag set " + tag.getName());
                    } else
                        lore = (Arrays.asList(ChatColor.YELLOW + "Click to Select", ChatColor.WHITE + "You may also use:", ChatColor.YELLOW + "/tag set " + tag.getName()));
                    prefixMeta.setLore(lore);
                    prefixItem.setItemMeta(prefixMeta);

                    // Add the prefix item to the inventory
                    inventory.setItem(itemSlots[slotIndex], prefixItem);
                    slotIndex++;
                }
            }
            addExtraMenuItems(player, inventory, pageIndex, availableTags.size(), itemsPerPage);
        } catch (Exception e) {
            errorHandler.logError("Error populating tag selection inventory", e);
        }
    }

    /**
     * Adds extra menu items securely
     */
    private void addExtraMenuItems(Player player, Inventory inventory, int pageIndex, int numTags, int itemsPerPage) {
        try {
            addPlayerHead(player, inventory, 49);
            if (pageIndex > 0) {
                ItemStack prevPageItem = createNavigationArrow(ChatColor.AQUA + "Previous Page");
                inventory.setItem(45, prevPageItem);
            }

            if ((pageIndex + 1) * itemsPerPage < numTags) {
                ItemStack nextPageItem = createNavigationArrow(ChatColor.AQUA + "Next Page");
                inventory.setItem(53, nextPageItem);
            }
        } catch (Exception e) {
            errorHandler.logError("Error adding extra menu items", e);
        }
    }

    /**
     * Adds player head to inventory with security checks
     */
    public void addPlayerHead(Player player, Inventory inventory, int location) {
        try {
            // Add player's head to given location
            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta playerHeadMeta = (SkullMeta) playerHead.getItemMeta();
            playerHeadMeta.setOwningPlayer(player);
            playerHeadMeta.setDisplayName(ChatColor.YELLOW + player.getName());

            User lpUser = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId());
            if (lpUser == null) {
                errorHandler.logError("Failed to get LuckPerms user for " + player.getName(), null);
                return;
            }
            
            String prefix = lpUser.getCachedData().getMetaData().getPrefix();
            String suffix = lpUser.getCachedData().getMetaData().getSuffix();

            String currentTitle = ChatColor.GRAY + "Current Title(s)";
            if (prefix != null) {
                currentTitle += "\n" + ChatColor.GRAY + "Prefix: " + ChatColor.translateAlternateColorCodes('&', prefix);
            }
            if (suffix != null) {
                currentTitle += "\n" + ChatColor.GRAY + "Suffix: " + ChatColor.translateAlternateColorCodes('&', suffix);
            }

            playerHeadMeta.setLore(Arrays.asList(currentTitle.split("\n")));
            playerHead.setItemMeta(playerHeadMeta);
            inventory.setItem(location, playerHead);
        } catch (Exception e) {
            errorHandler.logError("Error adding player head", e);
        }
    }

    /**
     * Creates navigation arrow item securely
     */
    private ItemStack createNavigationArrow(String displayName) {
        ItemStack arrowItem = new ItemStack(Material.ARROW);
        ItemMeta arrowMeta = arrowItem.getItemMeta();
        arrowMeta.setDisplayName(displayName);
        arrowItem.setItemMeta(arrowMeta);
        return arrowItem;
    }

    /**
     * Handles tag menu interaction securely
     */
    private void handleTagMenuInteraction(InventoryClickEvent event) {
        try {
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem == null || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) {
                return;
            }

            String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

            if ("Change Prefix".equals(itemName)) {
                // Log this interaction
                securityService.logSecurityEvent(Level.FINE, player, "MENU_ACTION", 
                    "Player selected 'Change Prefix' option");
                openTagSelection(player, 0, TagType.PREFIX);
            } else if ("Change Suffix".equals(itemName)) {
                // Log this interaction
                securityService.logSecurityEvent(Level.FINE, player, "MENU_ACTION", 
                    "Player selected 'Change Suffix' option");
                openTagSelection(player, 0, TagType.SUFFIX);
            }
        } catch (Exception e) {
            errorHandler.logError("Error handling tag menu interaction", e);
        }
    }

    /**
     * Handles tag selection securely
     */
    private void handleTagSelection(InventoryClickEvent event, TagType tagType, int currentPage) {
        try {
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem == null || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) {
                return;
            }

            String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

            // Handle navigation buttons
            if (itemName.equals("Previous Page")) {
                openTagSelection(player, currentPage - 1, tagType);
                return;
            } else if (itemName.equals("Next Page")) {
                openTagSelection(player, currentPage + 1, tagType);
                return;
            }
            
            // Handle tag selection
            String tag;
            int slotIndex = 0;
            
            // Handle custom tag slots
            if ((event.getRawSlot() == 1 || event.getRawSlot() == 3 || event.getRawSlot() == 5 || event.getRawSlot() == 7) && tagType == TagType.PREFIX) {
                slotIndex = (event.getSlot() + 1) / 2;
                tag = player.getName() + slotIndex;
                
                // Check permission for custom slot
                if (!securityService.checkTagAccess(player, tag)) {
                    return; // Security service already logs this
                }
            } else {
                // Regular tag selection
                tag = clickedItem.getItemMeta().getDisplayName();
                
                // Check if the tag is in the list of available tags and if the player has permission
                boolean hasPermission = securityService.checkTagAccess(player, plugin.getTagNameByDisplay(tag));
                boolean isAvailableTag = plugin.getAvailableTags(tagType).stream().anyMatch(availableTag -> 
                    availableTag.getDisplay().equals(tag) || availableTag.getName().equals(tag));

                if (!hasPermission || !isAvailableTag) {
                    // Log potential tampering attempt
                    securityService.logSecurityEvent(Level.WARNING, player, "INVALID_TAG_SELECTION", 
                        "Player attempted to select a tag they don't have permission for: " + tag);
                    return;
                }
            }

            // Update the player's LuckPerms prefix
            String tagDisplay;
            if ((event.getRawSlot() == 1 || event.getRawSlot() == 3 || event.getRawSlot() == 5 || event.getRawSlot() == 7) && tagType == TagType.PREFIX) {
                tagDisplay = plugin.getTagDisplayByName(tag);
            } else {
                tagDisplay = tag;
            }
            
            // Log this tag selection
            securityService.logSecurityEvent(Level.INFO, player, "TAG_SELECTED", 
                "Player selected " + tagType + ": " + tagDisplay);
            
            // Set the tag
            plugin.setPlayerTag(player, tagDisplay, tagType);

            // Close the current inventory and inform the player
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "Your " + tagType + " has been updated to: " + 
                              ChatColor.translateAlternateColorCodes('&', tagDisplay));
        } catch (Exception e) {
            errorHandler.logError("Error handling tag selection", e);
        }
    }

    /**
     * Creates inventory frame securely
     */
    public Inventory createInventoryFrame(int size, String title, Material frameMaterial, Player player) {
        try {
            Inventory inventory = Bukkit.createInventory(player, size, title);

            ItemStack frameItem = new ItemStack(frameMaterial);
            ItemMeta frameMeta = frameItem.getItemMeta();
            frameMeta.setDisplayName(" ");
            frameItem.setItemMeta(frameMeta);

            for (int i = 0; i < size; i++) {
                if (i < 9 || i > size - 10 || i % 9 == 0 || (i + 1) % 9 == 0) {
                    inventory.setItem(i, frameItem);
                }
            }

            return inventory;
        } catch (Exception e) {
            errorHandler.logError("Error creating inventory frame", e);
            // Fallback to basic inventory in case of error
            return Bukkit.createInventory(player, size, title);
        }
    }

    /**
     * Creates custom tag menu item securely
     */
    private ItemStack createCustomTagMenuItem(Player player, int slotIndex) {
        try {
            String permissionBase = "utags.custom";
            String permissionTag = "utags.tag." + player.getName();
            ItemStack item;
            ItemMeta meta;

            if (player.hasPermission(permissionTag + (slotIndex + 1))) {
                // Player has this custom tag
                item = new ItemStack(Material.PLAYER_HEAD, 1);
                SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
                skullMeta.setOwningPlayer(player);
                meta = skullMeta;
                String tagDisplay = plugin.getTagDisplayByName(player.getName() + (slotIndex + 1));
                if (tagDisplay == null) {
                    tagDisplay = "Not Set";
                } else {
                    tagDisplay = ChatColor.translateAlternateColorCodes('&', tagDisplay);
                }

                meta.setDisplayName(ChatColor.GOLD + "Custom Tag #" + (slotIndex + 1) + ": " + tagDisplay);
                meta.setLore(Arrays.asList(
                    ChatColor.YELLOW + "Click to Select", 
                    ChatColor.WHITE + "You may also use:", 
                    ChatColor.YELLOW + "/tag set " + (player.getName() + (slotIndex + 1)))
                );
            } else if (player.hasPermission(permissionBase + (slotIndex+ 1))) {
                // Player can request this tag
                item = new ItemStack(Material.GREEN_STAINED_GLASS_PANE, 1);
                meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.YELLOW + "Request Custom Tag #" + (slotIndex+ 1));
                meta.setLore(Arrays.asList(
                    ChatColor.WHITE + "You can request a custom tag using", 
                    ChatColor.WHITE + "/tag request")
                );
            } else {
                // Player doesn't have access to this tag slot
                item = new ItemStack(Material.BARRIER, 1);
                meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Unlock Custom Tag #" + (slotIndex+ 1));
                meta.setLore(Arrays.asList(
                    ChatColor.WHITE + "Become a premium member", 
                    ChatColor.WHITE + "to unlock custom tags.")
                );
            }

            item.setItemMeta(meta);
            return item;
        } catch (Exception e) {
            errorHandler.logError("Error creating custom tag menu item", e);
            // Return a fallback item in case of error
            return new ItemStack(Material.BARRIER);
        }
    }
    
    /**
     * Checks if player interactions are too frequent (anti-spam)
     */
    private boolean isInteractionTooFrequent(UUID playerId) {
        long now = System.currentTimeMillis();
        Long lastInteraction = lastInteractions.get(playerId);
        
        if (lastInteraction != null) {
            return (now - lastInteraction) < INTERACTION_COOLDOWN_MS;
        }
        
        return false;
    }
    
    /**
     * Updates the last interaction timestamp for a player
     */
    private void updateLastInteraction(UUID playerId) {
        lastInteractions.put(playerId, System.currentTimeMillis());
    }
    
    /**
     * Cleans up resources when the plugin is disabled
     */
    public void cleanup() {
        playerOpenMenus.clear();
        lastInteractions.clear();
    }
}
