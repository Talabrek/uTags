package com.blockworlds.utags.menu;

import com.blockworlds.utags.*;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public class AdminMenuClicks implements Listener {

    private final uTags plugin;
    private final AdminMenuManager adminMenuManager;
    // Map to store ongoing tag creation processes
    private final Map<UUID, TagCreationData> tagCreationProcesses = new ConcurrentHashMap<>();

    // Map to track pending chat input for specific attributes (read from the async chat thread)
    private final Map<UUID, String> pendingAdminInput = new ConcurrentHashMap<>();
    public AdminMenuClicks(uTags plugin, AdminMenuManager adminMenuManager) {
        this.plugin = plugin;
        this.adminMenuManager = adminMenuManager;
    }

    // --- entry points from the menu holders (see AdminMenuManager); all clicks are already cancelled ---

    private static String nameOf(MenuClick click) {
        ItemMeta meta = click.item().getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return null;
        return ChatColor.stripColor(meta.getDisplayName());
    }

    void mainMenu(MenuClick click) {
        String name = nameOf(click);
        if (name != null) handleAdminMainMenuClick(click.player(), name);
    }

    void purgeType(MenuClick click) {
        String name = nameOf(click);
        if (name != null) handlePurgeTypeSelectionClick(click.event(), click.player(), name);
    }

    void purgeConfirm(MenuClick click, String purgeType) {
        String name = nameOf(click);
        if (name != null) handlePurgeConfirmClick(click.player(), name, purgeType);
    }

    void tagList(MenuClick click, int page) {
        String name = nameOf(click);
        if (name != null) handleTagListClick(click.event(), click.player(), name, page);
    }

    void tagEditor(MenuClick click, String tagName) {
        String name = nameOf(click);
        if (name != null) handleTagEditorClick(click.event(), click.player(), name, tagName);
    }

    void deleteConfirm(MenuClick click, String tagName) {
        String name = nameOf(click);
        if (name != null) handleDeleteConfirmClick(click.player(), name, tagName);
    }

    void wizard(MenuClick click) {
        String name = nameOf(click);
        if (name != null) handleCreationWizardClick(click.event(), click.player(), name);
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
                plugin.requestMenus().open(player);
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
    private void handlePurgeConfirmClick(Player player, String itemName, String purgeType) {
        if (itemName.startsWith("CONFIRM PURGE")) {
            player.closeInventory();
            String command = "tag admin purge " + purgeType + " confirm";
            player.sendMessage(ChatColor.YELLOW + "Executing purge command: /" + command);
            Bukkit.dispatchCommand(player, command);
        } else if (itemName.equals("Cancel")) {
            adminMenuManager.openAdminMainMenu(player);
        }
    } // End of handlePurgeConfirmClick


    // Handles clicks in the Tag List menu
    private void handleTagListClick(InventoryClickEvent event, Player player, String itemName, int currentPage) {
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
                    Tag tagToEdit = plugin.tags().byName(tagName);
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
    private void handleTagEditorClick(InventoryClickEvent event, Player player, String itemName, String tagName) {
        Tag currentTag = plugin.tags().byName(tagName);
        if (currentTag == null) {
            player.sendMessage(ChatColor.RED + "Error: Tag '" + tagName + "' no longer exists.");
            adminMenuManager.openTagListMenu(player, 0);
            return;
        }

        final String editedName = tagName;
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
                plugin.tags().edit(tagName, "type", nextType.name()).thenAccept(edited -> {
                 if (!player.isOnline()) return;
                 if (edited) {
                     player.sendMessage(ChatColor.GREEN + "Tag type set to " + nextType.name());
                     Tag updatedTag = plugin.tags().byName(editedName);
                     if (updatedTag != null) adminMenuManager.openTagEditorMenu(player, updatedTag); else player.closeInventory();
                } else {
                     player.sendMessage(ChatColor.RED + "Failed to update tag type.");
                }
                 });
                break;

            case "Weight (Sort Order)":
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "To change the weight (sort order), use:");
                player.sendMessage(ChatColor.WHITE + "/tag admin edit " + tagName + " weight <number>");
                player.sendMessage(ChatColor.GRAY + "(Higher weight appears first in lists)");
                break;

            case "Publicly Visible":
                boolean nextPublic = !currentTag.isPublic();
                plugin.tags().edit(tagName, "public", String.valueOf(nextPublic)).thenAccept(edited -> {
                 if (!player.isOnline()) return;
                 if (edited) {
                     player.sendMessage(ChatColor.GREEN + "Tag visibility set to " + (nextPublic ? "Public" : "Private"));
                     Tag updatedTag = plugin.tags().byName(editedName);
                     if (updatedTag != null) adminMenuManager.openTagEditorMenu(player, updatedTag); else player.closeInventory();
                } else {
                     player.sendMessage(ChatColor.RED + "Failed to update tag visibility.");
                }
                 });
                break;

            case "Color Flag (Unused?)":
                boolean nextColor = !currentTag.isColor();
                plugin.tags().edit(tagName, "color", String.valueOf(nextColor)).thenAccept(edited -> {
                 if (!player.isOnline()) return;
                 if (edited) {
                     player.sendMessage(ChatColor.GREEN + "Tag color flag set to " + nextColor);
                     Tag updatedTag = plugin.tags().byName(editedName);
                     if (updatedTag != null) adminMenuManager.openTagEditorMenu(player, updatedTag); else player.closeInventory();
                } else {
                     player.sendMessage(ChatColor.RED + "Failed to update tag color flag.");
                }
                 });
                break;

            case "Current Icon":
                 ItemStack cursorItem = event.getCursor();
                 if (cursorItem != null && cursorItem.getType() != Material.AIR) {
                     String newMaterialData = com.blockworlds.utags.tags.TagCodec.serializeMaterial(cursorItem);
                     plugin.tags().edit(tagName, "material", newMaterialData).thenAccept(edited -> {
                 if (!player.isOnline()) return;
                 if (edited) {
                         player.sendMessage(ChatColor.GREEN + "Tag icon updated!");
                         player.setItemOnCursor(null);
                         Tag updatedTag = plugin.tags().byName(editedName);
                         if (updatedTag != null) adminMenuManager.openTagEditorMenu(player, updatedTag); else player.closeInventory();
                     } else {
                          player.sendMessage(ChatColor.RED + "Failed to update tag icon.");
                     }
                 });
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
    private void handleDeleteConfirmClick(Player player, String itemName, String tagName) {
        if (itemName.equals("CONFIRM DELETE")) {
            player.closeInventory();
            String command = "tag admin delete " + tagName;
            player.sendMessage(ChatColor.YELLOW + "Executing delete command: /" + command);
            Bukkit.dispatchCommand(player, command);
        } else if (itemName.equals("Cancel")) {
            Tag tagToEdit = plugin.tags().byName(tagName);
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
                    if (plugin.tags().byName(data.getName()) != null) {
                        player.sendMessage(ChatColor.RED + "Error: A tag with the name '" + data.getName() + "' already exists.");
                        adminMenuManager.openCreationWizardStep(player, data); // Re-open wizard
                        return;
                    }
                    // Create the tag
                    Tag newTag = new Tag(data.getName(), data.getDisplay(), data.getType(), data.isPublic(), data.isColor(), true, data.getMaterial(), data.getWeight());
                    plugin.tags().create(newTag).thenAccept(saved -> {
                    if (!player.isOnline()) return;
                    if (!saved) {
                        // Keep the wizard data so the admin can try again
                        player.sendMessage(ChatColor.RED + "Could not save tag '" + newTag.getName() + "' (database error, see the console).");
                        adminMenuManager.openCreationWizardStep(player, data);
                        return;
                    }
                    player.sendMessage(ChatColor.GREEN + "Tag '" + newTag.getName() + "' created successfully!");
                    tagCreationProcesses.remove(player.getUniqueId()); // Clean up map
                    adminMenuManager.openTagListMenu(player, 0); // Go to tag list
                    });
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
                // One click, then the number in chat (no left/right/shift clicks: Bedrock players can't do those)
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "Please type the weight (sort order) in chat. Current: " + data.getWeight());
                player.sendMessage(ChatColor.GRAY + "(A whole number, 1 or more. Higher weight appears first.)");
                player.sendMessage(ChatColor.GRAY + "Type 'cancel' to abort setting the weight.");
                pendingAdminInput.put(player.getUniqueId(), "weight");
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
                     player.setItemOnCursor(null); // Clear cursor
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
                    if (plugin.tags().byName(message) != null) {
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

            case "weight":
                try {
                    int weight = Integer.parseInt(message.trim());
                    if (weight >= 1) {
                        data.setWeight(weight);
                        feedback = ChatColor.GREEN + "Tag weight set to: " + weight;
                        success = true;
                    } else {
                        feedback = ChatColor.RED + "Invalid weight. It must be 1 or more.";
                    }
                } catch (NumberFormatException e) {
                    feedback = ChatColor.RED + "Invalid weight. Type a whole number, like 10.";
                }
                break;

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

} // End of AdminMenuClicks class
