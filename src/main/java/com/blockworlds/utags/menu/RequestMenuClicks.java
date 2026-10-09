package com.blockworlds.utags.menu;

import com.blockworlds.utags.*;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Click handling of the staff request menus. A click on a request opens its review screen; Accept, Deny and Back are
 * buttons there. Requests are identified by the id stored on the item, not by the player name on it.
 */
final class RequestMenuClicks {

    private final uTags plugin;
    private final RequestMenus menus;

    RequestMenuClicks(uTags plugin, RequestMenus menus) {
        this.plugin = plugin;
        this.menus = menus;
    }

    void list(MenuClick click) {
        Player player = click.player();
        if (!player.hasPermission("utags.staff")) {
            player.closeInventory();
            return;
        }
        if (!click.item().hasItemMeta()) return;
        Integer id = requestId(click.item().getItemMeta());
        if (id == null) return;
        withRequest(player, id, request -> menus.openReview(player, request));
    }

    /** Review screen: buttons by slot (see RequestMenus#openReview), never by name: a player can be called "Accept". */
    void review(MenuClick click, int id) {
        Player player = click.player();
        if (!player.hasPermission("utags.staff")) {
            player.closeInventory();
            return;
        }
        switch (click.slot()) {
            case RequestMenus.REVIEW_ACCEPT_SLOT -> decide(player, id, true);
            case RequestMenus.REVIEW_DENY_SLOT -> decide(player, id, false);
            case RequestMenus.REVIEW_BACK_SLOT -> reopenList(player);
            default -> {
            }
        }
    }

    private Integer requestId(ItemMeta meta) {
        return meta.getPersistentDataContainer().get(plugin.requestIdKey(), PersistentDataType.INTEGER);
    }

    /** Loads the request off the main thread, then runs the action on it; reports a request that's gone. */
    private void withRequest(Player player, int id, java.util.function.Consumer<CustomTagRequest> action) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            CustomTagRequest request = plugin.requests().byId(id);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (request == null) {
                    player.sendMessage(ChatColor.RED + "That request was already handled.");
                    reopenList(player);
                    return;
                }
                action.accept(request);
            });
        });
    }

    /** Accepts or denies a request off the main thread, then reports and shows the list again. */
    private void decide(Player player, int id, boolean accept) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            CustomTagRequest request = plugin.requests().byId(id);
            com.blockworlds.utags.tags.TagRequests.AcceptResult result;
            if (request == null) {
                result = com.blockworlds.utags.tags.TagRequests.AcceptResult.ALREADY_HANDLED;
            } else if (accept) {
                result = plugin.requests().accept(request);
            } else {
                result = plugin.requests().deny(request) ? com.blockworlds.utags.tags.TagRequests.AcceptResult.ACCEPTED : com.blockworlds.utags.tags.TagRequests.AcceptResult.ALREADY_HANDLED;
            }
            List<CustomTagRequest> remaining = plugin.requests().all();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (result == com.blockworlds.utags.tags.TagRequests.AcceptResult.FAILED) {
                    player.sendMessage(ChatColor.RED + "Could not create the tag (database error); the request is still pending.");
                } else if (result == com.blockworlds.utags.tags.TagRequests.AcceptResult.ALREADY_HANDLED) {
                    player.sendMessage(ChatColor.RED + "That request was already handled.");
                } else if (accept) {
                    player.sendMessage(ChatColor.GREEN + "Custom tag request accepted.");
                } else {
                    player.sendMessage(ChatColor.RED + "Custom tag request denied.");
                }
                menus.open(player, remaining);
            });
        });
    }

    /** Loads the pending requests off the main thread and opens the list. */
    private void reopenList(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<CustomTagRequest> requests = plugin.requests().all();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) menus.open(player, requests);
            });
        });
    }
}
