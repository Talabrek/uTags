package com.blockworlds.utags.unlock;

import com.blockworlds.utags.api.UTagsApi;
import com.blockworlds.utags.module.UTagsContext;
import com.blockworlds.utags.module.UTagsModule;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/** Earned tag unlocks (config {@code unlocks:}): checked when a player joins and every {@code unlocks.check-minutes}. */
public final class UnlockModule implements UTagsModule, Listener {

    /** Ticks to wait after a join, so the ledger has loaded the player's grants before ownership is read. */
    private static final long JOIN_DELAY_TICKS = 100L;

    private Plugin plugin;
    private UnlockService service;
    private BiFunction<Player, String, String> placeholders;
    private BukkitTask timer;

    @Override
    public String id() {
        return "unlock";
    }

    @Override
    public void enable(UTagsContext ctx) {
        plugin = ctx.plugin();
        UnlockRules.Result parsed = UnlockRules.parse(ctx.plugin().getConfig().getMapList("unlocks.rules"));
        parsed.errors().forEach(e -> ctx.logger().warning("Ignoring a bad " + e));
        if (parsed.rules().isEmpty()) return;
        // The API is registered by another module, so look it up once everything is up.
        ctx.storageReady().thenRun(() -> start(ctx, parsed.rules(), UTagsApi.find()));
    }

    private void start(UTagsContext ctx, List<UnlockRule> rules, Optional<UTagsApi> api) {
        Optional<UnlockService> created = UnlockService.create(api, rules, ctx.logger());
        if (created.isEmpty() || !plugin.isEnabled()) return;
        service = created.get();
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholders = PlaceholderAPI::setPlaceholders;
        } else if (rules.stream().anyMatch(r -> r.when() instanceof UnlockRule.Placeholder)) {
            ctx.logger().warning("PlaceholderAPI not found: unlock rules with a placeholder never match.");
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        long period = Math.max(1, plugin.getConfig().getInt("unlocks.check-minutes", 5)) * 60L * 20L;
        timer = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAll, period, period);
        ctx.logger().info("Unlocks on: " + rules.size() + " rule(s), checked every " + period / 1200 + " minute(s).");
        checkAll();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) check(player);
        }, JOIN_DELAY_TICKS);
    }

    private void checkAll() {
        for (Player player : Bukkit.getOnlinePlayers()) check(player);
    }

    private void check(Player player) {
        service.check(player.getUniqueId(), new BukkitFacts(player, placeholders));
    }

    @Override
    public void disable() {
        if (timer != null) timer.cancel();
        HandlerList.unregisterAll(this);
    }
}
