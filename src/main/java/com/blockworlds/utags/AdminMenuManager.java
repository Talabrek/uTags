package com.blockworlds.utags;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class AdminMenuManager {

    private final uTags plugin;
    private final TagMenuManager tagMenuManager; // For createInventoryFrame

    public AdminMenuManager(uTags plugin, TagMenuManager tagMenuManager) {
        this.plugin = plugin;
        this.tagMenuManager = tagMenuManager;
    }

    // Main Admin Menu
    public void openAdminMainMenu(Player player) {
        Inventory adminMenu = Bukkit.createInventory(player, 27, ChatColor.DARK_RED + "uTags Admin Menu"); // 3 rows

        // --- Placeholder Items ---
        ItemStack listEditItem = new ItemStack(Material.BOOKSHELF);
        ItemMeta listEditMeta = listEditItem.getItemMeta();
        if (listEditMeta != null) {
            listEditMeta.setDisplayName(ChatColor.AQUA + "List / Edit Tags");
            listEditMeta.setLore(Arrays.asList(ChatColor.GRAY + "View, modify, or delete existing tags."));
            listEditItem.setItemMeta(listEditMeta);
        }

        ItemStack createItem = new ItemStack(Material.ANVIL);
        ItemMeta createMeta = createItem.getItemMeta();
        if (createMeta != null) {
            createMeta.setDisplayName(ChatColor.GREEN + "Create New Tag");
            createMeta.setLore(Arrays.asList(ChatColor.GRAY + "Create a new tag using a GUI wizard."));
            createItem.setItemMeta(createMeta);
        }

        ItemStack requestsItem = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta requestsMeta = requestsItem.getItemMeta();
        if (requestsMeta != null) {
            requestsMeta.setDisplayName(ChatColor.YELLOW + "Manage Requests");
            requestsMeta.setLore(Arrays.asList(ChatColor.GRAY + "Accept or deny pending custom tag requests."));
            requestsItem.setItemMeta(requestsMeta);
        }

        ItemStack purgeItem = new ItemStack(Material.LAVA_BUCKET);
        ItemMeta purgeMeta = purgeItem.getItemMeta();
        if (purgeMeta != null) {
            purgeMeta.setDisplayName(ChatColor.RED + "Purge Data");
            purgeMeta.setLore(Arrays.asList(ChatColor.DARK_RED + "" + ChatColor.BOLD + "WARNING:",
                                           ChatColor.GRAY + "Permanently delete tags or requests.",
                                           ChatColor.GRAY + "Requires confirmation."));
            purgeItem.setItemMeta(purgeMeta);
        }

        // Example Layout
        adminMenu.setItem(10, listEditItem);
        adminMenu.setItem(12, createItem);
        adminMenu.setItem(14, requestsItem);
        adminMenu.setItem(16, purgeItem);

        player.openInventory(adminMenu);
    }


    // Opens the paginated list of all tags for viewing/editing
    public void openTagListMenu(Player player, int pageIndex) {
        List<Tag> allTags = plugin.getAvailableTags(null); // Get all tags regardless of type

        // Sort tags alphabetically by name for consistency
        allTags.sort(Comparator.comparing(Tag::getName, String.CASE_INSENSITIVE_ORDER));

        int itemsPerPage = 28; // 4 rows * 7 slots
        int totalPages = Math.max(1, (int) Math.ceil((double) allTags.size() / itemsPerPage)); // Ensure at least 1 page
        pageIndex = Math.max(0, Math.min(pageIndex, totalPages - 1)); // Clamp page index

        String title = ChatColor.AQUA + "Tag List (Page " + (pageIndex + 1) + "/" + totalPages + ")";
        Inventory inventory = tagMenuManager.createInventoryFrame(54, title, // Use common frame creator
                Material.valueOf(plugin.getConfig().getString("frame-material", "GRAY_STAINED_GLASS_PANE")), player); // Different frame color?

        int startIndex = pageIndex * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, allTags.size());

        int[] itemSlots = {10, 11, 12, 13, 14, 15, 16,
                           19, 20, 21, 22, 23, 24, 25,
                           28, 29, 30, 31, 32, 33, 34,
                           37, 38, 39, 40, 41, 42, 43};

        int slotIndex = 0;
        for (int i = startIndex; i < endIndex; i++) {
            if (slotIndex >= itemSlots.length) break;

            Tag tag = allTags.get(i);
            ItemStack tagItem = tag.getMaterial().clone();
            ItemMeta tagMeta = tagItem.getItemMeta();

            if (tagMeta != null) {
                tagMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', tag.getDisplay()));
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Name: " + ChatColor.WHITE + tag.getName());
                lore.add(ChatColor.GRAY + "Type: " + ChatColor.WHITE + tag.getType());
                lore.add(ChatColor.GRAY + "Weight: " + ChatColor.WHITE + tag.getWeight());
                lore.add(ChatColor.GRAY + "Public: " + (tag.isPublic() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
                // lore.add(ChatColor.GRAY + "Color: " + (tag.isColor() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No")); // Add if needed
                lore.add(" ");
                lore.add(ChatColor.YELLOW + "Click to Edit");
                // TODO: Add PDC with tag name
                tagMeta.setLore(lore);
                tagItem.setItemMeta(tagMeta);
            }
            inventory.setItem(itemSlots[slotIndex], tagItem);
            slotIndex++;
        }

        // Navigation Items
        if (pageIndex > 0) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            if (prevMeta != null) {
                prevMeta.setDisplayName(ChatColor.AQUA + "Previous Page");
                prevPage.setItemMeta(prevMeta);
            }
            inventory.setItem(45, prevPage); // Bottom left
        }
        if ((pageIndex + 1) < totalPages) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
             if (nextMeta != null) {
                nextMeta.setDisplayName(ChatColor.AQUA + "Next Page");
                nextPage.setItemMeta(nextMeta);
            }
            inventory.setItem(53, nextPage); // Bottom right
        }

        // Back to Main Admin Menu Item
        ItemStack backItem = new ItemStack(Material.BARRIER); // Or other item
        ItemMeta backMeta = backItem.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.RED + "Back to Admin Menu");
            backItem.setItemMeta(backMeta);
        }
        inventory.setItem(49, backItem); // Bottom center

        player.openInventory(inventory);
    }


    // Opens the editor menu for a specific tag
    public void openTagEditorMenu(Player player, Tag tag) {
        String title = ChatColor.DARK_AQUA + "Edit Tag: " + ChatColor.AQUA + tag.getName();
        // Ensure title length is okay
        if (title.length() > 32) {
            title = title.substring(0, 29) + "...";
        }
        Inventory editorMenu = Bukkit.createInventory(player, 27, title); // 3 rows

        // --- Items representing attributes ---

        // Current Icon (Display Only + Edit Instruction) - Slot 4
        ItemStack iconItem = tag.getMaterial().clone();
        ItemMeta iconMeta = iconItem.getItemMeta();
        if (iconMeta != null) {
            iconMeta.setDisplayName(ChatColor.YELLOW + "Current Icon");
            List<String> iconLore = new ArrayList<>();
            iconLore.add(ChatColor.GRAY + "Material: " + ChatColor.WHITE + iconItem.getType().name());
            iconLore.add(" ");
            iconLore.add(ChatColor.AQUA + "Click with new item");
            iconLore.add(ChatColor.AQUA + "to change icon.");
            iconMeta.setLore(iconLore);
            // Store tag name in PDC
            // TODO: Implement PDC
            // iconMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "utag_editing_name"), PersistentDataType.STRING, tag.getName());
            iconItem.setItemMeta(iconMeta);
        }
        editorMenu.setItem(4, iconItem); // Center top row

        // Name - Slot 10
        ItemStack nameItem = new ItemStack(Material.NAME_TAG);
        ItemMeta nameMeta = nameItem.getItemMeta();
        if (nameMeta != null) {
            nameMeta.setDisplayName(ChatColor.GREEN + "Name (Internal ID)");
            nameMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Current: " + ChatColor.WHITE + tag.getName(),
                    " ",
                    ChatColor.YELLOW + "Click for command info" // Changed lore
            ));
            nameItem.setItemMeta(nameMeta);
        }
        editorMenu.setItem(10, nameItem);

        // Display - Slot 11
        ItemStack displayItem = new ItemStack(Material.PAPER);
        ItemMeta displayMeta = displayItem.getItemMeta();
        if (displayMeta != null) {
            displayMeta.setDisplayName(ChatColor.GREEN + "Display Text");
            displayMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Current: " + ChatColor.translateAlternateColorCodes('&', tag.getDisplay()),
                    " ",
                    ChatColor.YELLOW + "Click for command info" // Changed lore
            ));
            displayItem.setItemMeta(displayMeta);
        }
        editorMenu.setItem(11, displayItem);

        // Type - Slot 12
        ItemStack typeItem = new ItemStack(Material.COMPARATOR);
        ItemMeta typeMeta = typeItem.getItemMeta();
        if (typeMeta != null) {
            typeMeta.setDisplayName(ChatColor.GREEN + "Type");
            typeMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Current: " + ChatColor.WHITE + tag.getType().name(),
                    " ",
                    ChatColor.YELLOW + "Click to cycle (PREFIX/SUFFIX/BOTH)"
            ));
            typeItem.setItemMeta(typeMeta);
        }
        editorMenu.setItem(12, typeItem);

        // Weight - Slot 13
        ItemStack weightItem = new ItemStack(Material.ANVIL); // Or FEATHER?
        ItemMeta weightMeta = weightItem.getItemMeta();
        if (weightMeta != null) {
            weightMeta.setDisplayName(ChatColor.GREEN + "Weight (Sort Order)");
            weightMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Current: " + ChatColor.WHITE + tag.getWeight(),
                    ChatColor.GRAY + "(Higher weight appears first)",
                    " ",
                    ChatColor.YELLOW + "Click for command info" // Changed lore
            ));
            weightItem.setItemMeta(weightMeta);
        }
        editorMenu.setItem(13, weightItem);

        // Public - Slot 14
        ItemStack publicItem = new ItemStack(tag.isPublic() ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta publicMeta = publicItem.getItemMeta();
        if (publicMeta != null) {
            publicMeta.setDisplayName(ChatColor.GREEN + "Publicly Visible");
            publicMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Current: " + (tag.isPublic() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"),
                    ChatColor.GRAY + "(Can players without specific perm see it?)",
                    " ",
                    ChatColor.YELLOW + "Click to toggle"
            ));
            publicItem.setItemMeta(publicMeta);
        }
        editorMenu.setItem(14, publicItem);

        // Color (Placeholder - Keep or Remove?) - Slot 15
        ItemStack colorItem = new ItemStack(tag.isColor() ? Material.ORANGE_DYE : Material.GRAY_DYE);
        ItemMeta colorMeta = colorItem.getItemMeta();
        if (colorMeta != null) {
            colorMeta.setDisplayName(ChatColor.GREEN + "Color Flag (Unused?)");
            colorMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Current: " + (tag.isColor() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"),
                    " ",
                    ChatColor.YELLOW + "Click to toggle"
            ));
            colorItem.setItemMeta(colorMeta);
        }
        editorMenu.setItem(15, colorItem);


        // --- Action Buttons ---

        // Back Button - Slot 18 (Bottom Left)
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.AQUA + "Back to Tag List");
            backItem.setItemMeta(backMeta);
        }
        editorMenu.setItem(18, backItem);

        // Delete Button - Slot 22 (Bottom Center)
        ItemStack deleteItem = new ItemStack(Material.BARRIER);
        ItemMeta deleteMeta = deleteItem.getItemMeta();
        if (deleteMeta != null) {
            deleteMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "DELETE TAG");
            deleteMeta.setLore(Arrays.asList(
                    ChatColor.YELLOW + "Click to permanently delete this tag.",
                    ChatColor.DARK_RED + "Requires confirmation."
            ));
            deleteItem.setItemMeta(deleteMeta);
        }
        editorMenu.setItem(22, deleteItem);

        player.openInventory(editorMenu);
    }




    // Opens a menu to select which data type to purge
    public void openPurgeTypeSelectionMenu(Player player) {
        Inventory selectionMenu = Bukkit.createInventory(player, 9, ChatColor.DARK_RED + "Select Purge Type");

        // Purge Tags Item
        ItemStack tagsItem = new ItemStack(Material.BOOKSHELF); // Or other representative item
        ItemMeta tagsMeta = tagsItem.getItemMeta();
        if (tagsMeta != null) {
            tagsMeta.setDisplayName(ChatColor.RED + "Purge ALL Tags");
            tagsMeta.setLore(Arrays.asList(
                ChatColor.YELLOW + "Click to delete all defined tags.",
                ChatColor.DARK_RED + "Requires further confirmation."
            ));
            tagsItem.setItemMeta(tagsMeta);
        }

        // Purge Requests Item
        ItemStack requestsItem = new ItemStack(Material.WRITABLE_BOOK); // Or other item
        ItemMeta requestsMeta = requestsItem.getItemMeta();
        if (requestsMeta != null) {
            requestsMeta.setDisplayName(ChatColor.RED + "Purge ALL Requests");
            requestsMeta.setLore(Arrays.asList(
                ChatColor.YELLOW + "Click to delete all pending tag requests.",
                ChatColor.DARK_RED + "Requires further confirmation."
            ));
            requestsItem.setItemMeta(requestsMeta);
        }

        // Cancel Item
        ItemStack cancelItem = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName(ChatColor.GREEN + "Cancel");
            cancelMeta.setLore(Arrays.asList(ChatColor.GRAY + "Return to the main admin menu."));
            cancelItem.setItemMeta(cancelMeta);
        }

        // Layout: [T] [T] [T] [R] [R] [R] [C] [C] [C] (T=Tags, R=Requests, C=Cancel) - Example
        selectionMenu.setItem(2, tagsItem);
        selectionMenu.setItem(4, requestsItem);
        selectionMenu.setItem(6, cancelItem);

        player.openInventory(selectionMenu);
    }

    // Opens a confirmation menu for deleting a specific tag
    public void openDeleteConfirmationMenu(Player player, Tag tagToDelete) {
        String title = ChatColor.DARK_RED + "Confirm Delete: " + tagToDelete.getName();
        if (title.length() > 32) {
             title = title.substring(0, 29) + "...";
        }
        Inventory confirmMenu = Bukkit.createInventory(player, 9, title);

        // Tag Info Item (Display Only)
        ItemStack infoItem = tagToDelete.getMaterial().clone();
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.YELLOW + "Delete This Tag?");
            infoMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Name: " + ChatColor.WHITE + tagToDelete.getName(),
                ChatColor.GRAY + "Display: " + ChatColor.translateAlternateColorCodes('&', tagToDelete.getDisplay()),
                " ",
                ChatColor.DARK_RED + "This action cannot be undone!"
            ));
            // Store tag name in PDC for listener
            // TODO: Implement PDC
            // infoMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "utag_delete_name"), PersistentDataType.STRING, tagToDelete.getName());
            infoItem.setItemMeta(infoMeta);
        }

        // Confirm Delete Item (Red Wool)
        ItemStack confirmItem = new ItemStack(Material.RED_WOOL);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "CONFIRM DELETE");
            confirmMeta.setLore(Arrays.asList(ChatColor.YELLOW + "Click to permanently delete tag '" + tagToDelete.getName() + "'."));
            confirmItem.setItemMeta(confirmMeta);
        }

        // Cancel Item (Green Wool)
        ItemStack cancelItem = new ItemStack(Material.GREEN_WOOL);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName(ChatColor.GREEN + "Cancel");
            cancelMeta.setLore(Arrays.asList(ChatColor.GRAY + "Return to the tag editor."));
            cancelItem.setItemMeta(cancelMeta);
        }

        // Layout: [X] [X] [C] [X] [I] [X] [A] [X] [X] (C=Confirm, I=Info, A=Cancel) - Adjusted
        confirmMenu.setItem(2, confirmItem);
        confirmMenu.setItem(4, infoItem);
        confirmMenu.setItem(6, cancelItem);

        player.openInventory(confirmMenu);
    }


    // Opens the main creation wizard step GUI
    public void openCreationWizardStep(Player player, TagCreationData data) {
        String title = ChatColor.GREEN + "Create Tag: Step 1"; // Or just "Create Tag"
        Inventory wizardMenu = Bukkit.createInventory(player, 36, title); // 4 rows

        // --- Display Current Values & Actions ---

        // Name - Slot 10
        ItemStack nameItem = new ItemStack(Material.NAME_TAG);
        ItemMeta nameMeta = nameItem.getItemMeta();
        if (nameMeta != null) {
            nameMeta.setDisplayName(ChatColor.AQUA + "Set Name (Internal ID)");
            List<String> lore = new ArrayList<>();
            if (data.getName() != null) {
                lore.add(ChatColor.GRAY + "Current: " + ChatColor.WHITE + data.getName());
            } else {
                lore.add(ChatColor.GRAY + "Current: " + ChatColor.RED + "Not Set");
            }
            lore.add(" ");
            lore.add(ChatColor.YELLOW + "Click to set via chat command");
            nameMeta.setLore(lore);
            nameItem.setItemMeta(nameMeta);
        }
        wizardMenu.setItem(10, nameItem);

        // Display - Slot 11
        ItemStack displayItem = new ItemStack(Material.PAPER);
        ItemMeta displayMeta = displayItem.getItemMeta();
         if (displayMeta != null) {
            displayMeta.setDisplayName(ChatColor.AQUA + "Set Display Text");
             List<String> lore = new ArrayList<>();
            if (data.getDisplay() != null) {
                lore.add(ChatColor.GRAY + "Current: " + ChatColor.translateAlternateColorCodes('&', data.getDisplay()));
            } else {
                lore.add(ChatColor.GRAY + "Current: " + ChatColor.RED + "Not Set");
            }
            lore.add(" ");
            lore.add(ChatColor.YELLOW + "Click to set via chat command");
            lore.add(ChatColor.GRAY + "(Use '&' for color codes)");
            displayMeta.setLore(lore);
            displayItem.setItemMeta(displayMeta);
        }
        wizardMenu.setItem(11, displayItem);

        // Type - Slot 12
        ItemStack typeItem = new ItemStack(Material.COMPARATOR);
        ItemMeta typeMeta = typeItem.getItemMeta();
        if (typeMeta != null) {
            typeMeta.setDisplayName(ChatColor.AQUA + "Set Type");
            typeMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Current: " + ChatColor.WHITE + data.getType().name(),
                    " ",
                    ChatColor.YELLOW + "Click to cycle (PREFIX/SUFFIX/BOTH)"
            ));
            typeItem.setItemMeta(typeMeta);
        }
        wizardMenu.setItem(12, typeItem);

        // Weight - Slot 13
        ItemStack weightItem = new ItemStack(Material.ANVIL);
        ItemMeta weightMeta = weightItem.getItemMeta();
        if (weightMeta != null) {
            weightMeta.setDisplayName(ChatColor.AQUA + "Set Weight (Sort Order)");
             List<String> lore = new ArrayList<>();
            // Weight is now an int with a default, always has a value
            lore.add(ChatColor.GRAY + "Current: " + ChatColor.WHITE + data.getWeight());
            lore.add(ChatColor.GRAY + "(Higher weight appears first)");
            lore.add(" ");
            lore.add(ChatColor.YELLOW + "Left-Click: " + ChatColor.WHITE + "+1");
            lore.add(ChatColor.YELLOW + "Right-Click: " + ChatColor.WHITE + "-1");
            lore.add(ChatColor.YELLOW + "Shift + Left-Click: " + ChatColor.WHITE + "+10");
            lore.add(ChatColor.YELLOW + "Shift + Right-Click: " + ChatColor.WHITE + "-10");
            lore.add(ChatColor.GRAY + "(Min weight: 1)");
            lore.add(" ");
            lore.add(ChatColor.YELLOW + "Click to set via chat command");
            weightMeta.setLore(lore);
            weightItem.setItemMeta(weightMeta);
        }
        wizardMenu.setItem(13, weightItem);

        // Public - Slot 14
        ItemStack publicItem = new ItemStack(data.isPublic() ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta publicMeta = publicItem.getItemMeta();
        if (publicMeta != null) {
            publicMeta.setDisplayName(ChatColor.AQUA + "Set Publicly Visible");
            publicMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Current: " + (data.isPublic() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"),
                    " ",
                    ChatColor.YELLOW + "Click to toggle"
            ));
            publicItem.setItemMeta(publicMeta);
        }
        wizardMenu.setItem(14, publicItem);

        // Color - Slot 15
        ItemStack colorItem = new ItemStack(data.isColor() ? Material.ORANGE_DYE : Material.GRAY_DYE);
        ItemMeta colorMeta = colorItem.getItemMeta();
        if (colorMeta != null) {
            colorMeta.setDisplayName(ChatColor.AQUA + "Set Color Flag (Unused?)");
             colorMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Current: " + (data.isColor() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"),
                    " ",
                    ChatColor.YELLOW + "Click to toggle"
            ));
            colorItem.setItemMeta(colorMeta);
        }
        wizardMenu.setItem(15, colorItem);

        // Icon - Slot 16
        ItemStack iconItem = data.getMaterial().clone(); // Show current icon
        ItemMeta iconMeta = iconItem.getItemMeta();
        if (iconMeta != null) {
            iconMeta.setDisplayName(ChatColor.AQUA + "Set Icon");
            iconMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + iconItem.getType().name(),
                " ",
                ChatColor.YELLOW + "Click with item on cursor",
                ChatColor.YELLOW + "to set new icon."
            ));
            iconItem.setItemMeta(iconMeta);
        }
        wizardMenu.setItem(16, iconItem);


        // --- Control Buttons ---

        // Cancel Button - Slot 27 (Bottom Left)
        ItemStack cancelItem = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName(ChatColor.RED + "Cancel Creation");
            cancelMeta.setLore(Arrays.asList(ChatColor.GRAY + "Discard changes and exit."));
            cancelItem.setItemMeta(cancelMeta);
        }
        wizardMenu.setItem(27, cancelItem);

        // Confirm Button - Slot 35 (Bottom Right) - Only enable if complete?
        Material confirmMat = data.isComplete() ? Material.GREEN_WOOL : Material.GRAY_WOOL;
        ItemStack confirmItem = new ItemStack(confirmMat);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.setDisplayName(data.isComplete() ? ChatColor.GREEN + "Confirm Creation" : ChatColor.GRAY + "Confirm Creation");
            List<String> confirmLore = new ArrayList<>();
            if (data.isComplete()) {
                confirmLore.add(ChatColor.YELLOW + "Click to create this tag!");
            } else {
                confirmLore.add(ChatColor.RED + "Please set Name, Display,");
                confirmLore.add(ChatColor.RED + "and Weight first.");
            }
            confirmMeta.setLore(confirmLore);
            confirmItem.setItemMeta(confirmMeta);
        }
        wizardMenu.setItem(35, confirmItem);


        player.openInventory(wizardMenu);
    }



    // Opens a confirmation menu for purging data
    public void openPurgeConfirmationMenu(Player player, String purgeType) {


        if (!purgeType.equals("tags") && !purgeType.equals("requests")) {
            player.sendMessage(ChatColor.RED + "Invalid purge type specified for confirmation menu.");
            return;
        }

        Inventory confirmMenu = Bukkit.createInventory(player, 9, ChatColor.DARK_RED + "Confirm Purge: " + purgeType);

        // Confirmation Item (e.g., Red Wool)
        ItemStack confirmItem = new ItemStack(Material.RED_WOOL);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "CONFIRM PURGE " + purgeType.toUpperCase());
            confirmMeta.setLore(Arrays.asList(
                    ChatColor.YELLOW + "Click to permanently delete all " + purgeType + ".",
                    ChatColor.DARK_RED + "This action cannot be undone!"
            ));
            // Store purge type in PDC for listener
            // TODO: Implement PDC
            // confirmMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "utag_purge_type"), PersistentDataType.STRING, purgeType);
            confirmItem.setItemMeta(confirmMeta);
        }

        // Cancel Item (e.g., Green Wool)
        ItemStack cancelItem = new ItemStack(Material.GREEN_WOOL);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName(ChatColor.GREEN + "Cancel");
            cancelMeta.setLore(Arrays.asList(ChatColor.GRAY + "Return to the admin menu."));
            cancelItem.setItemMeta(cancelMeta);
        }

        // Layout: [X] [X] [C] [X] [X] [X] [A] [X] [X] (C=Confirm, A=Cancel)
        confirmMenu.setItem(2, confirmItem);
        confirmMenu.setItem(6, cancelItem);

        player.openInventory(confirmMenu);
    }

    // TODO: Implement openCreateTagWizard(Player player, ...)
    // TODO: Implement openPurgeTypeSelectionMenu(Player player)

}