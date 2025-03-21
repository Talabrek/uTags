package com.blockworlds.utags.view;

import com.blockworlds.utags.model.CustomTagRequest;
import com.blockworlds.utags.model.Tag;
import com.blockworlds.utags.model.TagType;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
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

/**
 * Builds inventory menus for the uTags plugin.
 * Handles the construction of various menu types with consistent formatting.
 */
public class MenuBuilder {
    
    private final InventoryFactory inventoryFactory;
    private final LuckPerms luckPerms;
    private final String frameMaterial;
    
    /**
     * Creates a new MenuBuilder with dependencies.
     *
     * @param inventoryFactory The factory for creating inventories
     * @param luckPerms The LuckPerms instance for permission data
     * @param frameMaterial The material to use for menu frames
     */
    public MenuBuilder(InventoryFactory inventoryFactory, LuckPerms luckPerms, String frameMaterial) {
        this.inventoryFactory = inventoryFactory;
        this.luckPerms = luckPerms;
        this.frameMaterial = frameMaterial;
    }
    
    /**
     * Builds the main menu for tag selection.
     *
     * @param player The player to build the menu for
     * @return The created inventory
     */
    public Inventory buildMainMenu(Player player) {
        Inventory menu = Bukkit.createInventory(player, 9, "uTags Menu");
        
        // Create prefix button
        ItemStack prefixButton = inventoryFactory.createButton(
            Material.NAME_TAG, 
            ChatColor.GREEN + "Change Prefix",
            Arrays.asList(ChatColor.GRAY + "Click to select a prefix tag")
        );
        
        // Create suffix button
        ItemStack suffixButton = inventoryFactory.createButton(
            Material.NAME_TAG, 
            ChatColor.YELLOW + "Change Suffix",
            Arrays.asList(ChatColor.GRAY + "Click to select a suffix tag")
        );
        
        // Add buttons to menu
        menu.setItem(2, prefixButton);
        menu.setItem(6, suffixButton);
        
        // Add player's head with current tag info
        addPlayerHeadToMenu(player, menu, 4);
        
        return menu;
    }
    
    /**
     * Builds the tag selection menu.
     *
     * @param player The player to build the menu for
     * @param availableTags The tags available for selection
     * @param page The page number to display
     * @param tagType The type of tags to display
     * @return The created inventory
     */
    public Inventory buildTagSelectionMenu(Player player, List<Tag> availableTags, int page, TagType tagType) {
        String title = (tagType == TagType.PREFIX ? "Select Prefix" : "Select Suffix") + " " + page;
        Material frameMatl = Material.valueOf(frameMaterial);
        
        // Create framed inventory
        Inventory menu = inventoryFactory.createFramedInventory(54, title, frameMatl, player);
        
        // Calculate pagination
        int itemsPerPage = 28;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, availableTags.size());
        
