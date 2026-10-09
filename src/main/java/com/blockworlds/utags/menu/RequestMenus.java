package com.blockworlds.utags.menu;

import com.blockworlds.utags.*;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** The staff menus for custom tag requests. */
public final class RequestMenus {

    public static final int REVIEW_ACCEPT_SLOT = 2;
    public static final int REVIEW_DENY_SLOT = 6;
    public static final int REVIEW_BACK_SLOT = 8;

    private final uTags plugin;

    private final RequestMenuClicks clicks;

    public RequestMenus(uTags plugin) {
        this.plugin = plugin;
        this.clicks = new RequestMenuClicks(plugin, this);
    }

    /** The staff list of pending requests. */
    final class ListHolder extends MenuHolder {
        Inventory createInventory(int size) {
            return create(size, Text.c(ChatColor.BLUE + "Custom Tag Requests"));
        }

        @Override
        public void onClick(MenuClick click) {
            clicks.list(click);
        }
    }

    /** The review screen of one request. */
    final class ReviewHolder extends MenuHolder {
        private final int requestId;

        ReviewHolder(int requestId) {
            this.requestId = requestId;
        }

        Inventory createInventory() {
            return create(9, Text.c(ChatColor.BLUE + "Review Tag Request"));
        }

        @Override
        public void onClick(MenuClick click) {
            clicks.review(click, requestId);
        }
    }

    /** Loads the pending requests off the main thread, then opens the list for the player. */
    public void open(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<CustomTagRequest> requests = plugin.requests().all();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) open(player, requests);
            });
        });
    }

    public void open(Player player, List<CustomTagRequest> requests) {
        // Ensure minimum size of 9 (1 row) and maximum of 54 (6 rows)
        int rows = Math.min(Math.max(1, (int) Math.ceil(requests.size() / 9.0)), 6);
        Inventory inventory = new ListHolder().createInventory(rows * 9);
        for (CustomTagRequest request : requests) {
            if (inventory.firstEmpty() < 0) break;
            inventory.addItem(requestItem(request, ChatColor.YELLOW + "Click to review"));
        }
        player.openInventory(inventory);
    }

    /** The review screen for one request: Accept, Deny and Back are separate buttons (one click each). */
    public void openReview(Player player, CustomTagRequest request) {
        Inventory inventory = new ReviewHolder(request.getId()).createInventory();
        inventory.setItem(REVIEW_ACCEPT_SLOT, menuButton(Material.LIME_WOOL, ChatColor.GREEN + "" + ChatColor.BOLD + "Accept"));
        inventory.setItem(4, requestItem(request, null));
        inventory.setItem(REVIEW_DENY_SLOT, menuButton(Material.RED_WOOL, ChatColor.RED + "" + ChatColor.BOLD + "Deny"));
        inventory.setItem(REVIEW_BACK_SLOT, menuButton(Material.ARROW, ChatColor.GRAY + "Back"));
        player.openInventory(inventory);
    }

    private ItemStack requestItem(CustomTagRequest request, String actionLine) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(request.getPlayerUuid()));
        skullMeta.displayName(Text.c(ChatColor.GREEN + request.getPlayerName()));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Requested Tag: " + ChatColor.translateAlternateColorCodes('&', request.getTagDisplay()));
        if (actionLine != null) {
            lore.add("");
            lore.add(actionLine);
        }
        skullMeta.lore(Text.cl(lore));
        // The request is found by id, never by the name on the item (Bedrock names start with ".")
        skullMeta.getPersistentDataContainer().set(plugin.requestIdKey(), PersistentDataType.INTEGER, request.getId());
        item.setItemMeta(skullMeta);
        return item;
    }

    private static ItemStack menuButton(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.c(name));
        item.setItemMeta(meta);
        return item;
    }
}
