package com.blockworlds.utags.utils;

import com.blockworlds.utags.Tag;
import com.blockworlds.utags.TagType;
import com.blockworlds.utags.uTags;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class for optimizing inventory operations in the uTags plugin.
 * Provides methods for efficient inventory creation, caching, and item pooling.
 */
public class InventoryOptimizer {

    private final uTags plugin;
    private final ErrorHandler errorHandler;
    
    // Cache for common inventory layouts
    private final Map<String, Inventory> inventoryTemplateCache;
    
    // Cache for common ItemStacks
    private final Map<String, ItemStack> itemCache;
    
    // Loaded tag pages cache
    private final Map<String, List<Tag>> tagPagesCache;
    
    // Configuration
    private final boolean useTemplates;
    private final boolean useItemPooling;
    private final int pageCacheMaxSize;
    
    // Frame item reference (reused across inventories)
    private ItemStack frameItem;

    /**
     * Creates a new InventoryOptimizer.
     *
     * @param plugin The uTags plugin instance
     * @param errorHandler The error handler to use
     * @param useTemplates Whether to use inventory templates
     * @param useItemPooling Whether to use item pooling
     * @param pageCacheMaxSize Maximum size of the page cache
     */
    public InventoryOptimizer(uTags plugin, ErrorHandler errorHandler, 
                             boolean useTemplates, boolean useItemPooling, int pageCacheMaxSize) {
        this.plugin = plugin;
        this.errorHandler = errorHandler;
        this.useTemplates = useTemplates;
        this.useItemPooling = useItemPooling;
        this.pageCacheMaxSize = pageCacheMaxSize;
        
        // Initialize caches
        this.inventoryTemplateCache = new ConcurrentHashMap<>();
        this.itemCache = new ConcurrentHashMap<>();
        this.tagPagesCache = new ConcurrentHashMap<>();
        
        // Initialize common items
        initializeCommonItems();
        
        plugin.getLogger().info("InventoryOptimizer initialized with " + 
                               (useTemplates ? "templates enabled" : "templates disabled") + " and " +
                               (useItemPooling ? "item pooling enabled" : "item pooling disabled"));
    }
    
    /**
     * Initializes commonly used items for pooling.
     */
    private void initializeCommonItems() {
        try {
            // Create frame item
            Material frameMaterial = Material.valueOf(
                plugin.getConfig().getString("frame-material", "BLACK_STAINED_GLASS_PANE")
            );
            frameItem = createBasicItem(frameMaterial, " ", null);
            
            // Cache navigation buttons
            cacheCommonItem("nav_prev", createNavigationButton(ChatColor.AQUA + "Previous Page"));
            cacheCommonItem("nav_next", createNavigationButton(ChatColor.AQUA + "Next Page"));
            cacheCommonItem("nav_back", createBasicItem(Material.BARRIER, ChatColor.RED + "Return to Main Menu", null));
            
            // Cache border items if using item pooling
            if (useItemPooling) {
                cacheCommonItem("border", frameItem.clone());
                
                // Cache other commonly used items here as needed
                cacheCommonItem("prefix_button", createBasicItem(
                    Material.NAME_TAG, ChatColor.GREEN + "Change Prefix", null
                ));
                cacheCommonItem("suffix_button", createBasicItem(
                    Material.NAME_TAG, ChatColor.YELLOW + "Change Suffix", null
                ));
            }
        } catch (Exception e) {
            errorHandler.logError("Error initializing common items", e);
        }
    }
    
    /**
     * Creates an optimized inventory with frame.
     *
     * @param size The inventory size
     * @param title The inventory title
     * @param player The player who will view the inventory
     * @return The created inventory
     */
    public Inventory createFramedInventory(int size, String title, Player player) {
        String cacheKey = "frame_" + size + "_" + title.hashCode();
        
        // Use template if available and enabled
        if (useTemplates && inventoryTemplateCache.containsKey(cacheKey)) {
            // Clone the template inventory
            return cloneInventory(inventoryTemplateCache.get(cacheKey), title, player);
        }
        
        // Create a new inventory
        Inventory inventory = Bukkit.createInventory(player, size, title);
        
        // Add frame using pooled items for better performance
        addFrameBorder(inventory, size);
        
        // Cache the template if enabled
        if (useTemplates) {
            inventoryTemplateCache.put(cacheKey, cloneInventory(inventory, title, null));
        }
        
        return inventory;
    }
    
    /**
     * Adds a frame border to an inventory.
     *
     * @param inventory The inventory to add the frame to
     * @param size The size of the inventory
     */
    public void addFrameBorder(Inventory inventory, int size) {
        ItemStack borderItem = useItemPooling ? 
            getPooledItem("border") : frameItem.clone();
        
        for (int i = 0; i < size; i++) {
            if (i < 9 || i > size - 10 || i % 9 == 0 || (i + 1) % 9 == 0) {
                inventory.setItem(i, borderItem);
            }
        }
    }
    
