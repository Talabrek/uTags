package com.blockworlds.utags;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public class AdminMenuListener implements Listener {

    private final uTags plugin;
    private final AdminMenuManager adminMenuManager;
    // Map to store ongoing tag creation processes
    private final Map<UUID, TagCreationData> tagCreationProcesses = new HashMap<>();

    // Map to track pending chat input for specific attributes
    private final Map<UUID, String> pendingAdminInput = new HashMap<>();
    public AdminMenuListener(uTags plugin, AdminMenuManager adminMenuManager) {
        this.plugin = plugin;
        this.adminMenuManager = adminMenuManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        String inventoryTitle = event.getView().getTitle();

        // Check if it's one of the admin menus or the creation wizard
        if (!isAdminMenu(inventoryTitle) && !inventoryTitle.startsWith(ChatColor.GREEN + "Create Tag:")) {
             return;
        }

        // Basic checks for valid clicks
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) {
            return; // Prevent clicking outside inventory bounds
        }

        event.setCancelled(true); // Cancel all clicks in admin menus (and wizard for now)

        if (clickedItem == null || clickedItem.getType() == Material.AIR || !clickedItem.hasItemMeta()) {
            return; // Ignore clicks on empty slots or items without meta
        }

        // Handle interaction based on the specific admin menu title
        handleAdminMenuInteraction(event, inventoryTitle);
    }

    private boolean isAdminMenu(String inventoryTitle) {
        // Add more titles as new admin menus are created
        return inventoryTitle.equals(ChatColor.DARK_RED + "uTags Admin Menu")
                || inventoryTitle.startsWith(ChatColor.AQUA + "Tag List")
                || inventoryTitle.startsWith(ChatColor.DARK_AQUA + "Edit Tag:")
                || inventoryTitle.startsWith(ChatColor.DARK_RED + "Confirm Purge:")
                || inventoryTitle.startsWith(ChatColor.DARK_RED + "Confirm Delete:")
                || inventoryTitle.equals(ChatColor.DARK_RED + "Select Purge Type")
                || inventoryTitle.startsWith(ChatColor.GREEN + "Create Tag:"); // Add creation wizard title
    }

    private void handleAdminMenuInteraction(InventoryClickEvent event, String inventoryTitle) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        ItemMeta meta = clickedItem.getItemMeta();
        String itemName = ChatColor.stripColor(meta.getDisplayName());

        if (inventoryTitle.equals(ChatColor.DARK_RED + "uTags Admin Menu")) {
            handleAdminMainMenuClick(player, itemName);
        } else if (inventoryTitle.startsWith(ChatColor.DARK_RED + "Confirm Purge:")) {
             handlePurgeConfirmClick(event, player, itemName, inventoryTitle);
        } else if (inventoryTitle.startsWith(ChatColor.AQUA + "Tag List")) {
             handleTagListClick(event, player, itemName, inventoryTitle);
        } else if (inventoryTitle.startsWith(ChatColor.DARK_AQUA + "Edit Tag:")) {
             handleTagEditorClick(event, player, itemName, inventoryTitle);
        } else if (inventoryTitle.startsWith(ChatColor.DARK_RED + "Confirm Delete:")) {
             handleDeleteConfirmClick(event, player, itemName, inventoryTitle);
        } else if (inventoryTitle.equals(ChatColor.DARK_RED + "Select Purge Type")) {
             handlePurgeTypeSelectionClick(event, player, itemName);
        } else if (inventoryTitle.startsWith(ChatColor.GREEN + "Create Tag:")) {
             handleCreationWizardClick(event, player, itemName); // Route to creation wizard handler
        }
        // TODO: Add else if blocks for other admin menus
    }

    private void handleAdminMainMenuClick(Player player, String itemName) {
        switch (itemName) {
            case "List / Edit Tags":
                adminMenuManager.openTagListMenu(player, 0);
                break;
            case "Create New Tag":
                // Start the creation wizard
                TagCreationData creationData = new TagCreationData();
                tagCreationProcesses.put(player.getUniqueId(), creationData);
                // Open the first step of the wizard
                adminMenuManager.openCreationWizardStep(player, creationData);
                break;
            case "Manage Requests":
                plugin.openRequestsMenu(player);
                break;
            case "Purge Data":
                adminMenuManager.openPurgeTypeSelectionMenu(player);
                break;
            default:
                // Clicked on frame or unknown item
                break;
        }
    } // End of handleAdminMainMenuClick


    // Handles clicks in the Purge Confirmation menu
    private void handlePurgeConfirmClick(InventoryClickEvent event, Player player, String itemName, String inventoryTitle) {
        if (itemName.startsWith("CONFIRM PURGE")) {
            String purgeType = null;
            if (itemName.contains("TAGS")) {
                purgeType = "tags";
            } else if (itemName.contains("REQUESTS")) {
                purgeType = "requests";
            }

            if (purgeType != null) {
                player.closeInventory();
                String command = "tag admin purge " + purgeType + " confirm";
                player.sendMessage(ChatColor.YELLOW + "Executing purge command: /" + command);
                Bukkit.dispatchCommand(player, command);
            } else {
                player.sendMessage(ChatColor.RED + "Error determining purge type from confirmation menu.");
                player.closeInventory();
            }
        } else if (itemName.equals("Cancel")) {
            adminMenuManager.openAdminMainMenu(player);
        }
    } // End of handlePurgeConfirmClick


    // Handles clicks in the Tag List menu
    private void handleTagListClick(InventoryClickEvent event, Player player, String itemName, String inventoryTitle) {
        int currentPage = 0;
        try {
            int pageStartIndex = inventoryTitle.indexOf("(Page ") + 6;
            int pageEndIndex = inventoryTitle.indexOf('/');
            if (pageStartIndex > 5 && pageEndIndex > pageStartIndex) { // Basic validation
                currentPage = Integer.parseInt(inventoryTitle.substring(pageStartIndex, pageEndIndex).trim()) - 1;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not parse page number from Tag List title: " + inventoryTitle);
        }

        switch (itemName) {
            case "Previous Page":
                adminMenuManager.openTagListMenu(player, currentPage - 1);
                break;
            case "Next Page":
                adminMenuManager.openTagListMenu(player, currentPage + 1);
                break;
            case "Back to Admin Menu":
                adminMenuManager.openAdminMainMenu(player);
                break;
            default:
                // Assume a tag item was clicked
                String tagName = null;
                ItemMeta meta = event.getCurrentItem().getItemMeta();
                if (meta != null && meta.hasLore()) {
                    for (String loreLine : meta.getLore()) {
                        String strippedLore = ChatColor.stripColor(loreLine);
                        if (strippedLore.startsWith("Name: ")) {
                            tagName = strippedLore.substring("Name: ".length());
                            break;
                        }
                    }
                }

                if (tagName != null) {
                    Tag tagToEdit = plugin.getTagByName(tagName);
                    if (tagToEdit != null) {
                        adminMenuManager.openTagEditorMenu(player, tagToEdit);
                    } else {
                        player.sendMessage(ChatColor.RED + "Error: Could not find tag data for '" + tagName + "' in the database.");
                        player.closeInventory();
                    }
                } else {
                    // Clicked on frame or failed to get tag name from lore
                }
                break;
        }
    } // End of handleTagListClick


    // Handles clicks in the Tag Editor menu
    private void handleTagEditorClick(InventoryClickEvent event, Player player, String itemName, String inventoryTitle) {
        String tagName = null;
        String titlePrefix = ChatColor.DARK_AQUA + "Edit Tag: " + ChatColor.AQUA;
        if (inventoryTitle.startsWith(titlePrefix)) {
            tagName = inventoryTitle.substring(titlePrefix.length());
            if (tagName.endsWith("...")) {
                 player.sendMessage(ChatColor.RED + "Error: Cannot reliably edit tag with truncated name in title.");
                 player.closeInventory();
                 return;
            }
        }

        if (tagName == null) {
             player.sendMessage(ChatColor.RED + "Error: Could not determine which tag is being edited.");
             player.closeInventory();
             return;
        }

        Tag currentTag = plugin.getTagByName(tagName);
        if (currentTag == null) {
            player.sendMessage(ChatColor.RED + "Error: Tag '" + tagName + "' no longer exists.");
            adminMenuManager.openTagListMenu(player, 0);
            return;
        }

        switch (itemName) {
            case "Back to Tag List":
                adminMenuManager.openTagListMenu(player, 0);
                break;

            case "DELETE TAG":
                adminMenuManager.openDeleteConfirmationMenu(player, currentTag);
                break;

            case "Name (Internal ID)":
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "To change the internal name (ID), use:");
                player.sendMessage(ChatColor.WHITE + "/tag admin edit " + tagName + " name <new_name>");
                player.sendMessage(ChatColor.RED + "Warning: Changing the name requires updating permissions manually!");
                break;

            case "Display Text":
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "To change the display text, use:");
                player.sendMessage(ChatColor.WHITE + "/tag admin edit " + tagName + " display <new_display_text>");
                player.sendMessage(ChatColor.GRAY + "(Use '&' for color codes)");
                break;

            case "Type":
                TagType nextType;
                switch (currentTag.getType()) {
                    case PREFIX: nextType = TagType.SUFFIX; break;
                    case SUFFIX: nextType = TagType.BOTH; break;
                    case BOTH: default: nextType = TagType.PREFIX; break;
                }
                if (plugin.editTagAttribute(tagName, "type", nextType.name())) {
                     player.sendMessage(ChatColor.GREEN + "Tag type set to " + nextType.name());
                     Tag updatedTag = plugin.getTagByName(tagName);
                     if (updatedTag != null) adminMenuManager.openTagEditorMenu(player, updatedTag); else player.closeInventory();
                } else {
                     player.sendMessage(ChatColor.RED + "Failed to update tag type.");
                }
                break;

            case "Weight (Sort Order)":
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "To change the weight (sort order), use:");
                player.sendMessage(ChatColor.WHITE + "/tag admin edit " + tagName + " weight <number>");
                player.sendMessage(ChatColor.GRAY + "(Higher weight appears first in lists)");
                break;

            case "Publicly Visible":
                boolean nextPublic = !currentTag.isPublic();
                if (plugin.editTagAttribute(tagName, "public", String.valueOf(nextPublic))) {
                     player.sendMessage(ChatColor.GREEN + "Tag visibility set to " + (nextPublic ? "Public" : "Private"));
                     Tag updatedTag = plugin.getTagByName(tagName);
                     if (updatedTag != null) adminMenuManager.openTagEditorMenu(player, updatedTag); else player.closeInventory();
                } else {
                     player.sendMessage(ChatColor.RED + "Failed to update tag visibility.");
                }
                break;

            case "Color Flag (Unused?)":
                boolean nextColor = !currentTag.isColor();
                if (plugin.editTagAttribute(tagName, "color", String.valueOf(nextColor))) {
                     player.sendMessage(ChatColor.GREEN + "Tag color flag set to " + nextColor);
                     Tag updatedTag = plugin.getTagByName(tagName);
                     if (updatedTag != null) adminMenuManager.openTagEditorMenu(player, updatedTag); else player.closeInventory();
                } else {
                     player.sendMessage(ChatColor.RED + "Failed to update tag color flag.");
                }
                break;

            case "Current Icon":
                 ItemStack cursorItem = event.getCursor();
                 if (cursorItem != null && cursorItem.getType() != Material.AIR) {
                     String newMaterialData = plugin.serializeMaterial(cursorItem);
                     if (plugin.editTagAttribute(tagName, "material", newMaterialData)) {
                         player.sendMessage(ChatColor.GREEN + "Tag icon updated!");
                         event.setCursor(null);
                         Tag updatedTag = plugin.getTagByName(tagName);
                         if (updatedTag != null) adminMenuManager.openTagEditorMenu(player, updatedTag); else player.closeInventory();
                     } else {
                          player.sendMessage(ChatColor.RED + "Failed to update tag icon.");
                     }
                 } else {
                     player.sendMessage(ChatColor.YELLOW + "Click this slot with the item you want to use as the new icon.");
                 }
                break;

            default:
                // Clicked on frame or unknown item
                break;
        }
    } // End of handleTagEditorClick


    // Handles clicks in the Delete Confirmation menu
    private void handleDeleteConfirmClick(InventoryClickEvent event, Player player, String itemName, String inventoryTitle) {
        String tagName = null;
        String titlePrefix = ChatColor.DARK_RED + "Confirm Delete: ";
        if (inventoryTitle.startsWith(titlePrefix)) {
            tagName = inventoryTitle.substring(titlePrefix.length());
             if (tagName.endsWith("...")) {
                 ItemStack infoItem = event.getInventory().getItem(4);
                 if (infoItem != null && infoItem.hasItemMeta() && infoItem.getItemMeta().hasLore()) {
                     for(String line : infoItem.getItemMeta().getLore()) {
                         String stripped = ChatColor.stripColor(line);
                         if (stripped.startsWith("Name: ")) {
                             tagName = stripped.substring("Name: ".length());
                             break;
                         }
                     }
                 }
                 if (tagName.endsWith("...")) {
                     player.sendMessage(ChatColor.RED + "Error: Cannot reliably delete tag with truncated name.");
                     player.closeInventory();
                     return;
                 }
            }
        }

         if (tagName == null) {
             player.sendMessage(ChatColor.RED + "Error: Could not determine which tag to delete.");
             player.closeInventory();
             return;
         }

        if (itemName.equals("CONFIRM DELETE")) {
            player.closeInventory();
            String command = "tag admin delete " + tagName;
            player.sendMessage(ChatColor.YELLOW + "Executing delete command: /" + command);
            Bukkit.dispatchCommand(player, command);
        } else if (itemName.equals("Cancel")) {
            Tag tagToEdit = plugin.getTagByName(tagName);
            if (tagToEdit != null) {
                adminMenuManager.openTagEditorMenu(player, tagToEdit);
            } else {
                player.sendMessage(ChatColor.RED + "Tag '" + tagName + "' seems to have been deleted. Returning to list.");
                adminMenuManager.openTagListMenu(player, 0);
            }
        }
    } // End of handleDeleteConfirmClick


    // Handles clicks in the Purge Type Selection menu
    private void handlePurgeTypeSelectionClick(InventoryClickEvent event, Player player, String itemName) {
        switch (itemName) {
            case "Purge ALL Tags":
                adminMenuManager.openPurgeConfirmationMenu(player, "tags");
                break;
            case "Purge ALL Requests":
                adminMenuManager.openPurgeConfirmationMenu(player, "requests");
                break;
            case "Cancel":
                adminMenuManager.openAdminMainMenu(player);
                break;
            default:
                // Clicked on frame or unknown item
                break;
        }
    } // End of handlePurgeTypeSelectionClick


    // Handles clicks in the Tag Creation Wizard menu
    private void handleCreationWizardClick(InventoryClickEvent event, Player player, String itemName) {
        TagCreationData data = tagCreationProcesses.get(player.getUniqueId());
        if (data == null) {
            player.sendMessage(ChatColor.RED + "Error: Could not find tag creation data. Please start again from /tag admin gui.");
            player.closeInventory();
            return; // Return early, don't go back to main menu automatically
        }

        switch (itemName) {
            case "Cancel Creation":
                tagCreationProcesses.remove(player.getUniqueId()); // Clean up map
                adminMenuManager.openAdminMainMenu(player); // Go back to main menu
                break;

            case "Confirm Creation":
                if (data.isComplete()) {
                    // Check if tag name already exists
                    if (plugin.getTagByName(data.getName()) != null) {
                        player.sendMessage(ChatColor.RED + "Error: A tag with the name '" + data.getName() + "' already exists.");
                        adminMenuManager.openCreationWizardStep(player, data); // Re-open wizard
                        return;
                    }
                    // Create the tag
                    Tag newTag = new Tag(data.getName(), data.getDisplay(), data.getType(), data.isPublic(), data.isColor(), data.getMaterial(), data.getWeight());
                    plugin.addTagToDatabase(newTag);
                    player.sendMessage(ChatColor.GREEN + "Tag '" + newTag.getName() + "' created successfully!");
                    tagCreationProcesses.remove(player.getUniqueId()); // Clean up map
                    adminMenuManager.openTagListMenu(player, 0); // Go to tag list
                } else {
                    player.sendMessage(ChatColor.RED + "Please set Name, Display, and Weight before confirming.");
                    // Refresh the menu to ensure button state is correct
                    adminMenuManager.openCreationWizardStep(player, data);
                }
                break;

            // --- Attribute Setters ---
            case "Set Name (Internal ID)":
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "Please type the desired internal name (ID) in chat.");
                player.sendMessage(ChatColor.GRAY + "(Letters, numbers, underscores, hyphens only. No spaces.)");
                player.sendMessage(ChatColor.GRAY + "Type 'cancel' to abort setting the name.");
                pendingAdminInput.put(player.getUniqueId(), "name");
                break;

            case "Set Display Text":
                 player.closeInventory();
                 player.sendMessage(ChatColor.YELLOW + "Please type the desired display text in chat.");
                 player.sendMessage(ChatColor.GRAY + "(Use '&' for color codes. Max 15 chars excluding codes. Must be in [])");
                 player.sendMessage(ChatColor.GRAY + "Type 'cancel' to abort setting the display text.");
                 pendingAdminInput.put(player.getUniqueId(), "display");
                break;

            case "Set Weight (Sort Order)":
                int currentWeight = data.getWeight();
                int newWeight = currentWeight; // Initialize with current value

                if (event.isShiftClick()) {
                    if (event.isLeftClick()) {
                        newWeight += 10; // Shift + Left = +10
                    } else if (event.isRightClick()) {
                        newWeight -= 10; // Shift + Right = -10
                    }
                } else {
                    if (event.isLeftClick()) {
                        newWeight += 1; // Left = +1
                    } else if (event.isRightClick()) {
                        newWeight -= 1; // Right = -1
                    }
                }

                // Enforce minimum weight of 1
                newWeight = Math.max(1, newWeight);

                if (newWeight != currentWeight) {
                    data.setWeight(newWeight);
                    player.sendMessage(ChatColor.GREEN + "Tag weight set to: " + newWeight);
                    adminMenuManager.openCreationWizardStep(player, data); // Refresh GUI
                } else {
                    // No change occurred (e.g., trying to decrease below 1)
                    // Optionally add feedback, or just do nothing
                }
                break;

            case "Set Type":
                TagType nextType;
                switch (data.getType()) {
                    case PREFIX: nextType = TagType.SUFFIX; break;
                    case SUFFIX: nextType = TagType.BOTH; break;
                    case BOTH: default: nextType = TagType.PREFIX; break;
                }
                data.setType(nextType);
                adminMenuManager.openCreationWizardStep(player, data); // Refresh GUI
                break;

            case "Set Publicly Visible":
                data.setPublic(!data.isPublic());
                adminMenuManager.openCreationWizardStep(player, data); // Refresh GUI
                break;

            case "Set Color Flag (Unused?)":
                data.setColor(!data.isColor());
                adminMenuManager.openCreationWizardStep(player, data); // Refresh GUI
                break;

            case "Set Icon":
                 ItemStack cursorItem = event.getCursor();
                 if (cursorItem != null && cursorItem.getType() != Material.AIR) {
                     data.setMaterial(cursorItem.clone()); // Set icon in data
                     event.setCursor(null); // Clear cursor
                     player.sendMessage(ChatColor.GREEN + "Icon set to " + data.getMaterial().getType().name());
                     adminMenuManager.openCreationWizardStep(player, data); // Refresh GUI
                 } else {
                     player.sendMessage(ChatColor.YELLOW + "Click this slot with the item you want to use as the icon in your cursor.");
                 }
                break;

            default:
                // Clicked on frame or unknown item
                break;
        }
    } // End of handleCreationWizardClick

    @EventHandler
    public void onAdminChatInput(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String message = event.getMessage();

        // Check if this player is expected to provide input
        if (!pendingAdminInput.containsKey(playerId)) {
            return;
        }

        // Player is providing input, cancel the chat message from appearing globally
        event.setCancelled(true);

        String attributeToSet = pendingAdminInput.get(playerId);
        TagCreationData data = tagCreationProcesses.get(playerId);

        if (data == null) {
            player.sendMessage(ChatColor.RED + "Error: Could not find your tag creation session. Please start again.");
            pendingAdminInput.remove(playerId);
            return;
        }

        // Handle cancellation
        if (message.equalsIgnoreCase("cancel")) {
            player.sendMessage(ChatColor.YELLOW + "Cancelled setting " + attributeToSet + ".");
            pendingAdminInput.remove(playerId);
            // Re-open GUI on the main thread
            Bukkit.getScheduler().runTask(plugin, () -> adminMenuManager.openCreationWizardStep(player, data));
            return;
        }

        boolean success = false;
        String feedback = "";

        // Process input based on the attribute
        switch (attributeToSet) {
            case "name":
                // Basic validation: No spaces, allowed characters (adjust regex as needed)
                if (message.matches("^[a-zA-Z0-9_-]+$")) {
                    // Check if name already exists (case-insensitive check during creation)
                    if (plugin.getTagByName(message) != null) {
                         feedback = ChatColor.RED + "Error: A tag with the name '" + message + "' already exists.";
                    } else {
                        data.setName(message);
                        feedback = ChatColor.GREEN + "Tag name set to: " + message;
                        success = true;
                    }
                } else {
                    feedback = ChatColor.RED + "Invalid name. Use only letters, numbers, underscores, hyphens. No spaces.";
                }
                break;

            case "display":
                int firstBracket = message.indexOf('[');
                int lastBracket = message.lastIndexOf(']');

                // Validation: Must contain brackets in the correct order
                if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket) {
                    // Extract content between the first '[' and last ']'
                    String contentBetweenBrackets = message.substring(firstBracket + 1, lastBracket);
                    String strippedContent = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', contentBetweenBrackets));

                    // Length check on the stripped content between brackets
                    if (strippedContent.length() > 0 && strippedContent.length() <= 15) {
                        data.setDisplay(message); // Store the full input with color codes
                        feedback = ChatColor.GREEN + "Tag display set to: " + ChatColor.translateAlternateColorCodes('&', message);
                        success = true;
                    } else if (strippedContent.length() == 0) {
                        feedback = ChatColor.RED + "Invalid display text. Cannot be empty between brackets.";
                    } else { // Too long
                        feedback = ChatColor.RED + "Invalid display text. Content between brackets (excluding color codes) cannot exceed 15 characters.";
                    }
                } else {
                    feedback = ChatColor.RED + "Invalid display text format. Must contain '[' and ']' in the correct order (e.g., &a[MyTag]).";
                }
                break;

            // Weight is now handled by clicking in the GUI, removed from chat input

            default:
                feedback = ChatColor.RED + "Internal error: Unknown attribute to set.";
                break;
        }

        player.sendMessage(feedback);

        // Only remove pending input if successful or explicitly cancelled (handled above)
        if (success) {
            pendingAdminInput.remove(playerId);
        }

        // Re-open GUI on the main thread regardless of success/failure to show current state or error context
        Bukkit.getScheduler().runTask(plugin, () -> adminMenuManager.openCreationWizardStep(player, data));
    }

} // End of AdminMenuListener class
