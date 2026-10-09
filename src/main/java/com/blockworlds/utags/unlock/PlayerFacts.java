package com.blockworlds.utags.unlock;

/** What an unlock rule may ask about a player. The Bukkit-backed one must be used on the main thread. */
public interface PlayerFacts {

    boolean hasPermission(String node);

    /** Time played, in hours (Statistic.PLAY_ONE_MINUTE is ticks despite its name). */
    double playtimeHours();

    /** True when the advancement ({@code namespace:path}) is completed. */
    boolean hasAdvancement(String key);

    /** The placeholder text (for example {@code %statistic_mob_kills%}) resolved for the player, or null when it cannot be. */
    String placeholder(String placeholder);
}