    /**
     * Creates a clone of an inventory.
     *
     * @param source The source inventory
     * @param title The title for the new inventory
     * @param player The player who will view the inventory
     * @return A clone of the source inventory
     */
    private Inventory cloneInventory(Inventory source, String title, Player player) {
        Inventory clone = Bukkit.createInventory(player, source.getSize(), title);
        
        for (int i = 0; i < source.getSize(); i++) {
            ItemStack item = source.getItem(i);
            if (item != null) {
                clone.setItem(i, item.clone());
            }
        }
        
        return clone;
    }
    
    /**
     * Gets or loads a page of tags for a specific type.
     *
     * @param tagType The type of tags to load
     * @param pageIndex The page index
     * @param pageSize The number of tags per page
     * @return A list of tags for the specified page
     */
    public List<Tag> getTagsPage(TagType tagType, int pageIndex, int pageSize) {
        String cacheKey = tagType + "_" + pageIndex + "_" + pageSize;
        
        // Check if page is already loaded
        if (tagPagesCache.containsKey(cacheKey)) {
            return tagPagesCache.get(cacheKey);
        }
        
        // Load all tags of the specified type
        List<Tag> allTags = plugin.getAvailableTags(tagType);
        if (allTags == null || allTags.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Calculate page bounds
        int startIndex = pageIndex * pageSize;
        int endIndex = Math.min(startIndex + pageSize, allTags.size());
        
        // If start index is beyond available tags, return empty page
        if (startIndex >= allTags.size()) {
            return Collections.emptyList();
        }
        
        // Extract the page of tags
        List<Tag> pageTags = new ArrayList<>(allTags.subList(startIndex, endIndex));
        
        // Cache the page if cache isn't full
        if (tagPagesCache.size() < pageCacheMaxSize) {
            tagPagesCache.put(cacheKey, pageTags);
        }
        
        return pageTags;
    }
    
    /**
     * Gets the total number of pages for tags of a specific type.
     *
     * @param tagType The type of tags
     * @param pageSize The number of tags per page
     * @return The total number of pages
     */
    public int getTotalPages(TagType tagType, int pageSize) {
        List<Tag> allTags = plugin.getAvailableTags(tagType);
        if (allTags == null || allTags.isEmpty()) {
            return 0;
        }
        
        return (int) Math.ceil((double) allTags.size() / pageSize);
    }
    
    /**
     * Creates a basic ItemStack with name and lore.
     *
     * @param material The material for the item
     * @param name The display name for the item
     * @param lore The lore for the item
     * @return The created ItemStack
     */
    public ItemStack createBasicItem(Material material, String name, List<String> lore) {
        // Use pooled item if possible and enabled
        String cacheKey = material.name() + "_" + (name != null ? name.hashCode() : 0);
        if (useItemPooling && lore == null && itemCache.containsKey(cacheKey)) {
            return itemCache.get(cacheKey).clone();
        }
        
        // Create new item
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (name != null) {
            meta.setDisplayName(name);
        }
        
        if (lore != null && !lore.isEmpty()) {
            meta.setLore(lore);
        }
        
        item.setItemMeta(meta);
        
        // Cache basic items without lore for reuse
        if (useItemPooling && lore == null) {
            itemCache.put(cacheKey, item.clone());
        }
        
        return item;
    }
    
    /**
     * Creates a player head item with optimized metadata handling.
     *
     * @param player The player whose head to create
     * @param name The display name for the item
     * @param lore The lore for the item
     * @return The created player head ItemStack
     */
    public ItemStack createPlayerHead(Player player, String name, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        
        // Set owner without unnecessary operations
        meta.setOwningPlayer(player);
        
        if (name != null) {
            meta.setDisplayName(name);
        }
        
        if (lore != null && !lore.isEmpty()) {
            meta.setLore(lore);
        }
        
        head.setItemMeta(meta);
        return head;
    }
    
    /**
     * Creates a navigation button.
     *
     * @param displayName The display name for the button
     * @return The created navigation button ItemStack
     */
    public ItemStack createNavigationButton(String displayName) {
        return createBasicItem(Material.ARROW, displayName, null);
    }
    
    /**
     * Gets a tag item from cache or creates it.
     *
     * @param tag The tag to create an item for
     * @param lore The lore for the item
     * @return The tag ItemStack
     */
    public ItemStack getTagItem(Tag tag, List<String> lore) {
        if (tag == null) {
            return null;
        }
        
        ItemStack item = tag.getMaterial().clone();
        ItemMeta meta = item.getItemMeta();
        
        // Apply the display attribute of the tag to the item
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', tag.getDisplay()));
        
        if (lore != null) {
            meta.setLore(lore);
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * Populates an inventory with tag items efficiently.
     *
     * @param inventory The inventory to populate
     * @param tags The tags to add
     * @param slots The slots to place the tags in
     * @param player The player viewing the inventory
     * @param loreProvider Function to create lore for each tag
     */
    public void populateTagInventory(Inventory inventory, List<Tag> tags, int[] slots, Player player, 
                                    Function<Tag, List<String>> loreProvider) {
        if (inventory == null || tags == null || slots == null || slots.length == 0) {
            return;
        }
        
        int slotIndex = 0;
        for (Tag tag : tags) {
            if (slotIndex >= slots.length) {
                break;
            }
            
            // Check if the player has permission for the tag
            if (tag.isPublic() && player.hasPermission("utags.tag." + tag.getName())) {
                List<String> lore = loreProvider != null ? loreProvider.apply(tag) : null;
                ItemStack tagItem = getTagItem(tag, lore);
                
                inventory.setItem(slots[slotIndex], tagItem);
                slotIndex++;
            }
        }
    }
    
    /**
     * Adds player information to an inventory.
     *
     * @param player The player to display information for
     * @param inventory The inventory to add the info to
     * @param slot The slot to place the info in
     */
    public void addPlayerInfoToInventory(Player player, Inventory inventory, int slot) {
        try {
            // Get player's current prefix and suffix
            String prefix = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId())
                .getCachedData().getMetaData().getPrefix();
            String suffix = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId())
                .getCachedData().getMetaData().getSuffix();
            
            // Create lore with tag info
            List<String> lore = generatePlayerTagLore(prefix, suffix);
            
            // Create and add the player head
            ItemStack playerHead = createPlayerHead(player, ChatColor.YELLOW + player.getName(), lore);
            inventory.setItem(slot, playerHead);
        } catch (Exception e) {
            errorHandler.logError("Error adding player info to inventory", e);
            
            // Fallback to simple player head
            ItemStack playerHead = createPlayerHead(player, ChatColor.YELLOW + player.getName(), null);
            inventory.setItem(slot, playerHead);
        }
    }
    
    /**
     * Generates lore for a player's head showing their current tags.
     *
     * @param prefix The player's current prefix
     * @param suffix The player's current suffix
     * @return A list of strings for the lore
     */
    private List<String> generatePlayerTagLore(String prefix, String suffix) {
        List<String> lore = new ArrayList<>();
        
        lore.add(ChatColor.GRAY + "Current Title(s)");
        
        if (prefix != null) {
            lore.add(ChatColor.GRAY + "Prefix: " + ChatColor.translateAlternateColorCodes('&', prefix));
        }
        
        if (suffix != null) {
            lore.add(ChatColor.GRAY + "Suffix: " + ChatColor.translateAlternateColorCodes('&', suffix));
        }
        
        return lore;
    }
    
    /**
     * Adds navigation buttons to an inventory for pagination.
     *
     * @param inventory The inventory to add buttons to
     * @param currentPage The current page index
     * @param totalPages The total number of pages
     * @param prevSlot The slot for the previous page button
     * @param nextSlot The slot for the next page button
     * @param backSlot The slot for the back button, or -1 for none
     */
    public void addNavigationButtons(Inventory inventory, int currentPage, int totalPages, 
                                   int prevSlot, int nextSlot, int backSlot) {
        // Add previous page button if not on first page
        if (currentPage > 0 && prevSlot >= 0) {
            ItemStack prevButton = useItemPooling ? 
                getPooledItem("nav_prev") : createNavigationButton(ChatColor.AQUA + "Previous Page");
            inventory.setItem(prevSlot, prevButton);
        }
        
        // Add next page button if not on last page
        if (currentPage < totalPages - 1 && nextSlot >= 0) {
            ItemStack nextButton = useItemPooling ? 
                getPooledItem("nav_next") : createNavigationButton(ChatColor.AQUA + "Next Page");
            inventory.setItem(nextSlot, nextButton);
        }
        
        // Add back button if requested
        if (backSlot >= 0) {
            ItemStack backButton = useItemPooling ? 
                getPooledItem("nav_back") : createBasicItem(Material.BARRIER, ChatColor.RED + "Return to Main Menu", null);
            inventory.setItem(backSlot, backButton);
        }
    }
    
    /**
     * Gets a pooled item by key.
     *
     * @param key The item cache key
     * @return The pooled item, or null if not found
     */
    private ItemStack getPooledItem(String key) {
        ItemStack item = itemCache.get(key);
        return item != null ? item.clone() : null;
    }
    
    /**
     * Caches a common item for reuse.
     *
     * @param key The cache key
     * @param item The item to cache
     */
    private void cacheCommonItem(String key, ItemStack item) {
        if (item != null) {
            itemCache.put(key, item);
        }
    }
    
    /**
     * Invalidates the tag pages cache.
     */
    public void invalidateTagPagesCache() {
        tagPagesCache.clear();
    }
    
    /**
     * Gets statistics about the optimizer.
     *
     * @return A string with optimizer statistics
     */
    public String getStatistics() {
        return String.format(
            "Inventory Optimizer Stats: Template Cache Size=%d, Item Cache Size=%d, Tag Pages Cache Size=%d",
            inventoryTemplateCache.size(), itemCache.size(), tagPagesCache.size()
        );
    }
    
    /**
     * Cleans up resources used by the optimizer.
     */
    public void shutdown() {
        inventoryTemplateCache.clear();
        itemCache.clear();
        tagPagesCache.clear();
    }
}
