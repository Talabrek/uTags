package com.blockworlds.utags.services;

import com.blockworlds.utags.CustomTagRequest;
import com.blockworlds.utags.Tag;
import com.blockworlds.utags.TagType;
import com.blockworlds.utags.uTags;
import com.blockworlds.utags.utils.ErrorHandler;
import com.blockworlds.utags.utils.MenuUtils;
import com.blockworlds.utags.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service class for menu-related operations in the uTags plugin.
 * Centralizes menu creation, management, and interaction handling.
 */
public class MenuService {

    private final uTags plugin;
    private final ErrorHandler errorHandler;

    /**
     * Creates a new MenuService.
     *
     * @param plugin The uTags plugin instance
     * @param errorHandler The error handler to use
     */
    public MenuService(uTags plugin, ErrorHandler errorHandler) {
        this.plugin = plugin;
        this.errorHandler = errorHandler;
    }

    /**
     * Opens the main tag menu for a player.
     *
     * @param player The player to open the menu for
     */
    public void openTagMenu(Player player) {
        try {
            Inventory tagMenu = Bukkit.createInventory(player, 9, "uTags Menu");

            // Create prefix item
            ItemStack changePrefixItem = MenuUtils.createSimpleItem(
                Material.NAME_TAG, 
                ChatColor.GREEN + "Change Prefix", 
                null
            );

            // Create suffix item
            ItemStack changeSuffixItem = MenuUtils.createSimpleItem(
                Material.NAME_TAG, 
                ChatColor.YELLOW + "Change Suffix", 
                null
            );

            // Add items to menu
            tagMenu.setItem(2, changePrefixItem);
            tagMenu.setItem(6, changeSuffixItem);

            // Add player's head to middle location
            addPlayerInfoToMenu(player, tagMenu, 4);

            // Open the menu for the player
            player.openInventory(tagMenu);
        } catch (Exception e) {
            errorHandler.logError("Error opening tag menu", e);
        }
    }

    /**
     * Opens the tag selection menu for a player.
     *
     * @param player The player to open the menu for
     * @param pageIndex The page index to display
     * @param selectionType The type of tags to display (PREFIX/SUFFIX)
     */
    public void openTagSelection(Player player, int pageIndex, TagType selectionType) {
        try {
            // Get available tags
            List<Tag> tags = plugin.getAvailableTags(selectionType);
            List<Tag> availableTags = tags.stream()
                    .filter(tag -> tag.getType() == selectionType || tag.getType() == TagType.BOTH)
                    .collect(Collectors.toList());

            // Create inventory
            String inventoryTitle = selectionType == TagType.PREFIX ? "Select Prefix" : "Select Suffix";
            Inventory inventory = MenuUtils.createInventoryFrame(
                54, 
                inventoryTitle + " " + pageIndex,
                Material.valueOf(plugin.getConfig().getString("frame-material", "BLACK_STAINED_GLASS_PANE")), 
                player
            );

            // Populate inventory with tags
            populateTagSelectionInventory(player, inventory, availableTags, pageIndex);

            // Add custom tag slots if this is a prefix selection
            if (selectionType == TagType.PREFIX) {
                addCustomTagSlots(player, inventory);
            }

            // Open the inventory
            player.openInventory(inventory);
        } catch (Exception e) {
            errorHandler.logError("Error opening tag selection menu", e);
        }
    }

    /**
     * Populates the tag selection inventory with available tags.
     *
     * @param player The player viewing the inventory
     * @param inventory The inventory to populate
     * @param availableTags The available tags to display
     * @param pageIndex The current page index
     */
    private void populateTagSelectionInventory(Player player, Inventory inventory, List<Tag> availableTags, int pageIndex) {
        int itemsPerPage = 28;
        int startIndex = pageIndex * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, availableTags.size());
        int[] itemSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        int slotIndex = 0;
        for (int i = startIndex; i < endIndex; i++) {
            Tag tag = availableTags.get(i);
            // Check if the player has permission for the tag or if the tag is public
            if (tag.isPublic() && PermissionUtils.hasTagPermission(player, tag.getName())) {
                // Create tag item
                ItemStack tagItem = tag.getMaterial().clone();
                ItemMeta tagMeta = tagItem.getItemMeta();
                List<String> lore;

                // Apply the display attribute of the tag to the item
                tagMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', tag.getDisplay()));
                
                // Create lore
                if (tagMeta.hasLore()) {
                    lore = tagMeta.getLore();
                    lore.add(" ");
                    lore.add(ChatColor.YELLOW + "Click to Select");
                    lore.add(ChatColor.WHITE + "You may also use:");
                    lore.add(ChatColor.YELLOW + "/tag set " + tag.getName());
                } else {
                    lore = Arrays.asList(
                        ChatColor.YELLOW + "Click to Select",
                        ChatColor.WHITE + "You may also use:",
                        ChatColor.YELLOW + "/tag set " + tag.getName()
                    );
                }
                
                tagMeta.setLore(lore);
                tagItem.setItemMeta(tagMeta);

                // Add the tag item to the inventory
                inventory.setItem(itemSlots[slotIndex], tagItem);
                slotIndex++;
            }
        }

