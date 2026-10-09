package com.blockworlds.utags.menu;

import com.blockworlds.utags.*;

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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;


import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Click handling of the tag selection, options and request confirmation menus (called from their holders). */
final class TagMenuClicks {

    private final uTags plugin;

    TagMenuClicks(uTags plugin) {
        this.plugin = plugin;
    }

    // Handles clicks within the Prefix/Suffix selection menus
    void handleTagSelection(MenuClick click, TagType tagType, int currentPage) {
        InventoryClickEvent event = click.event();
        Player player = click.player();
        ItemStack clickedItem = click.item();
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
                    plugin.menuState().toggleShowAllPublicTags(player.getUniqueId());
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
            player.sendMessage(ChatColor.RED + "This custom tag slot is locked. Ask a server admin to unlock it.");
            player.closeInventory();
            return;
        }

        // --- Regular Tag or Unlocked Custom Tag Selection ---
        // The internal name is stored on every tag item (custom tags are c<uuid>-<slot>; never rebuilt from the
        // player's name, which for Bedrock players starts with ".")
        String internalTagName = meta.getPersistentDataContainer().get(plugin.tagKey(), PersistentDataType.STRING);

        // Fallback: the "ID:" lore line (items built before 1.2.0, or a custom lore config that keeps it)
        if (internalTagName == null && meta.hasLore()) {
            for (String loreLine : meta.getLore()) {
                String strippedLore = ChatColor.stripColor(loreLine);
                if (strippedLore.startsWith("ID: ")) {
                    internalTagName = strippedLore.substring("ID: ".length());
                    break;
                }
            }
        }

        // If name found synchronously, process it. Otherwise, try async fallback.
        if (internalTagName != null) {
            onTagClicked(player, internalTagName, tagType, currentPage);
        } else if (itemName.isBlank() || event.getRawSlot() == 49) {
            // Frame panes and the player-info head: nothing to select
            return;
        } else {
            // Last resort: look up the internal name by display name asynchronously
            plugin.getLogger().warning("Attempting fallback: Looking up tag by display name: " + meta.getDisplayName());
            String displayToLookup = ChatColor.translateAlternateColorCodes('&', meta.getDisplayName());
            Material clickedType = clickedItem.getType();

            plugin.tags().nameByDisplayAsync(displayToLookup).thenAcceptAsync(resolvedName -> {
                // Runs on the main thread (see the executor below)
                if (!player.isOnline()) return;
                if (resolvedName == null) {
                    player.sendMessage(ChatColor.RED + "Error: Could not identify the selected tag (fallback failed).");
                    plugin.getLogger().severe("Failed to determine internal name via fallback for clicked item: " + itemName + " (Material: " + clickedType + ")");
                    return;
                }
                onTagClicked(player, resolvedName, tagType, currentPage);
            }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
        }
    }

