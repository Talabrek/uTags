package com.blockworlds.utags.unlock;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

import java.util.function.BiFunction;

/** {@link PlayerFacts} read from a live player. Main thread only. */
final class BukkitFacts implements PlayerFacts {

    private final Player player;
    private final BiFunction<Player, String, String> placeholders;

    /** @param placeholders resolves a placeholder for the player, or null when PlaceholderAPI is not installed */
    BukkitFacts(Player player, BiFunction<Player, String, String> placeholders) {
        this.player = player;
        this.placeholders = placeholders;
    }

    @Override
    public boolean hasPermission(String node) {
        return player.hasPermission(node);
    }

    @Override
    public double playtimeHours() {
        return player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 72000.0;
    }

    @Override
    public boolean hasAdvancement(String key) {
        NamespacedKey nk = NamespacedKey.fromString(key);
        Advancement advancement = nk == null ? null : Bukkit.getAdvancement(nk);
        return advancement != null && player.getAdvancementProgress(advancement).isDone();
    }

    @Override
    public String placeholder(String placeholder) {
        return placeholders == null ? null : placeholders.apply(player, placeholder);
    }
}
