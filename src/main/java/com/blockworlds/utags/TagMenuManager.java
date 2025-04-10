package com.blockworlds.utags;

import net.luckperms.api.model.user.User;
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
import java.util.List;
import java.util.stream.Collectors;

public class TagMenuManager {
    private final uTags plugin;

    public TagMenuManager(uTags plugin) {
        this.plugin = plugin;
    }

    // Original method - will be modified later to directly open selection
    public void openTagMenu(Player player) {
        // Redirect to openTagSelection directly for prefixes as default
        openTagSelection(player, 0, TagType.PREFIX);

        /* Old intermediate menu code:
        Inventory tagMenu = Bukkit.createInventory(player, 9,  "uTags Menu");

        ItemStack changePrefixItem = new ItemStack(Material.NAME_TAG);
        ItemMeta changePrefixMeta = changePrefixItem.getItemMeta();
        changePrefixMeta.setDisplayName(ChatColor.GREEN + "Change Prefix");
        changePrefixItem.setItemMeta(changePrefixMeta);

        ItemStack changeSuffixItem = new ItemStack(Material.NAME_TAG);
        ItemMeta changeSuffixMeta = changeSuffixItem.getItemMeta();
        changeSuffixMeta.setDisplayName(ChatColor.YELLOW + "Change Suffix");
        changeSuffixItem.setItemMeta(changeSuffixMeta);

        tagMenu.setItem(2, changePrefixItem);
        tagMenu.setItem(6, changeSuffixItem);

        // Add player's head to middle location
        addPlayerHead(player, tagMenu, 4); // Use the refactored method

        player.openInventory(tagMenu);
        */
    }

    // --- Methods moved from TagMenuListener ---

    public void openTagSelection(Player player, int pageIndex, TagType selectionType) {
        List<Tag> tags = plugin.getAvailableTags(selectionType);
        // Filter tags based on type (prefix/suffix/both)
        List<Tag> availableTags = tags.stream()
                .filter(tag -> tag.getType() == selectionType || tag.getType() == TagType.BOTH)
                .collect(Collectors.toList());

        // We will now filter tags inside populateTagSelectionInventory based on preference
        // List<Tag> permittedTags = availableTags.stream()
        //         .filter(tag -> tag.isPublic() || player.hasPermission("utags.tag." + tag.getName()))
        //         .collect(Collectors.toList());
        // Pass all available tags of the correct type
        List<Tag> tagsToDisplay = availableTags;

        String inventoryTitle = selectionType == TagType.PREFIX ? "Select Prefix" : "Select Suffix";
        // Ensure title length doesn't exceed limits, add page number safely
        String fullTitle = inventoryTitle + " (Page " + (pageIndex + 1) + ")";
        if (fullTitle.length() > 32) {
            fullTitle = inventoryTitle.substring(0, Math.min(inventoryTitle.length(), 25)) + "... (P" + (pageIndex + 1) + ")";
        }

        Inventory inventory = createInventoryFrame(54, fullTitle,
                Material.valueOf(plugin.getConfig().getString("frame-material", "BLACK_STAINED_GLASS_PANE")), player);

        populateTagSelectionInventory(player, inventory, tagsToDisplay, pageIndex, selectionType);

        // Add custom tag slots only for Prefix menu (top row)
        if (selectionType == TagType.PREFIX) {
            int[] itemSlots = {1, 3, 5, 7}; // Top row slots for custom tags
            for (int i = 0; i < itemSlots.length; i++) {
                // Only add if slot index is valid (e.g., 0-3 for 4 slots)
                ItemStack customTagItem = createCustomTagMenuItem(player, i);
                inventory.setItem(itemSlots[i], customTagItem);
            }
        }

        player.openInventory(inventory);
    }