        // Add navigation buttons and player info
        addMenuNavigation(player, inventory, pageIndex, availableTags.size(), itemsPerPage);
    }

    /**
     * Adds navigation buttons and player info to a menu.
     *
     * @param player The player viewing the inventory
     * @param inventory The inventory to add items to
     * @param pageIndex The current page index
     * @param totalTags The total number of tags available
     * @param itemsPerPage The number of items per page
     */
    private void addMenuNavigation(Player player, Inventory inventory, int pageIndex, int totalTags, int itemsPerPage) {
        // Add player head
        addPlayerInfoToMenu(player, inventory, 49);

        // Add previous page button if not on first page
        if (pageIndex > 0) {
            ItemStack prevPageItem = MenuUtils.createNavigationButton(ChatColor.AQUA + "Previous Page");
            inventory.setItem(45, prevPageItem);
        }

        // Add next page button if more pages available
        if ((pageIndex + 1) * itemsPerPage < totalTags) {
            ItemStack nextPageItem = MenuUtils.createNavigationButton(ChatColor.AQUA + "Next Page");
            inventory.setItem(53, nextPageItem);
        }
    }

    /**
     * Adds custom tag slots to the inventory.
     *
     * @param player The player viewing the inventory
     * @param inventory The inventory to add slots to
     */
    private void addCustomTagSlots(Player player, Inventory inventory) {
        int[] itemSlots = {1, 3, 5, 7};

        for (int i = 0; i < 4; i++) {
            ItemStack customTagItem = createCustomTagMenuItem(player, i);
            inventory.setItem(itemSlots[i], customTagItem);
        }
    }

    /**
     * Creates a custom tag menu item.
     *
     * @param player The player to create the item for
     * @param slotIndex The slot index (0-3)
     * @return The created item
     */
    private ItemStack createCustomTagMenuItem(Player player, int slotIndex) {
        String permissionBase = "utags.custom";
        String permissionTag = "utags.tag." + player.getName();
        ItemStack item;
        ItemMeta meta;
        int tagNumber = slotIndex + 1;

        if (PermissionUtils.hasCustomTag(player, tagNumber)) {
            // Player has this custom tag
            item = new ItemStack(Material.PLAYER_HEAD, 1);
            SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
            skullMeta.setOwningPlayer(player);
            meta = skullMeta;
            
            // Get tag display
            String tagDisplay = plugin.getTagDisplayByName(player.getName() + tagNumber);
            if (tagDisplay == null) {
                tagDisplay = "Not Set";
            } else {
                tagDisplay = ChatColor.translateAlternateColorCodes('&', tagDisplay);
            }

            meta.setDisplayName(ChatColor.GOLD + "Custom Tag #" + tagNumber + ": " + tagDisplay);
            meta.setLore(Arrays.asList(
                ChatColor.YELLOW + "Click to Select",
                ChatColor.WHITE + "You may also use:",
                ChatColor.YELLOW + "/tag set " + (player.getName() + tagNumber)
            ));
        } else if (PermissionUtils.hasCustomSlotPermission(player, tagNumber)) {
            // Player can request this custom tag
            item = new ItemStack(Material.GREEN_STAINED_GLASS_PANE, 1);
            meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + "Request Custom Tag #" + tagNumber);
            meta.setLore(Arrays.asList(
                ChatColor.WHITE + "You can request a custom tag using",
                ChatColor.WHITE + "/tag request"
            ));
        } else {
            // Player doesn't have permission for this slot
            item = new ItemStack(Material.BARRIER, 1);
            meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Unlock Custom Tag #" + tagNumber);
            meta.setLore(Arrays.asList(
                ChatColor.WHITE + "Become a premium member",
                ChatColor.WHITE + "to unlock custom tags."
            ));
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Adds player information to a menu.
     *
     * @param player The player to display information for
     * @param inventory The inventory to add the info to
     * @param slot The slot to place the info in
     */
    private void addPlayerInfoToMenu(Player player, Inventory inventory, int slot) {
        try {
            // Get player's current prefix and suffix
            String prefix = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId()).getCachedData().getMetaData().getPrefix();
            String suffix = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId()).getCachedData().getMetaData().getSuffix();
            
            // Add the player head with tag info
            MenuUtils.addPlayerHeadWithTags(player, inventory, slot, prefix, suffix);
        } catch (Exception e) {
            errorHandler.logError("Error adding player info to menu", e);
            
            // Fallback to simple player head
            ItemStack playerHead = MenuUtils.createPlayerHead(player, ChatColor.YELLOW + player.getName(), null);
            inventory.setItem(slot, playerHead);
        }
    }

    /**
     * Opens the tag requests menu for a player.
     *
     * @param player The player to open the menu for
     */
    public void openRequestsMenu(Player player) {
        try {
            List<CustomTagRequest> requests = plugin.getCustomTagRequests();
            int rows = Math.max(1, (int) Math.ceil(requests.size() / 9.0));
            Inventory requestsMenu = Bukkit.createInventory(player, rows * 9, "Custom Tag Requests");

            for (CustomTagRequest request : requests) {
                // Create player head for each request
                ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) playerHead.getItemMeta();

                if (meta != null) {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(request.getPlayerUuid()));
                    meta.setDisplayName(ChatColor.GREEN + request.getPlayerName());
                    
                    // Add tag display and instructions
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GRAY + "Requested Tag: " + ChatColor.translateAlternateColorCodes('&', request.getTagDisplay()));
                    lore.add("");
                    lore.add(ChatColor.YELLOW + "Left-click to accept");
                    lore.add(ChatColor.RED + "Right-click to deny");
                    meta.setLore(lore);
                    
                    playerHead.setItemMeta(meta);
                }

                requestsMenu.addItem(playerHead);
            }

            player.openInventory(requestsMenu);
        } catch (Exception e) {
            errorHandler.logError("Error opening requests menu", e);
        }
    }
}
