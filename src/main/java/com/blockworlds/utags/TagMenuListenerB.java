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

import java.util.List;
import java.util.stream.Collectors;

/*public class TagMenuListenerB implements Listener {

    private uTags plugin;

    public TagMenuListenerB(uTags plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();
        ItemStack clickedItem = event.getCurrentItem();

        // Check if the clicked inventory is a uTags menu
        String inventoryTitle = event.getView().getTitle();
        if (!inventoryTitle.contains("uTags Menu") && !inventoryTitle.contains("Select Prefix") && !inventoryTitle.contains("Select Suffix")) return;

        // Prevent players from taking items from the uTags menu
        event.setCancelled(true);

        // Ensure the clicked item is not null or air
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // Handle interactions based on the inventory title
        if (inventoryTitle.contains("uTags Menu"))
        {
            handleTagMenuInteraction(event);
        } else if (inventoryTitle.contains("Select Prefix")){
            handleTagSelection(event, TagType.PREFIX, inventoryTitle.charAt(inventoryTitle.length() - 1));
        } else if (inventoryTitle.contains("Select Suffix")){
            handleTagSelection(event, TagType.SUFFIX, inventoryTitle.charAt(inventoryTitle.length() - 1));
        }
    }

    public void openTagSelection(Player player, int pageIndex, TagType selectionType) {
        List<Tag> tags = plugin.getAvailableTags(selectionType);
        List<Tag> availableTags = tags.stream()
                .filter(tag -> tag.getType() == selectionType || tag.getType() == TagType.BOTH)
                .collect(Collectors.toList());

        String inventoryTitle = selectionType == TagType.PREFIX ? "Select Prefix" : "Select Suffix";
        Inventory inventory = Bukkit.createInventory(player, 54, inventoryTitle + " " + pageIndex);

        int itemsPerPage = 45;
        int startIndex = pageIndex * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, availableTags.size());

        int slot = 10;
        for (Tag tag : availableTags) {

            // Check if the player has permission for the tag or if the tag is public
            if (tag.isPublic() || player.hasPermission("utags.tag." + tag.getName())) {
                ItemStack prefixItem = tag.getMaterial();
                ItemMeta prefixMeta = prefixItem.getItemMeta();

                // Apply the display attribute of the tag to the item
                prefixMeta.setDisplayName(tag.getDisplay().replace("&", "§"));
                prefixItem.setItemMeta(prefixMeta);

                // Add the prefix item to the inventory
                inventory.setItem(slot, prefixItem);

                // Increment the slot and skip slots
                slot++;
                if (slot % 9 == 7) {
                    slot += 3;
                }
            }
        }

        if (pageIndex > 0) {
            ItemStack prevPageItem = new ItemStack(Material.ARROW);
            ItemMeta prevPageMeta = prevPageItem.getItemMeta();
            prevPageMeta.setDisplayName(ChatColor.AQUA + "Previous Page");
            prevPageItem.setItemMeta(prevPageMeta);
            inventory.setItem(45, prevPageItem);
        }

        if (endIndex < availableTags.size()) {
            ItemStack nextPageItem = new ItemStack(Material.ARROW);
            ItemMeta nextPageMeta = nextPageItem.getItemMeta();
            nextPageMeta.setDisplayName(ChatColor.AQUA + "Next Page");
            nextPageItem.setItemMeta(nextPageMeta);
            inventory.setItem(53, nextPageItem);
        }

        player.openInventory(inventory);
    }

    private void handleTagMenuInteraction(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

        if ("Change Prefix".equals(itemName)) {
            openTagSelection(player, 1, TagType.PREFIX);
            return;
        }

        if ("Change Suffix".equals(itemName)) {
            openTagSelection(player, 1, TagType.SUFFIX);
            return;
        }

        // If you want to handle more interactions, add them here
    }

    private void handleTagSelection(InventoryClickEvent event, TagType tagType, int currentPage) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem != null && clickedItem.hasItemMeta()) {
            String itemName = clickedItem.getItemMeta().getDisplayName();

            if (itemName.equals("Previous Page")) {
                openTagSelection(player, currentPage - 1, tagType);
                return;
            } else if (itemName.equals("Next Page")) {
                openTagSelection(player, currentPage + 1, tagType);
                return;
            }
        }

        String tag = clickedItem.getItemMeta().getDisplayName();

        // Update the player's LuckPerms prefix
        setPlayerTag(player, tag, tagType);

        // Close the current inventory (Select Prefix) and inform the player
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Your " + tagType + " has been updated to: " + ChatColor.translateAlternateColorCodes('&', tag));
    }

    public void setPlayerTag(Player player, String tagName, TagType tagType) {
        User user = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            if (tagType == TagType.PREFIX)
            {
                user.data().clear(NodeType.PREFIX.predicate());
                user.data().add(PrefixNode.builder(tagName, 10000).build());
            }
            else {
                user.data().clear(NodeType.SUFFIX.predicate());
                user.data().add(SuffixNode.builder(tagName, 10000).build());
            }
            plugin.getLuckPerms().getUserManager().saveUser(user);
        }
    }

    public Inventory createInventoryFrame(int size, String title, Material frameMaterial) {
        Inventory inventory = Bukkit.createInventory(null, size, title);

        // Fill the frame with the specified material
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
}

    /*private void openPrefixSelection(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();

        // Create a new inventory for prefix selection
        int inventorySize = 54;
        Inventory prefixSelection = Bukkit.createInventory(player, inventorySize, "Select Prefix");

        // Fetch the actual list of prefixes from your database
        List<Tag> availablePrefixes = plugin.getAvailablePrefixes();

        // Add the available prefixes as items in the inventory
        int slot = 10;
        for (Tag tag : availablePrefixes) {

            // Check if the player has permission for the tag or if the tag is public
            if (tag.isPublic() || player.hasPermission("utags.tag." + tag.getName())) {
                ItemStack prefixItem = tag.getMaterial();
                ItemMeta prefixMeta = prefixItem.getItemMeta();

                // Apply the display attribute of the tag to the item
                prefixMeta.setDisplayName(tag.getDisplay().replace("&", "§"));
                prefixItem.setItemMeta(prefixMeta);

                // Add the prefix item to the inventory
                prefixSelection.setItem(slot, prefixItem);

                // Increment the slot and skip slots
                slot++;
                if (slot % 9 == 7) {
                    slot += 3;
                }
            }
        }

        // Close the current inventory (Tag Menu) and open the prefix selection inventory
        player.closeInventory();
        player.openInventory(prefixSelection);
    }*/

    /*private void handleSuffixSelection(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        String suffix = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

        // Update the player's LuckPerms suffix
        setPlayerSuffix(player, suffix);

        // Close the current inventory (Select Suffix) and inform the player
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Your suffix has been updated to: " + ChatColor.translateAlternateColorCodes('&', suffix));
    }*/

    /*public void setPlayerPrefix(Player player, String prefix) {
        User user = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            user.data().clear(NodeType.META.predicate(mn -> mn.getMetaKey().equalsIgnoreCase("prefix")));
            user.data().add(PrefixNode.builder(prefix, 9000).build());
            plugin.getLuckPerms().getUserManager().saveUser(user);
        }
    }*/


    /*public void openPrefixSelection(InventoryClickEvent event, int page) {
        Player player = (Player) event.getWhoClicked();
        List<Tag> availablePrefixes = plugin.getAvailablePrefixes();
        int tagsPerPage = 28;
        int totalPages = (int) Math.ceil((double) availablePrefixes.size() / tagsPerPage);

        // Create the inventory with a frame
        Inventory inventory = createInventoryFrame(54, "Select Prefix", Material.BLACK_STAINED_GLASS_PANE);

        // Display the available prefixes
        for (int i = 0; i < tagsPerPage; i++) {
            int index = i + (page * tagsPerPage);

            if (index >= availablePrefixes.size()) {
                break;
            }

            Tag tag = availablePrefixes.get(index);
            ItemStack item = new ItemStack(tag.getMaterial());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(tag.getDisplay());
            item.setItemMeta(meta);
            inventory.setItem(i + 9 + (i / 7), item);
        }

        // Add the previous and next page buttons
        ItemStack prevButton = new ItemStack(Material.ARROW);
        ItemMeta prevButtonMeta = prevButton.getItemMeta();
        prevButtonMeta.setDisplayName("Previous Page");
        prevButton.setItemMeta(prevButtonMeta);
        ItemStack nextButton = new ItemStack(Material.ARROW);
        ItemMeta nextButtonMeta = nextButton.getItemMeta();
        nextButtonMeta.setDisplayName("Next Page");
        nextButton.setItemMeta(nextButtonMeta);

        if (page > 0) {
            inventory.setItem(48, prevButton);
        }

        if (page < totalPages - 1) {
            inventory.setItem(50, nextButton);
        }

        player.openInventory(inventory);
    }*/

    /*private void openSuffixSelection(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();

        // Create a new inventory for suffix selection
        int inventorySize = 54;
        Inventory suffixSelection = Bukkit.createInventory(player, inventorySize, "Select Suffix");

        // Fetch the actual list of prefixes from your database
        List<Tag> availableSuffixes = plugin.getAvailableSuffixes();

        // Add the available prefixes as items in the inventory
        int slot = 10;
        for (Tag tag : availableSuffixes) {

            // Check if the player has permission for the tag or if the tag is public
            if (tag.isPublic() && player.hasPermission("utags.tag." + tag.getName())) {
                ItemStack suffixItem = tag.getMaterial();
                ItemMeta suffixMeta = suffixItem.getItemMeta();

                // Apply the display attribute of the tag to the item
                suffixMeta.setDisplayName(tag.getDisplay().replace("&", "§"));
                suffixItem.setItemMeta(suffixMeta);

                // Add the suffix item to the inventory
                suffixSelection.setItem(slot, suffixItem);

                // Increment the slot and skip slots
                slot++;
                if (slot % 9 == 7) {
                    slot += 3;
                }
            }
        }

        // Close the current inventory (Tag Menu) and open the suffix selection inventory
        player.closeInventory();
        player.openInventory(suffixSelection);
    }*/