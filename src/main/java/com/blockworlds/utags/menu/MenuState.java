package com.blockworlds.utags.menu;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Small per-player state of the menus (moved out of the main class). */
public final class MenuState {

    private final Map<UUID, String> previewTags = Collections.synchronizedMap(new HashMap<>());
    /** Per player: show every public tag in the menu, not only the permitted ones (default false). */
    private final Map<UUID, Boolean> showAll = new ConcurrentHashMap<>();

    public void addPreviewTag(Player player, String tag) { previewTags.put(player.getUniqueId(), tag); }

    public Map<UUID, String> previewTags() { return previewTags; }

    public boolean showAllPublicTags(UUID player) { return showAll.getOrDefault(player, false); }

    public void toggleShowAllPublicTags(UUID player) {
        showAll.compute(player, (uuid, current) -> current == null || !current);
    }
}