    /**
     * One click on a tag the player owns. A colorable tag, for a player who may recolor tags, opens the options
     * menu (Use this tag / Change colors); anything else is applied straight away. There's no right-click path:
     * Bedrock players can't right-click in a chest menu.
     */
    private void onTagClicked(Player player, String internalTagName, TagType tagType, int currentPage) {
        if (!plugin.ownership().owns(player, internalTagName)) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this tag.");
            return;
        }
        if (!player.hasPermission("utags.color.change")) {
            processTagSelection(player, internalTagName, tagType);
            return;
        }
        // The tag row decides whether it's colorable: load it off the main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Tag tag = plugin.tags().byName(internalTagName);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (tag != null && tag.isColorable()) {
                    PlayerTagColorPreference preference = plugin.colors().tagPreference(player.getUniqueId(), tag.getName());
                    String display = com.blockworlds.utags.display.DisplayService.formatTagDisplayWithColor(tag.getDisplay(), preference);
                    TagOptionsMenu.open(plugin, player, tag, display, tagType, currentPage);
                } else {
                    processTagSelection(player, internalTagName, tagType);
                }
            });
        });
    }

    /** Clicks in the Tag Options menu . Every button is a single click. */
    void handleTagOptionsClick(MenuClick click, TagOptionsMenu menu) {
        Player player = click.player();
        switch (click.slot()) {
            case TagOptionsMenu.USE_SLOT -> processTagSelection(player, menu.tagName(), menu.tagType());
            case TagOptionsMenu.COLORS_SLOT -> {
                if (!player.hasPermission("utags.color.change")) {
                    player.sendMessage(plugin.display().message("tag-color-no-permission"));
                    return;
                }
                String tagName = menu.tagName();
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    Tag tag = plugin.tags().byName(tagName);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        if (tag == null) {
                            player.sendMessage(ChatColor.RED + "That tag no longer exists.");
                            player.closeInventory();
                            return;
                        }
                        plugin.getTagColorMenuManager().openColorSelectionMenu(player, tag, true);
                    });
                });
            }
            case TagOptionsMenu.BACK_SLOT -> plugin.getTagMenuManager().openTagSelection(player, menu.returnPage(), menu.tagType());
            default -> {
            }
        }
    }

    /**
     * Applies a tag the player clicked (from the selection menu or the options menu): checks the permission,
     * loads the display off the main thread and sets the tag back on it.
     */
    private void processTagSelection(Player player, String internalTagName, TagType tagType) {
        // Final check (safety)
        if (internalTagName == null) {
             player.sendMessage(ChatColor.RED + "Error: Could not identify the selected tag.");
             return;
        }

        // Verify permission (synchronous check is okay here)
        if (!plugin.ownership().owns(player, internalTagName)) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this tag.");
            return;
        }

        // Get the correct display format asynchronously
        plugin.tags().displayByNameAsync(internalTagName).thenAcceptAsync(correctDisplay -> {
            // Runs on the main thread (see the executor below)
            if (!player.isOnline()) return;
            if (correctDisplay == null) {
                player.sendMessage(ChatColor.RED + "Error: Tag data not found in database for ID: " + internalTagName);
                plugin.getLogger().severe("Tag data missing in DB for existing tag ID: " + internalTagName);
                return;
            }

            // Get player's color preference and format the display string
            PlayerTagColorPreference preference = plugin.colors().tagPreference(player.getUniqueId(), internalTagName);
            String finalDisplay = com.blockworlds.utags.display.DisplayService.formatTagDisplayWithColor(correctDisplay, preference);

            // Apply the tag using the INTERNAL NAME.
            // setPlayerTag will handle fetching the display and adding the name color.
            plugin.selection().select(player, internalTagName, tagType);
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "Your " + tagType.name().toLowerCase() + " has been updated to: " + ChatColor.translateAlternateColorCodes('&', finalDisplay));
        }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
    }


    /** Clears the stored selection and the LuckPerms node (the selection is the truth, so clearing the node alone would come back on the next join). */
    private void removePlayerTag(Player player, TagType tagType) {
        User user = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId());
        String current = user == null ? null : (tagType == TagType.PREFIX
                ? user.getCachedData().getMetaData().getPrefix() : user.getCachedData().getMetaData().getSuffix());
        boolean hadTag = (current != null && !current.isEmpty()) || plugin.selection().cached(player.getUniqueId()).prefixTag() != null && tagType == TagType.PREFIX
                || plugin.selection().cached(player.getUniqueId()).suffixTag() != null && tagType == TagType.SUFFIX;
        if (!hadTag) {
            player.sendMessage(ChatColor.YELLOW + "You did not have a " + tagType.name().toLowerCase() + " set.");
            return;
        }
        plugin.selection().clear(player, tagType).thenRunAsync(() ->
                player.sendMessage(ChatColor.GREEN + "Your " + tagType.name().toLowerCase() + " has been removed."),
                runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
    }


    /** Clicks in the "Confirm Tag Request" menu: Confirm and Cancel are buttons by slot. */
    void handleRequestConfirmation(MenuClick click, String requestedTagDisplay) {
        Player player = click.player();
        if (click.slot() == TagMenuManager.CONFIRM_SLOT) {
            // Trim again just in case something went wrong
            int endIndex = requestedTagDisplay.indexOf(']') + 1;
            if (endIndex > 0 && endIndex < requestedTagDisplay.length()) {
                requestedTagDisplay = requestedTagDisplay.substring(0, endIndex);
            }
            plugin.requests().submitAsync(player, requestedTagDisplay);
            // Close inventory immediately, feedback will be sent asynchronously
            player.closeInventory();
        } else if (click.slot() == TagMenuManager.CANCEL_SLOT) {
            player.sendMessage(ChatColor.YELLOW + "Tag request cancelled.");
            player.closeInventory();
        }
        // Ignore clicks on the preview item itself
    }

}
