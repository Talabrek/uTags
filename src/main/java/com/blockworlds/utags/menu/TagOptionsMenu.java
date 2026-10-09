package com.blockworlds.utags.menu;

import com.blockworlds.utags.spi.MenuRequest;
import com.blockworlds.utags.*;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Shown when a player who may recolor tags clicks a colorable tag: "Use this tag" or "Change colors". Replaces the
 * old right-click, which Bedrock players can't do in a chest menu.
 */
public final class TagOptionsMenu extends MenuHolder {

    public static final int USE_SLOT = 2;
    public static final int COLORS_SLOT = 6;
    public static final int BACK_SLOT = 8;

    private final String tagName;
    private final TagType tagType;
    private final int returnPage;
    private final uTags plugin;

    private TagOptionsMenu(uTags plugin, String tagName, TagType tagType, int returnPage) {
        this.plugin = plugin;
        this.tagName = tagName;
        this.tagType = tagType;
        this.returnPage = returnPage;
    }

    public static void open(uTags plugin, Player player, Tag tag, String display, TagType tagType, int returnPage) {
        if (plugin.extensions().openOverridden(player, new MenuRequest.Options(tag, display, tagType, returnPage))) return;
        TagOptionsMenu holder = new TagOptionsMenu(plugin, tag.getName(), tagType, returnPage);
        Inventory inv = holder.create(9, Text.c(ChatColor.DARK_AQUA + "Tag Options"));
        inv.setItem(USE_SLOT, button(Material.LIME_WOOL, ChatColor.GREEN + "Use this tag",
                List.of(ChatColor.translateAlternateColorCodes('&', display))));
        inv.setItem(4, button(Material.NAME_TAG, ChatColor.translateAlternateColorCodes('&', display), List.of()));
        inv.setItem(COLORS_SLOT, button(Material.PAINTING, ChatColor.AQUA + "Change colors",
                List.of(ChatColor.GRAY + "Pick the bracket and text colors.")));
        inv.setItem(BACK_SLOT, button(Material.ARROW, ChatColor.GRAY + "Back", List.of()));
        player.openInventory(inv);
    }

    public String tagName() {
        return tagName;
    }

    public TagType tagType() {
        return tagType;
    }

    public int returnPage() {
        return returnPage;
    }

    @Override
    public void onClick(MenuClick click) {
        plugin.getTagMenuManager().clicks().handleTagOptionsClick(click, this);
    }

    private static ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.c(name));
        meta.lore(Text.cl(lore));
        item.setItemMeta(meta);
        return item;
    }
}
