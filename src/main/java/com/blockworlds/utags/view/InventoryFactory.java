package com.blockworlds.utags.view;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating inventory-related components.
 * Provides methods for creating common inventory items and layouts.
 */
public class InventoryFactory {
    
    // Cache commonly used items
    private final Map<String, ItemStack> itemCache = new ConcurrentHashMap<>();
    
    /**
     * Creates a framed inventory with borders.
     *
     * @param size The size of the inventory (must be a multiple of 9)
     * @param title The title of the inventory
     * @param frameMaterial The material to use for the frame
     * @param player The player who will view the inventory
     * @return The created inventory
     */
    public Inventory createFramedInventory(int size, String title, Material frameMaterial, Player player) {
        Inventory inventory = Bukkit.createInventory(player, size, title);
        
        // Create frame item
        ItemStack frameItem = createSimpleItem(frameMaterial, " ");
        
        // Add frame to inventory (border only)
        for (int i = 0; i < size; i++) {
            if (i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) {
                inventory.setItem(i, frameItem);
            }
        }
        
        return inventory;
    }
    
    /**
     * Creates a button with a custom name and lore.
     *
     * @param material The material for the button
     * @param name The name for the button
     * @param lore The lore for the button
     * @return The created button
     */
    public ItemStack createButton(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Creates a simple item with just a name.
     *
     * @param material The material for the item
     * @param name The name for the item
     * @return The created item
     */
    public ItemStack createSimpleItem(Material material, String name) {
        // Check cache first
        String cacheKey = material.name() + ":" + name;
        ItemStack cachedItem = itemCache.get(cacheKey);
        
        if (cachedItem != null) {
            return cachedItem.clone();
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        
        // Cache the item
        itemCache.put(cacheKey, item.clone());
        
        return item;
    }
    
    /**
     * Creates a player head item.
     *
     * @param player The player whose head to create
     * @param name The name for the head
     * @param lore The lore for the head
     * @return The created player head
     */
    public ItemStack createPlayerHead(OfflinePlayer player, String name, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName(name);
            
            if (lore != null) {
                meta.setLore(lore);
            }
            
            head.setItemMeta(meta);
        }
        
        return head;
    }
    
    /**
     * Creates a colored item with the specified material and color.
     *
     * @param material The material for the item
     * @param name The name for the item
     * @param color The color code (without &)
     * @return The created colored item
     */
    public ItemStack createColoredItem(Material material, String name, char color) {
        return createSimpleItem(material, ChatColor.getByChar(color) + name);
    }
    
    /**
     * Clears the item cache.
     */
    public void clearCache() {
        itemCache.clear();
    }
}
