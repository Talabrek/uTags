package com.blockworlds.utags;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class TagColorMenuListener implements Listener {

    private final uTags plugin;
    private final TagColorMenuManager colorMenuManager; // Keep a reference

    public TagColorMenuListener(uTags plugin, TagColorMenuManager colorMenuManager) {
        this.plugin = plugin;
        this.colorMenuManager = colorMenuManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Basic checks
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        ItemStack clickedItem = event.getCurrentItem();
        String inventoryTitle = event.getView().getTitle();

        // Check if it's the color selection menu
        if (clickedInventory != null && inventoryTitle.startsWith(TagColorMenuManager.COLOR_MENU_TITLE)) {
            event.setCancelled(true); // Prevent taking items

            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return; // Ignore clicks on empty slots
            }

            // Extract tag name from title
            String tagNamePart = inventoryTitle.substring(TagColorMenuManager.COLOR_MENU_TITLE.length());
            String tagName = null;
            if (tagNamePart.contains(" - ")) {
                 tagName = ChatColor.stripColor(tagNamePart.substring(tagNamePart.indexOf(" - ") + 3));
            }

            if (tagName == null) {
                player.sendMessage(ChatColor.RED + "Error identifying the tag being edited.");
                player.closeInventory();
                return;
            }

            // Validate Tag and Permissions
            Tag tag = plugin.getTagByName(tagName);
             if (tag == null) {
                 player.sendMessage(ChatColor.RED + "Could not find the tag: " + tagName);
                 player.closeInventory();
                 return;
             }
             if (!tag.isColor()) {
                 player.sendMessage(ChatColor.RED + "This tag does not support color customization.");
                 player.closeInventory();
                 return;
             }
             if (!player.hasPermission("utags.color")) {
                 player.sendMessage(ChatColor.RED + "You do not have permission to change tag colors.");
                 player.closeInventory();
                 return;
             }

            ItemMeta meta = clickedItem.getItemMeta();
            if (meta == null) return;
            String displayName = meta.getDisplayName();

            // --- Handle clicks on control items using else if ---

            // Mode Switch Click
            if (clickedItem.getType() == Material.INK_SAC || clickedItem.getType() == Material.BONE_MEAL) {
                boolean currentlyEditingBrackets = clickedItem.getType() == Material.INK_SAC;
                colorMenuManager.openColorSelectionMenu(player, tag, !currentlyEditingBrackets);
                return;
            }
            // Reset Click
            else if (clickedItem.getType() == Material.BARRIER && displayName.equals(TagColorMenuManager.RESET_ITEM_NAME)) {
                plugin.resetPlayerTagColor(player.getUniqueId(), tagName);
                player.sendMessage(ChatColor.GREEN + "Colors for tag '" + tagName + "' reset to default.");
                colorMenuManager.openColorSelectionMenu(player, tag, true); // Re-open menu
                return;
            }
            // Back Button Click
            else if (clickedItem.getType() == Material.ARROW && displayName.equals(TagColorMenuManager.BACK_BUTTON_NAME)) {
                plugin.getTagMenuManager().openTagSelection(player, 0, TagType.PREFIX); // Return to prefix menu
                return;
            }
            // Apply Button(s) Click
            else if (clickedItem.getType() == Material.LIME_WOOL || clickedItem.getType() == Material.YELLOW_WOOL) { // Check for both potential button materials
                PlayerTagColorPreference preference = plugin.getPlayerTagColorPreference(player.getUniqueId(), tagName);
                String finalDisplay = plugin.formatTagDisplayWithColor(tag.getDisplay(), preference);
                TagType applyType; // Determine which type to apply

                if (displayName.equals(TagColorMenuManager.APPLY_PREFIX_BUTTON_NAME)) {
                    applyType = TagType.PREFIX;
                } else if (displayName.equals(TagColorMenuManager.APPLY_SUFFIX_BUTTON_NAME)) {
                    applyType = TagType.SUFFIX;
                } else if (displayName.equals(TagColorMenuManager.ACCEPT_BUTTON_NAME)) {
                    // For non-BOTH tags, use the tag's original type
                    applyType = tag.getType();
                } else {
                    return; // Clicked on wool, but not a recognized apply button
                }

                // Pass the INTERNAL tag name, not the formatted display string.
                // setPlayerTag will handle formatting internally.
                plugin.setPlayerTag(player, tagName, applyType);
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "Tag " + ChatColor.translateAlternateColorCodes('&', finalDisplay) + ChatColor.GREEN + " applied as " + applyType.name().toLowerCase() + " with custom colors!");
                return;
            }

            // --- Handle clicks on color panes (Only if no control button was clicked) ---
            ChatColor selectedColor = TagColorMenuManager.getChatColorFromItem(clickedItem);
            if (selectedColor != null) {
                // Determine which part is being edited
                ItemStack modeSwitchItem = clickedInventory.getItem(clickedInventory.getSize() - 2);
                boolean editingBrackets = modeSwitchItem != null && modeSwitchItem.getType() == Material.INK_SAC;

                PlayerTagColorPreference currentPref = plugin.getPlayerTagColorPreference(player.getUniqueId(), tagName);
                ChatColor newBracketColor = currentPref.getBracketColor();
                ChatColor newContentColor = currentPref.getContentColor();

                if (editingBrackets) {
                    newBracketColor = selectedColor;
                    player.sendMessage(ChatColor.GREEN + "Set bracket color to " + selectedColor + selectedColor.name());
                } else {
                    newContentColor = selectedColor;
                    player.sendMessage(ChatColor.GREEN + "Set content color to " + selectedColor + selectedColor.name());
                }

                plugin.setPlayerTagColor(player.getUniqueId(), tagName, newBracketColor, newContentColor);

                // Re-open the menu to reflect the change and show preview
                colorMenuManager.openColorSelectionMenu(player, tag, editingBrackets);
            }
            // Ignore clicks on other items like the preview item
        }
    }
} // End of class TagColorMenuListener