        // Define grid layout slots
        int[] slots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };
        
        // Add tag items
        int slotIndex = 0;
        for (int i = startIndex; i < endIndex && slotIndex < slots.length; i++) {
            Tag tag = availableTags.get(i);
            if (tag.isPublic() && player.hasPermission("utags.tag." + tag.getName())) {
                menu.setItem(slots[slotIndex], createTagItem(tag));
                slotIndex++;
            }
        }
        
        // Add navigation buttons
        addNavigationButtons(menu, page, Math.ceil((double) availableTags.size() / itemsPerPage));
        
        // Add player info
        addPlayerHeadToMenu(player, menu, 49);
        
        // Add custom tag slots if this is prefix selection
        if (tagType == TagType.PREFIX) {
            addCustomTagSlots(player, menu);
        }
        
        return menu;
    }
    
    /**
     * Builds the tag requests menu.
     *
     * @param player The player to build the menu for
     * @param requests The list of tag requests
     * @return The created inventory
     */
    public Inventory buildRequestsMenu(Player player, List<CustomTagRequest> requests) {
        int rows = Math.max(1, (int) Math.ceil(requests.size() / 9.0));
        Inventory menu = Bukkit.createInventory(player, rows * 9, "Tag Requests");
        
        for (CustomTagRequest request : requests) {
            ItemStack playerHead = inventoryFactory.createPlayerHead(
                Bukkit.getOfflinePlayer(request.getPlayerUuid()),
                ChatColor.GREEN + request.getPlayerName(),
                Arrays.asList(
                    ChatColor.GRAY + "Requested Tag: " + ChatColor.translateAlternateColorCodes('&', request.getTagDisplay()),
                    "",
                    ChatColor.YELLOW + "Left-click to accept",
                    ChatColor.RED + "Right-click to deny"
                )
            );
            
            menu.addItem(playerHead);
        }
        
        return menu;
    }
    
    /**
     * Adds a player's head with tag information to a menu.
     *
     * @param player The player
     * @param menu The menu to add the head to
     * @param slot The slot to place the head
     */
    private void addPlayerHeadToMenu(Player player, Inventory menu, int slot) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return;
        
        String prefix = user.getCachedData().getMetaData().getPrefix();
        String suffix = user.getCachedData().getMetaData().getSuffix();
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Current Title(s)");
        
        if (prefix != null) {
            lore.add(ChatColor.GRAY + "Prefix: " + ChatColor.translateAlternateColorCodes('&', prefix));
        }
        
        if (suffix != null) {
            lore.add(ChatColor.GRAY + "Suffix: " + ChatColor.translateAlternateColorCodes('&', suffix));
        }
        
        ItemStack playerHead = inventoryFactory.createPlayerHead(player, ChatColor.YELLOW + player.getName(), lore);
        menu.setItem(slot, playerHead);
    }
    
    /**
     * Creates a menu item for a tag.
     *
     * @param tag The tag to create an item for
     * @return The created item
     */
    private ItemStack createTagItem(Tag tag) {
        ItemStack item = tag.getMaterial().clone();
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', tag.getDisplay()));
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Click to Select");
            lore.add(ChatColor.WHITE + "You may also use:");
            lore.add(ChatColor.YELLOW + "/tag set " + tag.getName());
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Adds navigation buttons to a menu.
     *
     * @param menu The menu to add buttons to
     * @param currentPage The current page number
     * @param totalPages The total number of pages
     */
    private void addNavigationButtons(Inventory menu, int currentPage, double totalPages) {
        // Add previous page button if not on first page
        if (currentPage > 0) {
            menu.setItem(45, inventoryFactory.createButton(
                Material.ARROW,
                ChatColor.AQUA + "Previous Page",
                null
            ));
        }
        
        // Add next page button if not on last page
        if (currentPage < totalPages - 1) {
            menu.setItem(53, inventoryFactory.createButton(
                Material.ARROW,
                ChatColor.AQUA + "Next Page",
                null
            ));
        }
    }
    
    /**
     * Adds custom tag slots to a menu.
     *
     * @param player The player
     * @param menu The menu to add slots to
     */
    private void addCustomTagSlots(Player player, Inventory menu) {
        for (int i = 0; i < 4; i++) {
            int slotNumber = i + 1;
            int menuSlot = i * 2 + 1; // Positions 1, 3, 5, 7
            
            String tagPermission = "utags.tag." + player.getName() + slotNumber;
            String slotPermission = "utags.custom" + slotNumber;
            
            if (player.hasPermission(tagPermission)) {
                // Player has this custom tag
                menu.setItem(menuSlot, createCustomTagItem(player, slotNumber));
            } else if (player.hasPermission(slotPermission)) {
                // Player can request this custom tag
                menu.setItem(menuSlot, inventoryFactory.createButton(
                    Material.GREEN_STAINED_GLASS_PANE,
                    ChatColor.YELLOW + "Request Custom Tag #" + slotNumber,
                    Arrays.asList(
                        ChatColor.WHITE + "You can request a custom tag using",
                        ChatColor.WHITE + "/tag request"
                    )
                ));
            } else {
                // Player doesn't have permission
                menu.setItem(menuSlot, inventoryFactory.createButton(
                    Material.BARRIER,
                    ChatColor.LIGHT_PURPLE + "Unlock Custom Tag #" + slotNumber,
                    Arrays.asList(
                        ChatColor.WHITE + "Become a premium member",
                        ChatColor.WHITE + "to unlock custom tags."
                    )
                ));
            }
        }
    }
    
    /**
     * Creates a menu item for a custom tag.
     *
     * @param player The player
     * @param slotNumber The custom tag slot number
     * @return The created item
     */
    private ItemStack createCustomTagItem(Player player, int slotNumber) {
        // This would typically get tag display from database
        // Using a placeholder for now
        String tagDisplay = "Custom Tag " + slotNumber;
        
        return inventoryFactory.createPlayerHead(
            player,
            ChatColor.GOLD + "Custom Tag #" + slotNumber + ": " + tagDisplay,
            Arrays.asList(
                ChatColor.YELLOW + "Click to Select",
                ChatColor.WHITE + "You may also use:",
                ChatColor.YELLOW + "/tag set " + player.getName() + slotNumber
            )
        );
    }
}