    private void populateTagSelectionInventory(Player player, Inventory inventory, List<Tag> allAvailableTags, int pageIndex, TagType selectionType) {
        int itemsPerPage = 28; // 4 rows * 7 slots
        int startIndex = pageIndex * itemsPerPage;
        // We need to iterate through all tags and apply filtering *before* pagination logic.
        // This requires restructuring how pagination works.

        boolean showAll = plugin.getShowAllPublicTagsPreference(player.getUniqueId());
        List<Tag> displayableTags = new ArrayList<>();

        for (Tag tag : allAvailableTags) {
            boolean hasPermission = player.hasPermission("utags.tag." + tag.getName());
            boolean isPublic = tag.isPublic();

            if (hasPermission) {
                displayableTags.add(tag); // Always show if permitted
            } else if (showAll && isPublic) {
                // Mark this tag to be shown as locked (we'll handle item creation later)
                displayableTags.add(tag); // Add it, but we'll modify its appearance
            }
            // Implicitly skip if !hasPermission && !showAll
            // Implicitly skip if !hasPermission && showAll && !isPublic
        }

        int totalDisplayableItems = displayableTags.size();
        // int startIndex = pageIndex * itemsPerPage; // Removed duplicate declaration
        int endIndex = Math.min(startIndex + itemsPerPage, totalDisplayableItems);
        // Slots available for tags (excluding frame and navigation)
        int[] itemSlots = {10, 11, 12, 13, 14, 15, 16,
                           19, 20, 21, 22, 23, 24, 25,
                           28, 29, 30, 31, 32, 33, 34,
                           37, 38, 39, 40, 41, 42, 43};

        String currentTagDisplay = null;
        User user = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            currentTagDisplay = (selectionType == TagType.PREFIX) ?
                    user.getCachedData().getMetaData().getPrefix() :
                    user.getCachedData().getMetaData().getSuffix();
            // Normalize current tag display by removing color codes for comparison if needed
            // currentTagDisplay = currentTagDisplay != null ? ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', currentTagDisplay)) : null;
        }


