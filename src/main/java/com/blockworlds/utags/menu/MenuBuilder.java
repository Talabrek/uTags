package com.blockworlds.utags.menu;

import com.blockworlds.utags.model.CustomTagRequest;
import com.blockworlds.utags.model.Tag;
import com.blockworlds.utags.model.TagType;
import com.blockworlds.utags.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MenuBuilder {
    private final JavaPlugin plugin;

    public MenuBuilder(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Inventory createMainMenu(Player player, String prefix, String suffix) {
        Inventory menu = Bukkit.createInventory(player, 9, "uTags Menu");

        // Change prefix button
        ItemStack prefixItem = Utils.createSimpleItem(Material.NAME_TAG, ChatColor.GREEN + "Change Prefix", null);
        menu.setItem(2, prefixItem);

        // Change suffix button
        ItemStack suffixItem = Utils.createSimpleItem(Material.NAME_TAG, ChatColor.YELLOW + "Change Suffix", null);
        menu.setItem(6, suffixItem);

        // Player head with current tags
        ItemStack head = createPlayerHeadWithTags(player, prefix, suffix);
        menu.setItem(4, head);

        return menu;
    }

    public Inventory createTagSelectionMenu(Player player, TagType tagType, List<Tag> availableTags, int page) {
        String title = (tagType == TagType.PREFIX ? "Select Prefix" : "Select Suffix") + " " + page;
        Material frameMaterial = Material.valueOf(plugin.getConfig().getString("frame-material", "BLACK_STAINED_GLASS_PANE"));
        Inventory menu = Utils.createInventoryFrame(54, title, frameMaterial, player);

        // Calculate pagination
        int itemsPerPage = 28;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, availableTags.size());

        // Item slots layout
        int[] slots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        // Populate tags
        int slotIndex = 0;
        for (int i = startIndex; i < endIndex; i++) {
            Tag tag = availableTags.get(i);
            if (tag.isPublic() && Utils.hasTagPermission(player, tag.getName()) && slotIndex < slots.length) {
                menu.setItem(slots[slotIndex], createTagItem(tag));
                slotIndex++;
            }
        }

        // Add player head
        ItemStack head = createPlayerHeadWithTags(player, null, null);
        menu.setItem(49, head);

        // Add navigation buttons
        if (page > 0) {
            menu.setItem(45, Utils.createNavigationButton(ChatColor.AQUA + "Previous Page"));
        }

        if ((page + 1) * itemsPerPage < availableTags.size()) {
            menu.setItem(53, Utils.createNavigationButton(ChatColor.AQUA + "Next Page"));
        }

        // Add custom tag slots
        if (tagType == TagType.PREFIX) {
            menu.setItem(1, createCustomTagSlot(player, 1));
            menu.setItem(3, createCustomTagSlot(player, 2));
            menu.setItem(5, createCustomTagSlot(player, 3));
            menu.setItem(7, createCustomTagSlot(player, 4));
        }

        return menu;
    }

    public Inventory createRequestsMenu(Player player, List<CustomTagRequest> requests) {
        int rows = Math.max(1, (int) Math.ceil(requests.size() / 9.0));
        Inventory menu = Bukkit.createInventory(player, rows * 9, "Custom Tag Requests");

        for (CustomTagRequest request : requests) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();

            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(request.getPlayerUuid()));
                meta.setDisplayName(ChatColor.GREEN + request.getPlayerName());
                
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Requested Tag: " + ChatColor.translateAlternateColorCodes('&', request.getTagDisplay()));
                lore.add("");
                lore.add(ChatColor.YELLOW + "Left-click to accept");
                lore.add(ChatColor.RED + "Right-click to deny");
                meta.setLore(lore);
                
                head.setItemMeta(meta);
            }

            menu.addItem(head);
        }

        return menu;
    }

    private ItemStack createTagItem(Tag tag) {
        ItemStack item = tag.getMaterial().clone();
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();

        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', tag.getDisplay()));
        
        lore.add(ChatColor.YELLOW + "Click to Select");
        lore.add(ChatColor.WHITE + "You may also use:");
        lore.add(ChatColor.YELLOW + "/tag set " + tag.getName());
        
        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createPlayerHeadWithTags(Player player, String prefix, String suffix) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.setDisplayName(ChatColor.YELLOW + player.getName());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Current Title(s)");
        
        if (prefix != null) {
            lore.add(ChatColor.GRAY + "Prefix: " + ChatColor.translateAlternateColorCodes('&', prefix));
        }
        
        if (suffix != null) {
            lore.add(ChatColor.GRAY + "Suffix: " + ChatColor.translateAlternateColorCodes('&', suffix));
        }
        
        meta.setLore(lore);
        head.setItemMeta(meta);
        
        return head;
    }

    private ItemStack createCustomTagSlot(Player player, int slot) {
        String permBase = "utags.custom";
        String permTag = "utags.tag." + player.getName() + slot;

        if (player.hasPermission(permTag)) {
            // Player has this custom tag
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(player);
            
            // Try to get the tag display if it exists
            String tagDisplay = "Not Set";
            // This would typically call a method to get the tag display
            
            meta.setDisplayName(ChatColor.GOLD + "Custom Tag #" + slot + ": " + tagDisplay);
            meta.setLore(Arrays.asList(
                ChatColor.YELLOW + "Click to Select",
                ChatColor.WHITE + "You may also use:",
                ChatColor.YELLOW + "/tag set " + player.getName() + slot
            ));
            
            head.setItemMeta(meta);
            return head;
        } else if (player.hasPermission(permBase + slot)) {
            // Player can request this tag
            return Utils.createSimpleItem(
                Material.GREEN_STAINED_GLASS_PANE,
                ChatColor.YELLOW + "Request Custom Tag #" + slot,
                Arrays.asList(
                    ChatColor.WHITE + "You can request a custom tag using",
                    ChatColor.WHITE + "/tag request"
                )
            );
        } else {
            // Player doesn't have access
            return Utils.createSimpleItem(
                Material.BARRIER,
                ChatColor.LIGHT_PURPLE + "Unlock Custom Tag #" + slot,
                Arrays.asList(
                    ChatColor.WHITE + "Become a premium member",
                    ChatColor.WHITE + "to unlock custom tags."
                )
            );
        }
    }
}
