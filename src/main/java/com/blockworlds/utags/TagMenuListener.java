package com.blockworlds.utags;

import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;


import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class TagMenuListener implements Listener {

    private final uTags plugin;
    // TODO: Uncomment and use PDC when ready
    // private final NamespacedKey tagInternalNameKey;

    public TagMenuListener(uTags plugin) {
        this.plugin = plugin;
        // this.tagInternalNameKey = new NamespacedKey(plugin, "utag_internal_name");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        String inventoryTitle = event.getView().getTitle();

        // Basic checks
        if (!isUTagsMenu(inventoryTitle)) {
            return;
        }
        // Check raw slot against actual inventory size, prevent clicks outside
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) {
             return;
        }

        event.setCancelled(true); // Cancel all clicks in uTags menus

        if (clickedItem == null || clickedItem.getType() == Material.AIR || !clickedItem.hasItemMeta()) {
            return; // Ignore clicks on empty slots or items without meta
        }

        handleMenuInteraction(event, inventoryTitle);
    }

    private boolean isUTagsMenu(String inventoryTitle) {
        // Check if the title starts with the known prefixes
        // Check if the title starts with the known prefixes or is the confirmation menu
        return inventoryTitle.startsWith("uTags Menu")
                || inventoryTitle.startsWith("Select Prefix")
                || inventoryTitle.startsWith("Select Suffix")
                || inventoryTitle.equals("Confirm Tag Request"); // Add confirmation menu title
    }

    private void handleMenuInteraction(InventoryClickEvent event, String inventoryTitle) {
        if (inventoryTitle.startsWith("uTags Menu")) {
            handleOldTagMenuInteraction(event);
        } else if (inventoryTitle.startsWith("Select Prefix")) {
            handleTagSelection(event, TagType.PREFIX, parsePageIndex(inventoryTitle));
        } else if (inventoryTitle.startsWith("Select Suffix")) {
            handleTagSelection(event, TagType.SUFFIX, parsePageIndex(inventoryTitle));
        } else if (inventoryTitle.equals("Confirm Tag Request")) {
            handleRequestConfirmation(event); // Handle clicks in the confirmation menu
        }
    }

    // Extracts page index (0-based) from title like "Select Prefix (Page 1)"
    private int parsePageIndex(String title) {
        try {
            // Look for the pattern " (Page N)"
            String pageMarker = "(Page ";
            int pageStartIndex = title.lastIndexOf(pageMarker);
            if (pageStartIndex != -1) {
                // Find the closing parenthesis after the marker
                int pageEndIndex = title.indexOf(')', pageStartIndex + pageMarker.length());
                if (pageEndIndex != -1) {
                    // Extract the number string between "(Page " and ")"
                    String pageNumStr = title.substring(pageStartIndex + pageMarker.length(), pageEndIndex).trim();
                    return Integer.parseInt(pageNumStr) - 1; // Return 0-based index
                }
            }
            // If the standard format isn't found, log a warning and default to 0
            // Removed the unreliable legacy parsing attempt. Titles should consistently use "(Page N)".
            plugin.getLogger().warning("Could not find standard page format '(Page N)' in title: '" + title + "'. Defaulting to page 0.");
            return 0;

        } catch (NumberFormatException e) {
            plugin.getLogger().severe("Failed to parse page number from title: '" + title + "'. Found non-numeric content. Error: " + e.getMessage());
            return 0; // Default to page 0 on parsing error
        } catch (Exception e) {
            plugin.getLogger().severe("Unexpected error parsing page index from title: '" + title + "'. Error: " + e.getMessage());
            return 0; // Default to page 0 on other errors
        }
    }


    // Handles clicks in the OLD main menu (Change Prefix/Suffix) - To be removed later
    private void handleOldTagMenuInteraction(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return; // Should be caught earlier, but safety first
        String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

        if ("Change Prefix".equals(itemName)) {
            // Use the manager to open the selection menu
            plugin.getTagMenuManager().openTagSelection(player, 0, TagType.PREFIX);
        } else if ("Change Suffix".equals(itemName)) {
            // Use the manager to open the selection menu
            plugin.getTagMenuManager().openTagSelection(player, 0, TagType.SUFFIX);
        }
    }

    // Handles clicks within the Prefix/Suffix selection menus
    private void handleTagSelection(InventoryClickEvent event, TagType tagType, int currentPage) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        // Already checked for null/air/no-meta in onInventoryClick

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return; // Extra safety check

        // --- Check for Toggle Button FIRST ---
        // Check if the clicked item is the visibility toggle button using its lore identifier.
        if (meta.hasLore()) {
            // String identifier = ChatColor.DARK_GRAY + "utag_toggle_visibility"; // Use the exact string from TagMenuManager - Not needed for comparison
            String rawIdentifier = "utag_toggle_visibility"; // For comparison after stripping color codes

            for (String loreLine : meta.getLore()) {
                // Compare the stripped lore line with the raw identifier
                if (ChatColor.stripColor(loreLine).equals(rawIdentifier)) {
                    plugin.getLogger().info("[uTags Debug] Visibility toggle button clicked. Handling toggle...");
                    // event.setCancelled(true); // Already cancelled globally for the menu at line 58
                    plugin.toggleShowAllPublicTagsPreference(player.getUniqueId());
                    plugin.getTagMenuManager().openTagSelection(player, currentPage, tagType); // Reopen/refresh menu
                    return; // IMPORTANT: Stop further processing for this click
                }
            }
        }
        // --- If NOT the toggle button, proceed with other checks ---
        // Note: The 'else' block was removed as the 'return' above handles the logic flow.

            String itemName = ChatColor.stripColor(meta.getDisplayName());

            // --- Navigation ---
            if (itemName.equals("Previous Page")) {
            plugin.getTagMenuManager().openTagSelection(player, currentPage - 1, tagType);
            return;
        } else if (itemName.equals("Next Page")) {
            plugin.getTagMenuManager().openTagSelection(player, currentPage + 1, tagType);
            return;
        } else if (itemName.startsWith("Switch to")) {
            TagType otherType = (tagType == TagType.PREFIX) ? TagType.SUFFIX : TagType.PREFIX;
            plugin.getTagMenuManager().openTagSelection(player, 0, otherType);
            return;
        } else if (itemName.startsWith("Remove Current")) {
            removePlayerTag(player, tagType); // removePlayerTag already handles async save
            player.closeInventory(); // Close inventory after removing
            return;
        } else if (itemName.equals("Change Name Color")) { // Handle the new button click
            plugin.getNameColorMenuManager().openNameColorMenu(player); // Open the name color menu
            return; // Exit after handling
        }

        // --- Explicit check for Locked Public Tags (Barrier) ---
        if (clickedItem.getType() == Material.BARRIER && !itemName.startsWith("Locked Custom Tag")) { // Avoid conflict with custom tag barrier
             player.sendMessage(ChatColor.RED + "This tag is locked. You need the required permission to use it.");
             // player.closeInventory(); // Optional: close inventory on locked click? Keep it open for now.
             return; // Stop processing for locked public tags
        }

        // --- Custom Tag Slot Interaction ---
        if (clickedItem.getType() == Material.WRITABLE_BOOK && itemName.startsWith("Request Custom Tag")) {
            int slotIndex = -1;
            try {
                slotIndex = Integer.parseInt(itemName.replaceAll("[^0-9]", "")) - 1;
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Could not parse slot index from 'Request Custom Tag' item: " + itemName);
            }
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "To request your custom tag for slot #" + (slotIndex != -1 ? slotIndex + 1 : "?") + ", use:");
            player.sendMessage(ChatColor.WHITE + "/tag request <YourDesiredTagDisplay>");
            player.sendMessage(ChatColor.GRAY + "(Example: /tag request &d[MyTag])");
            return;
        } else if (clickedItem.getType() == Material.BARRIER && itemName.startsWith("Locked Custom Tag")) {
            player.sendMessage(ChatColor.RED + "This custom tag slot is locked. Rank up or visit the store to unlock it.");
            player.closeInventory();
            return;
        }

        // --- Regular Tag or Unlocked Custom Tag Selection ---
        String internalTagName = null;

        // TODO: Prioritize PersistentDataContainer once implemented
        // internalTagName = meta.getPersistentDataContainer().get(tagInternalNameKey, PersistentDataType.STRING);

        // Fallback 1: Check lore
        if (internalTagName == null && meta.hasLore()) {
            for (String loreLine : meta.getLore()) {
                String strippedLore = ChatColor.stripColor(loreLine);
                if (strippedLore.startsWith("ID: ")) {
                    internalTagName = strippedLore.substring("ID: ".length());
                    break;
                }
            }
        }

        // Fallback 2: Player head custom tag
        if (internalTagName == null && clickedItem.getType() == Material.PLAYER_HEAD && itemName.startsWith("Custom Tag #")) {
            try {
                int slotIndex = Integer.parseInt(itemName.replaceAll("[^0-9]", "")) - 1;
                internalTagName = player.getName() + (slotIndex + 1);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Could not parse slot index from custom tag item: " + itemName);
            }
        }

        // If name found synchronously, process it. Otherwise, try async fallback.
        if (internalTagName != null) {
                        // --- Check for Right-Click Color Customization ---
            Tag clickedTag = plugin.getTagByName(internalTagName);
            boolean isColorable = clickedTag != null && clickedTag.isColor() && player.hasPermission("utags.color");

            if (event.isRightClick() && isColorable) {
                plugin.getTagColorMenuManager().openColorSelectionMenu(player, clickedTag, true); // Start with bracket editing
                return; // Don't apply tag on right-click
            } else {
                // Left-click or not colorable: Proceed to apply tag
                processTagSelection(player, internalTagName, tagType, clickedItem, meta, itemName);
            }
        } else {
            // Fallback 3: Try to look up internal name by display name asynchronously
            plugin.getLogger().warning("Attempting fallback: Looking up tag by display name: " + meta.getDisplayName());
            String displayToLookup = ChatColor.translateAlternateColorCodes('&', meta.getDisplayName());

            plugin.getTagNameByDisplayAsync(displayToLookup).thenAcceptAsync(resolvedName -> {
                // This block runs after the async DB query completes

                if (resolvedName == null) {
                    // Handle case where lookup failed - run on main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(ChatColor.RED + "Error: Could not identify the selected tag (fallback failed).");
                        plugin.getLogger().severe("Failed to determine internal name via fallback for clicked item: " + itemName + " (Material: " + clickedItem.getType() + ")");
                    });
                    return; // Stop processing for this future
                }
                // Name resolved, continue processing - pass to the common handler method
                // Run the rest of the logic on the main thread as it involves Bukkit API calls
                 // Run the rest of the logic on the main thread as it involves Bukkit API calls
                 Bukkit.getScheduler().runTask(plugin, () -> {
                    // Re-check for right-click color customization *after* async lookup
                    Tag resolvedTag = plugin.getTagByName(resolvedName);
                    boolean isColorable = resolvedTag != null && resolvedTag.isColor() && player.hasPermission("utags.color");

                    if (event.isRightClick() && isColorable) {
                        plugin.getTagColorMenuManager().openColorSelectionMenu(player, resolvedTag, true); // Start with bracket editing
                        // Don't process tag selection on right-click
                    } else {
                        // Left-click or not colorable: Proceed to apply tag
                        processTagSelection(player, resolvedName, tagType, clickedItem, meta, itemName);
                    }
                 });
            }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable)); // Ensure Bukkit tasks run on main thread if needed within the lambda
        }
    }

    /**
     * Helper method to handle the logic after the internal tag name has been determined
     * (either synchronously or asynchronously). This part involves permission checks,
     * fetching the display name asynchronously, and applying the tag.
     */
    private void processTagSelection(Player player, String internalTagName, TagType tagType, ItemStack clickedItem, ItemMeta meta, String itemName) {
        // Final check (safety)
        if (internalTagName == null) {
             player.sendMessage(ChatColor.RED + "Error: Could not identify the selected tag.");
             plugin.getLogger().severe("Failed to determine internal name for clicked item: " + itemName + " (Material: " + clickedItem.getType() + ")");
             return;
        }

        // Verify permission (synchronous check is okay here)
        if (!player.hasPermission("utags.tag." + internalTagName)) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this tag (" + internalTagName + ").");
            return;
        }

        // Get the correct display format asynchronously
        plugin.getTagDisplayByNameAsync(internalTagName).thenAcceptAsync(correctDisplay -> {
            // This block runs asynchronously after the DB query

            // Ensure subsequent Bukkit API calls run on the main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (correctDisplay == null) {
                     player.sendMessage(ChatColor.RED + "Error: Tag data not found in database for ID: " + internalTagName);
                     plugin.getLogger().severe("Tag data missing in DB for existing tag ID: " + internalTagName);
                     return; // Stop processing
                }

                // Get player's color preference and format the display string
                PlayerTagColorPreference preference = plugin.getPlayerTagColorPreference(player.getUniqueId(), internalTagName);
                String finalDisplay = plugin.formatTagDisplayWithColor(correctDisplay, preference);


                // Apply the tag using the INTERNAL NAME.
                // setPlayerTag will handle fetching the display and adding the name color.
                plugin.setPlayerTag(player, internalTagName, tagType);
                player.closeInventory(); // Close inventory on main thread
                player.sendMessage(ChatColor.GREEN + "Your " + tagType.name().toLowerCase() + " has been updated to: " + ChatColor.translateAlternateColorCodes('&', finalDisplay));

                // Optional: Re-open menu logic would also go here if uncommented
                // Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getTagMenuManager().openTagSelection(player, currentPage, tagType), 1L); // Need currentPage if re-enabling
            });
        }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable)); // Ensure Bukkit tasks run on main thread if needed within the lambda
    }


     private void removePlayerTag(Player player, TagType tagType) {
        User user = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            // Determine the node type predicate to clear
            NodeType nodeType = (tagType == TagType.PREFIX) ? NodeType.PREFIX : NodeType.SUFFIX;

            // Check if a tag of this type exists *before* clearing
            boolean hadTag = (tagType == TagType.PREFIX) ?
                             (user.getCachedData().getMetaData().getPrefix() != null && !user.getCachedData().getMetaData().getPrefix().isEmpty()) :
                             (user.getCachedData().getMetaData().getSuffix() != null && !user.getCachedData().getMetaData().getSuffix().isEmpty());

            if (hadTag) {
                // Clear existing nodes of that type
                user.data().clear(nodeType.predicate());

                // Save the changes
                plugin.getLuckPerms().getUserManager().saveUser(user).thenRunAsync(() -> {
                    player.sendMessage(ChatColor.GREEN + "Your " + tagType.name().toLowerCase() + " has been removed.");
                }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable)); // Ensure message is sent on main thread
            } else {
                 player.sendMessage(ChatColor.YELLOW + "You did not have a " + tagType.name().toLowerCase() + " set.");
            }
        } // End of if (user != null)
    } // End of removePlayerTag method


    // Handles clicks within the "Confirm Tag Request" menu
    private void handleRequestConfirmation(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        // Meta already checked for null in onInventoryClick

        ItemMeta meta = clickedItem.getItemMeta();
        String itemName = ChatColor.stripColor(meta.getDisplayName());

        if (itemName.equals("Confirm Request")) {
            // Find the preview item to get the tag display
            ItemStack previewItem = event.getInventory().getItem(4); // Assuming preview is at slot 4
            String requestedTagDisplay = null;

            if (previewItem != null && previewItem.hasItemMeta() && previewItem.getItemMeta().hasLore()) {
                // Extract tag from the first line of lore.
                // WARNING: This retrieves the *already translated* tag display.
                // This assumes createCustomTagRequest can handle it or was designed for it.
                // A better approach is storing the original string in PersistentDataContainer.
                List<String> lore = previewItem.getItemMeta().getLore();
                if (!lore.isEmpty()) {
                    // The first line in the confirmation GUI lore is the translated tag
                    requestedTagDisplay = lore.get(0);
                    // We need the raw display with '&' codes for createCustomTagRequest
                    // Let's try to reverse the translation (this is brittle)
                    requestedTagDisplay = requestedTagDisplay.replace("§", "&");

                }
            }

            if (requestedTagDisplay != null) {
                // Trim again just in case something went wrong
                 int endIndex = requestedTagDisplay.indexOf(']') + 1;
                 if (endIndex > 0 && endIndex < requestedTagDisplay.length()) {
                     requestedTagDisplay = requestedTagDisplay.substring(0, endIndex);
                }

               // Call the asynchronous method in uTags.java
               plugin.createCustomTagRequestAsync(player, requestedTagDisplay);
               // Close inventory immediately, feedback will be sent asynchronously
               player.closeInventory();
               // Confirmation/error message is now handled within createCustomTagRequestAsync's callback
           } else {
               player.sendMessage(ChatColor.RED + "Error retrieving tag preview. Please try again.");
                player.closeInventory();
                plugin.getLogger().warning("Could not retrieve requestedTagDisplay from confirmation GUI lore for player " + player.getName());
            }

        } else if (itemName.equals("Cancel")) {
            player.sendMessage(ChatColor.YELLOW + "Tag request cancelled.");
            player.closeInventory();
        }
        // Ignore clicks on other items like the preview item itself
    }


    // --- Methods below were moved to TagMenuManager ---
    // openTagSelection(...)
    // populateTagSelectionInventory(...)
    // addExtraMenuItems(...)
    // addPlayerHead(...)
    // createNavigationArrow(...)
    // createInventoryFrame(...)
    // createCustomTagMenuItem(...)

}