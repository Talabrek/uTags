package com.blockworlds.utags;

import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TagMenuListener implements Listener {

    private final uTags plugin;

    public TagMenuListener(uTags plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
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

        if (event.getSlot() == -999) return; // Prevent clicking outside of the inventory

        event.setCancelled(true);

        if (event.getRawSlot() > 54 ) return; // Prevent clicking outside of the inventory

        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        handleMenuInteraction(event, inventoryTitle);
    }

    private boolean isUTagsMenu(String inventoryTitle) {
        return inventoryTitle.contains("uTags Menu")
                || inventoryTitle.contains("Select Prefix")
                || inventoryTitle.contains("Select Suffix");
    }

    private void handleMenuInteraction(InventoryClickEvent event, String inventoryTitle) {
        if (inventoryTitle.contains("uTags Menu")) {
            handleTagMenuInteraction(event);
        } else if (inventoryTitle.contains("Select Prefix")) {
            handleTagSelection(event, TagType.PREFIX, Character.getNumericValue(inventoryTitle.charAt(inventoryTitle.length() - 1)));
        } else if (inventoryTitle.contains("Select Suffix")) {
            handleTagSelection(event, TagType.SUFFIX, Character.getNumericValue(inventoryTitle.charAt(inventoryTitle.length() - 1)));
        }
    }
    public void openTagSelection(Player player, int pageIndex, TagType selectionType) {
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

            int slotIndex = 0;
            for (int i = 0; i < 4; i++) {
                ItemStack customTagItem = createCustomTagMenuItem(player, i);
                inventory.setItem(itemSlots[i], customTagItem);
            }
        }
        player.openInventory(inventory);
    }

    private void populateTagSelectionInventory(Player player, Inventory inventory, List<Tag> availableTags, int pageIndex) {
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
            if (tag.isPublic() && player.hasPermission("utags.tag." + tag.getName())) {
                ItemStack prefixItem = tag.getMaterial();
                ItemMeta prefixMeta = prefixItem.getItemMeta();
                List<String> lore;
                // Apply the display attribute of the tag to the item
                prefixMeta.setDisplayName(tag.getDisplay().replace("&", "§"));
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
        addExtraMenuItems(player, inventory, pageIndex, slotIndex, itemsPerPage);
    }

    private void addExtraMenuItems(Player player, Inventory inventory, int pageIndex, int numTags, int itemsPerPage) {
        addPlayerHead(player, inventory, 49);
        if (pageIndex > 0) {
            ItemStack prevPageItem = createNavigationArrow(ChatColor.AQUA + "Previous Page");
            inventory.setItem(45, prevPageItem);
        }

        if ((pageIndex + 1) * itemsPerPage < numTags) {
            ItemStack nextPageItem = createNavigationArrow(ChatColor.AQUA + "Next Page");
            inventory.setItem(53, nextPageItem);
        }
    }

    public void addPlayerHead(Player player, Inventory inventory, int location)
    {
        // Add player's head to given location
        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta playerHeadMeta = (SkullMeta) playerHead.getItemMeta();
        playerHeadMeta.setOwningPlayer(player);
        playerHeadMeta.setDisplayName(ChatColor.YELLOW + player.getName());

        String prefix = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId()).getCachedData().getMetaData().getPrefix();
        String suffix = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId()).getCachedData().getMetaData().getSuffix();

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
    }

    private ItemStack createNavigationArrow(String displayName) {
        ItemStack arrowItem = new ItemStack(Material.ARROW);
        ItemMeta arrowMeta = arrowItem.getItemMeta();
        arrowMeta.setDisplayName(displayName);
        arrowItem.setItemMeta(arrowMeta);
        return arrowItem;
    }

    private void handleTagMenuInteraction(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

        if ("Change Prefix".equals(itemName)) {
            openTagSelection(player, 0, TagType.PREFIX);
        } else if ("Change Suffix".equals(itemName)) {
            openTagSelection(player, 0, TagType.SUFFIX);
        }
    }

    private void handleTagSelection(InventoryClickEvent event, TagType tagType, int currentPage) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem != null && clickedItem.hasItemMeta()) {
            String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

            if (itemName.equals("Previous Page")) {
                openTagSelection(player, currentPage - 1, tagType);
                return;
            } else if (itemName.equals("Next Page")) {
                openTagSelection(player, currentPage + 1, tagType);
                return;
            }
        }
        String tag;
        int slotIndex = 0;
        if ((event.getRawSlot() == 1 || event.getRawSlot() == 3 || event.getRawSlot() == 5 || event.getRawSlot() == 7) && tagType == TagType.PREFIX) {
            slotIndex = (event.getSlot() + 1) / 2;
            tag = player.getName() + slotIndex;
        }else
            tag = clickedItem.getItemMeta().getDisplayName();

        // Check if the tag is in the list of available tags and if the player has permission
        boolean hasPermission = player.hasPermission("utags.tag." + plugin.getTagNameByDisplay(tag)) || player.hasPermission("utags.tag." + tag);
        boolean isAvailableTag = plugin.getAvailableTags(tagType).stream().anyMatch(availableTag -> availableTag.getDisplay().equals(tag)) || plugin.getAvailableTags(tagType).stream().anyMatch(availableTag -> availableTag.getName().equals(tag));

        if (!hasPermission || !isAvailableTag) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this tag.");
            return;
        }

        // Update the player's LuckPerms prefix
        if ((event.getRawSlot() == 1 || event.getRawSlot() == 3 || event.getRawSlot() == 5 || event.getRawSlot() == 7) && tagType == TagType.PREFIX)
            plugin.setPlayerTag(player, plugin.getTagDisplayByName(tag), tagType);
        else
            plugin.setPlayerTag(player, tag, tagType);

        // Close the current inventory (Select Prefix) and inform the player
        player.closeInventory();
        if ((event.getRawSlot() == 1 || event.getRawSlot() == 3 || event.getRawSlot() == 5 || event.getRawSlot() == 7) && tagType == TagType.PREFIX)
            player.sendMessage(ChatColor.GREEN + "Your " + tagType + " has been updated to: " + ChatColor.translateAlternateColorCodes('&', plugin.getTagDisplayByName(tag)));
        else
            player.sendMessage(ChatColor.GREEN + "Your " + tagType + " has been updated to: " + ChatColor.translateAlternateColorCodes('&', tag));
    }

    public Inventory createInventoryFrame(int size, String title, Material frameMaterial, Player player) {
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
    }

    private ItemStack createCustomTagMenuItem(Player player, int slotIndex) {
        String permissionBase = "utags.custom";
        String permissionTag = "utags.tag." + player.getName();
        ItemStack item;
        ItemMeta meta;

        if (player.hasPermission(permissionTag + (slotIndex + 1))) {
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
            meta.setLore(Arrays.asList(ChatColor.YELLOW + "Click to Select", ChatColor.WHITE + "You may also use:", ChatColor.YELLOW + "/tag set " + (player.getName() + (slotIndex + 1))));
        } else if (player.hasPermission(permissionBase + (slotIndex+ 1))) {
            item = new ItemStack(Material.GREEN_STAINED_GLASS_PANE, 1);
            meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + "Request Custom Tag #" + (slotIndex+ 1));
            meta.setLore(Arrays.asList(ChatColor.WHITE + "You can request a custom tag using", ChatColor.WHITE + "/tag request"));
        } else {
            item = new ItemStack(Material.BARRIER, 1);
            meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Unlock Custom Tag #" + (slotIndex+ 1));
            meta.setLore(Arrays.asList(ChatColor.WHITE + "Become a premium member", ChatColor.WHITE + "to unlock custom tags."));
        }

        item.setItemMeta(meta);
        return item;
    }
}