        int slotIndex = 0;
        for (int i = startIndex; i < endIndex; i++) {
            if (slotIndex >= itemSlots.length) break; // Safety check

            Tag tag = displayableTags.get(i);
            boolean hasPermission = player.hasPermission("utags.tag." + tag.getName());
            // We already filtered based on showAll preference, so we just need to know
            // if we need to display it normally or as locked.

            ItemStack tagItem;
            ItemMeta tagMeta;

            if (hasPermission) {
                // --- Normal Tag Item Creation ---
                tagItem = tag.getMaterial().clone(); // Clone to avoid modifying original
                tagMeta = tagItem.getItemMeta();

                if (tagMeta != null) {
                    // Get player preference and format display name
                    PlayerTagColorPreference preference = plugin.getPlayerTagColorPreference(player.getUniqueId(), tag.getName());
                    String formattedDisplay = plugin.formatTagDisplayWithColor(tag.getDisplay(), preference);
                    String display = ChatColor.translateAlternateColorCodes('&', formattedDisplay);
                    tagMeta.setDisplayName(display);

                    List<String> lore = new ArrayList<>();
                    lore.add(" "); // Spacer
                    lore.add(ChatColor.YELLOW + "Click to Select");
                    lore.add(ChatColor.DARK_GRAY + "ID: " + tag.getName()); // Use DARK_GRAY for internal info
                    // Add default display to lore if different from current display
                    String defaultDisplay = ChatColor.translateAlternateColorCodes('&', tag.getDisplay());
                    if (!display.equals(defaultDisplay)) {
                        lore.add(ChatColor.YELLOW + "Default: " + defaultDisplay);
                    }
                    // Add color customization hint if applicable
                    if (tag.isColor()) {
                        lore.add(ChatColor.AQUA + "Right-click to change color");
                    }
                    tagMeta.setLore(lore);

                    // Add enchantment glow if this is the currently selected tag
                    if (currentTagDisplay != null && currentTagDisplay.equals(tag.getDisplay())) { // Compare raw display string from DB
                       try {
                           tagItem.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.LURE, 1); // Use any enchantment
                           tagMeta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                       } catch (Exception e) {
                           plugin.getLogger().warning("Failed to add enchantment glow: " + e.getMessage());
                       }
                    }
                    tagItem.setItemMeta(tagMeta); // Apply meta changes
                }
                // --- End Normal Tag Item Creation ---
            } else {
                // --- Locked Tag Item Creation ---
                // Player doesn't have permission, but showAll is true and tag is public
                tagItem = new ItemStack(Material.BARRIER); // Locked item material
                tagMeta = tagItem.getItemMeta();

                if (tagMeta != null) {
                    String display = ChatColor.translateAlternateColorCodes('&', tag.getDisplay()); // Show original display but grayed out?
                    tagMeta.setDisplayName(ChatColor.GRAY + "" + ChatColor.STRIKETHROUGH + display); // Example: Grayed out display

                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.RED + "Locked - Requires Permission");
                    lore.add(ChatColor.GRAY + "Permission: utags.tag." + tag.getName());
                    lore.add(" ");
                    lore.add(ChatColor.DARK_GRAY + "ID: " + tag.getName());
                    tagMeta.setLore(lore);

                    tagItem.setItemMeta(tagMeta);
                }
                // --- End Locked Tag Item Creation ---
            }


            // Place the created item (either normal or locked) into the inventory
            inventory.setItem(itemSlots[slotIndex], tagItem);
            slotIndex++;
        }
        // Pass total count of *displayable* tags for accurate pagination
        addExtraMenuItems(player, inventory, pageIndex, totalDisplayableItems, itemsPerPage, selectionType);
    }

    private void addExtraMenuItems(Player player, Inventory inventory, int pageIndex, int totalPermittedTags, int itemsPerPage, TagType currentType) {
        addPlayerHead(player, inventory, 49); // Player info always at bottom center

        // Previous Page Arrow
        if (pageIndex > 0) {
            ItemStack prevPageItem = createNavigationArrow(ChatColor.AQUA + "Previous Page");
            inventory.setItem(45, prevPageItem); // Bottom left
        }

        // Next Page Arrow
        // Check if there are more items than currently displayed up to this page
        if ((pageIndex + 1) * itemsPerPage < totalPermittedTags) {
            ItemStack nextPageItem = createNavigationArrow(ChatColor.AQUA + "Next Page");
            inventory.setItem(53, nextPageItem); // Bottom right
        }

        // Add Switch Button (Prefix/Suffix)
        ItemStack switchItem;
        ItemMeta switchMeta;
        if (currentType == TagType.PREFIX) {
            switchItem = new ItemStack(Material.NAME_TAG); // Consider different materials? Book?
            switchMeta = switchItem.getItemMeta();
            switchMeta.setDisplayName(ChatColor.YELLOW + "Switch to Suffixes");
            switchMeta.setLore(Arrays.asList(ChatColor.GRAY + "Click to view available suffixes."));
        } else {
            switchItem = new ItemStack(Material.NAME_TAG);
            switchMeta = switchItem.getItemMeta();
            switchMeta.setDisplayName(ChatColor.GREEN + "Switch to Prefixes");
            switchMeta.setLore(Arrays.asList(ChatColor.GRAY + "Click to view available prefixes."));
        }
        if (switchMeta != null) { // Null check
            switchItem.setItemMeta(switchMeta);
        }
        inventory.setItem(48, switchItem); // Slot next to player head (left)

        // Add Remove Tag Button
        ItemStack removeItem = new ItemStack(Material.BARRIER);
        ItemMeta removeMeta = removeItem.getItemMeta();
        if (removeMeta != null) { // Null check
            removeMeta.setDisplayName(ChatColor.RED + "Remove Current " + (currentType == TagType.PREFIX ? "Prefix" : "Suffix"));
            removeMeta.setLore(Arrays.asList(ChatColor.GRAY + "Click to remove your active tag."));
            removeItem.setItemMeta(removeMeta);
        }
        inventory.setItem(50, removeItem); // Slot next to player head (right)

        // Add Change Name Color Button
        ItemStack changeNameColorItem = new ItemStack(Material.EMERALD);
        ItemMeta changeNameColorMeta = changeNameColorItem.getItemMeta();
        if (changeNameColorMeta != null) {
            changeNameColorMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Change Name Color");
            changeNameColorMeta.setLore(Arrays.asList(ChatColor.GRAY + "Click to choose your name color."));
            changeNameColorItem.setItemMeta(changeNameColorMeta);
        }
        inventory.setItem(47, changeNameColorItem); // Slot next to switch button (left)

        // Add Tag Visibility Toggle Button (New)
        boolean showAll = plugin.getShowAllPublicTagsPreference(player.getUniqueId());
        ItemStack toggleItem;
        ItemMeta toggleMeta;
        List<String> toggleLore = new ArrayList<>();
        String identifierLore = ChatColor.DARK_GRAY + "utag_toggle_visibility"; // Identifier for listener

        if (showAll) {
            toggleItem = new ItemStack(Material.ENDER_PEARL);
            toggleMeta = toggleItem.getItemMeta();
            if (toggleMeta != null) {
                toggleMeta.setDisplayName(ChatColor.AQUA + "Show: All Public Tags");
                toggleLore.add(ChatColor.GRAY + "Currently showing all public tags.");
                toggleLore.add(ChatColor.YELLOW + "Click to show only permitted tags.");
                toggleLore.add(" ");
                toggleLore.add(identifierLore);
                toggleMeta.setLore(toggleLore);
            }
        } else {
            toggleItem = new ItemStack(Material.ENDER_EYE); // Changed from EYE_OF_ENDER
            toggleMeta = toggleItem.getItemMeta();
            if (toggleMeta != null) {
                toggleMeta.setDisplayName(ChatColor.GREEN + "Show: Permitted Tags");
                toggleLore.add(ChatColor.GRAY + "Currently showing only tags you");
                toggleLore.add(ChatColor.GRAY + "have permission for.");
                toggleLore.add(ChatColor.YELLOW + "Click to show all public tags.");
                 toggleLore.add(" ");
                toggleLore.add(identifierLore);
               toggleMeta.setLore(toggleLore);
            }
        }

        if (toggleMeta != null) { // Ensure meta exists before setting
            toggleItem.setItemMeta(toggleMeta);
        }
        inventory.setItem(51, toggleItem); // Slot next to remove button (right)
    }

    public void addPlayerHead(Player player, Inventory inventory, int location) {
        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta playerHeadMeta = (SkullMeta) playerHead.getItemMeta();
        if (playerHeadMeta == null) return; // Safety check

        playerHeadMeta.setOwningPlayer(player);
        playerHeadMeta.setDisplayName(ChatColor.YELLOW + player.getName());

        String prefix = null;
        String suffix = null;
        User user = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            prefix = user.getCachedData().getMetaData().getPrefix();
            suffix = user.getCachedData().getMetaData().getSuffix();
        }

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "--- Current Tags ---");
        if (prefix != null && !prefix.isEmpty()) {
            lore.add(ChatColor.GRAY + "Prefix: " + ChatColor.translateAlternateColorCodes('&', prefix));
        } else {
            lore.add(ChatColor.GRAY + "Prefix: " + ChatColor.ITALIC + "None");
        }
        if (suffix != null && !suffix.isEmpty()) {
            lore.add(ChatColor.GRAY + "Suffix: " + ChatColor.translateAlternateColorCodes('&', suffix));
        } else {
            lore.add(ChatColor.GRAY + "Suffix: " + ChatColor.ITALIC + "None");
        }

        playerHeadMeta.setLore(lore);
        playerHead.setItemMeta(playerHeadMeta);
        inventory.setItem(location, playerHead);
    }

    private ItemStack createNavigationArrow(String displayName) {
        ItemStack arrowItem = new ItemStack(Material.ARROW);
        ItemMeta arrowMeta = arrowItem.getItemMeta();
        if (arrowMeta == null) return arrowItem; // Safety check
        arrowMeta.setDisplayName(displayName);
        arrowItem.setItemMeta(arrowMeta);
        return arrowItem;
    }

    public Inventory createInventoryFrame(int size, String title, Material frameMaterial, Player player) {
        // Ensure size is valid
        if (size <= 0 || size % 9 != 0) {
            size = 54; // Default to max size if invalid
        }
        // Ensure title is not too long
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }
        Inventory inventory = Bukkit.createInventory(player, size, title);

        ItemStack frameItem = new ItemStack(frameMaterial);
        ItemMeta frameMeta = frameItem.getItemMeta();
        if (frameMeta != null) {
            frameMeta.setDisplayName(" ");
            frameItem.setItemMeta(frameMeta);
        }

        // Fill the border slots
        for (int i = 0; i < size; i++) {
            // Check if it's a border slot
            if (i < 9 || i >= size - 9 || i % 9 == 0 || (i + 1) % 9 == 0) {
                inventory.setItem(i, frameItem.clone()); // Use clone to avoid issues
            }
        }

        return inventory;
    }

    private ItemStack createCustomTagMenuItem(Player player, int slotIndex) {
        // slotIndex is 0-based (0, 1, 2, 3 for 4 slots)
        String permissionBase = "utags.custom"; // e.g., utags.custom1, utags.custom2
        String permissionTagBase = "utags.tag." + player.getName(); // e.g., utags.tag.PlayerName1
        ItemStack item;
        ItemMeta meta;

        String customTagPermission = permissionTagBase + (slotIndex + 1); // Permission for the specific tag
        String customSlotPermission = permissionBase + (slotIndex + 1); // Permission for the slot itself

        if (player.hasPermission(customTagPermission)) {
            // Player has the specific custom tag unlocked (implies they also have the slot permission)
            item = new ItemStack(Material.PLAYER_HEAD, 1);
            SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
            if (skullMeta != null) {
                 skullMeta.setOwningPlayer(player); // Show player's head
                 meta = skullMeta;

                 String tagInternalName = player.getName() + (slotIndex + 1);
                 String tagDisplay = plugin.getTagDisplayByName(tagInternalName);
                 if (tagDisplay == null) {
                     // This case might mean the tag was granted but somehow removed from DB? Or name mismatch.
                     tagDisplay = ChatColor.RED + "" + ChatColor.ITALIC + "Error: Not Found";
                 } else {
                     tagDisplay = ChatColor.translateAlternateColorCodes('&', tagDisplay);
                 }

                 meta.setDisplayName(ChatColor.GOLD + "Custom Tag #" + (slotIndex + 1));
                 // Configurable Lore for Unlocked/Set Tags
                 List<String> configLoreSet = plugin.getConfig().getStringList("gui.custom-tag-items.unlocked-set.lore");
                 List<String> finalLoreSet = new ArrayList<>();
                 for (String line : configLoreSet) {
                     finalLoreSet.add(ChatColor.translateAlternateColorCodes('&', line
                             .replace("%display%", tagDisplay)
                             .replace("%internal_name%", tagInternalName)
                             .replace("%slot_number%", String.valueOf(slotIndex + 1))
                     ));
                 }
                 // Default lore if config is empty
                 if (finalLoreSet.isEmpty()) {
                     finalLoreSet.add(ChatColor.GRAY + "Display: " + tagDisplay);
                     finalLoreSet.add(" ");
                     finalLoreSet.add(ChatColor.YELLOW + "Click to Select");
                     finalLoreSet.add(ChatColor.DARK_GRAY + "ID: " + tagInternalName);
                 }
                 meta.setLore(finalLoreSet);


                 // TODO: Add PersistentDataContainer here too
                 // PersistentDataContainer pdc = meta.getPersistentDataContainer();
                 // pdc.set(new NamespacedKey(plugin, "utag_internal_name"), PersistentDataType.STRING, tagInternalName);

            } else {
                 // Fallback if SkullMeta fails
                 item = new ItemStack(Material.PAPER);
                 meta = item.getItemMeta();
                 meta.setDisplayName(ChatColor.GOLD + "Custom Tag #" + (slotIndex + 1));
            }

        } else if (player.hasPermission(customSlotPermission)) {
            // Player has the slot unlocked but hasn't requested/received the specific tag permission yet
            item = new ItemStack(Material.WRITABLE_BOOK, 1);
            meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + "Request Custom Tag #" + (slotIndex + 1));
            // Configurable Lore for Requestable Slots
            List<String> configLoreReq = plugin.getConfig().getStringList("gui.custom-tag-items.unlocked-requestable.lore");
            List<String> finalLoreReq = new ArrayList<>();
            for (String line : configLoreReq) {
                finalLoreReq.add(ChatColor.translateAlternateColorCodes('&', line
                        .replace("%slot_number%", String.valueOf(slotIndex + 1))
                ));
            }
            // Default lore if config is empty
            if (finalLoreReq.isEmpty()) {
                finalLoreReq.add(ChatColor.GRAY + "You have unlocked this slot!");
                finalLoreReq.add(ChatColor.GRAY + "Click here or use " + ChatColor.WHITE + "/tag request <display>");
                finalLoreReq.add(ChatColor.GRAY + "to submit your custom tag.");
            }
            meta.setLore(finalLoreReq); // Use finalLoreReq here
        } else {
            // Player does not have permission for this slot
            item = new ItemStack(Material.BARRIER, 1);
            meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.RED + "Locked Custom Tag #" + (slotIndex + 1));
            // Configurable Lore for Locked Slots
            List<String> configLoreLocked = plugin.getConfig().getStringList("gui.custom-tag-items.locked.lore");
            List<String> finalLoreLocked = new ArrayList<>();
            for (String line : configLoreLocked) {
                finalLoreLocked.add(ChatColor.translateAlternateColorCodes('&', line
                        .replace("%slot_number%", String.valueOf(slotIndex + 1))
                ));
            }
            // Default lore if config is empty
            if (finalLoreLocked.isEmpty()) {
                finalLoreLocked.add(ChatColor.GRAY + "Unlock this slot by ranking up");
                finalLoreLocked.add(ChatColor.GRAY + "or visiting the server store.");
            }
            meta.setLore(finalLoreLocked);
        } // End of the main if-else if-else structure

        if (meta != null) { // Ensure meta is not null before setting
             item.setItemMeta(meta);
        }
        return item;
    } // End of createCustomTagMenuItem



    // Method to open the custom tag request confirmation GUI
    public void openRequestConfirmation(Player player, String requestedTagDisplay) {
        // Simple 9-slot inventory for confirmation
        Inventory confirmationMenu = Bukkit.createInventory(player, 9, "Confirm Tag Request");

        // Item showing the tag preview
        ItemStack previewItem = new ItemStack(Material.PAPER); // Or NAME_TAG
        ItemMeta previewMeta = previewItem.getItemMeta();
        if (previewMeta != null) {
            previewMeta.setDisplayName(ChatColor.GOLD + "Preview:");
            previewMeta.setLore(Arrays.asList(
                    ChatColor.translateAlternateColorCodes('&', requestedTagDisplay),
                    " ",
                    ChatColor.GRAY + "Is this the tag you want to request?"
            ));
            // Store the requested tag display in the item's PDC for the listener
            // TODO: Implement PDC
            // previewMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "utag_request_display"), PersistentDataType.STRING, requestedTagDisplay);
            previewItem.setItemMeta(previewMeta);
        }

        // Confirm Button
        ItemStack confirmItem = new ItemStack(Material.GREEN_WOOL); // Or LIME_WOOL
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Confirm Request");
            confirmMeta.setLore(Arrays.asList(ChatColor.GRAY + "Submit this tag for review."));
            confirmItem.setItemMeta(confirmMeta);
        }

        // Cancel Button
        ItemStack cancelItem = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Cancel");
            cancelMeta.setLore(Arrays.asList(ChatColor.GRAY + "Do not request this tag."));
            cancelItem.setItemMeta(cancelMeta);
        }

        // Layout: [C] [C] [C] [P] [X] [X] [X] [X] [X]
        // Example layout: Confirm at 2, Preview at 4, Cancel at 6
        confirmationMenu.setItem(2, confirmItem); // Confirm button
        confirmationMenu.setItem(4, previewItem);  // Preview in the middle
        confirmationMenu.setItem(6, cancelItem);  // Cancel button

        player.openInventory(confirmationMenu);
    }

    // --- End of moved methods ---
}