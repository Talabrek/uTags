package com.blockworlds.utags.menu;

import com.blockworlds.utags.*;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/** Click handling of the tag colour menu. Controls are found by slot (see {@link TagColorMenuManager}). */
final class TagColorMenuClicks {

    private final uTags plugin;
    private final TagColorMenuManager colorMenuManager;

    TagColorMenuClicks(uTags plugin, TagColorMenuManager colorMenuManager) {
        this.plugin = plugin;
        this.colorMenuManager = colorMenuManager;
    }

    void handle(MenuClick click, TagColorMenuManager.ColorHolder holder) {
        Player player = click.player();
        String tagName = holder.tagName;

        // Validate Tag and Permissions
        Tag tag = plugin.tags().byName(tagName);
        if (tag == null) {
            player.sendMessage(ChatColor.RED + "Could not find the tag: " + tagName);
            player.closeInventory();
            return;
        }
        if (!tag.isColorable()) {
            player.sendMessage(ChatColor.RED + "This tag does not support color customization.");
            player.closeInventory();
            return;
        }
        if (!player.hasPermission("utags.color.change")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to change tag colors.");
            player.closeInventory();
            return;
        }

        int slot = click.slot();
        if (slot == TagColorMenuManager.MODE_SLOT) {
            colorMenuManager.openColorSelectionMenu(player, tag, !holder.editingBrackets);
        } else if (slot == TagColorMenuManager.RESET_SLOT) {
            plugin.selection().setTagColor(player.getUniqueId(), tagName, null, null);
            player.sendMessage(ChatColor.GREEN + "Colors for tag '" + tagName + "' reset to default.");
            colorMenuManager.openColorSelectionMenu(player, tag, true); // Re-open menu
        } else if (slot == TagColorMenuManager.BACK_SLOT) {
            plugin.getTagMenuManager().openTagSelection(player, 0, TagType.PREFIX); // Return to prefix menu
        } else if (slot == TagColorMenuManager.APPLY_SLOT || (slot == TagColorMenuManager.APPLY_PREFIX_SLOT && tag.getType() == TagType.BOTH)) {
            // BOTH tags have two buttons (prefix, suffix); other tags apply as their own type
            TagType applyType = tag.getType() != TagType.BOTH ? tag.getType()
                    : (slot == TagColorMenuManager.APPLY_PREFIX_SLOT ? TagType.PREFIX : TagType.SUFFIX);
            PlayerTagColorPreference preference = plugin.colors().tagPreference(player.getUniqueId(), tagName);
            String finalDisplay = com.blockworlds.utags.display.DisplayService.formatTagDisplayWithColor(tag.getDisplay(), preference);
            // Pass the INTERNAL tag name; the selection service formats it
            plugin.selection().select(player, tagName, applyType);
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "Tag " + ChatColor.translateAlternateColorCodes('&', finalDisplay) + ChatColor.GREEN + " applied as " + applyType.name().toLowerCase() + " with custom colors!");
        } else {
            ChatColor selectedColor = TagColorMenuManager.colorAtSlot(slot);
            if (selectedColor == null) return; // preview item, blank slot
            PlayerTagColorPreference currentPref = plugin.colors().tagPreference(player.getUniqueId(), tagName);
            ChatColor newBracketColor = currentPref.getBracketColor();
            ChatColor newContentColor = currentPref.getContentColor();
            if (holder.editingBrackets) {
                newBracketColor = selectedColor;
                player.sendMessage(ChatColor.GREEN + "Set bracket color to " + selectedColor + selectedColor.name());
            } else {
                newContentColor = selectedColor;
                player.sendMessage(ChatColor.GREEN + "Set content color to " + selectedColor + selectedColor.name());
            }
            plugin.selection().setTagColor(player.getUniqueId(), tagName, newBracketColor, newContentColor);
            // Re-open the menu to reflect the change and show preview
            colorMenuManager.openColorSelectionMenu(player, tag, holder.editingBrackets);
        }
    }
}
