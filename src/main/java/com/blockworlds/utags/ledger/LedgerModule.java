package com.blockworlds.utags.ledger;

import com.blockworlds.utags.api.UTagsApi;
import com.blockworlds.utags.impl.UTagsApiImpl;
import com.blockworlds.utags.module.UTagsContext;
import com.blockworlds.utags.module.UTagsModule;
import com.blockworlds.utags.uTags;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;

import java.util.concurrent.CompletableFuture;

/**
 * Wires the grants ledger and the public API: the ownership cache, its listeners and the {@link UTagsApi} service now;
 * the ledger is attached once the schema (migration 7) exists. Migration 7 itself is a core migration ({@code Migrations.core()}).
 */
public final class LedgerModule implements UTagsModule {

    private UTagsContext ctx;
    private UTagsApiImpl api;
    private volatile boolean disabled;
    private final CompletableFuture<Void> ready = new CompletableFuture<>();

    @Override
    public String id() {
        return "ledger";
    }

    @Override
    public void enable(UTagsContext ctx) {
        this.ctx = ctx;
        uTags plugin = ctx.plugin();
        Ownership ownership = plugin.ownership();
        LedgerStore store = new LedgerStore(ctx.database().dialect());
        LedgerService service = new LedgerService(plugin, ctx.database(), store, ctx.tags(), ownership, ctx.selection(),
                ctx.changes(), ctx.luckPerms());
        UTagsApiImpl impl = new UTagsApiImpl(ctx.database(), ctx.tags(), service, ownership, ctx.selection(), ctx.colors(),
                ready, p -> plugin.getTagMenuManager().openTagMenu(p));
        impl.setEdition(plugin::edition);
        impl.setExtensions(ctx.extensions());
        ctx.selection().useWriter(store::select); // selects take the player lock, like revokes
        Bukkit.getPluginManager().registerEvents(ownership, plugin);
        ownership.subscribe(ctx.changes());
        // Registered now, so plugins that depend on uTags find the API in their onEnable; calls that touch the database
        // wait on ready, which completes once the migrations ran and the ledger is attached.
        Bukkit.getServicesManager().register(UTagsApi.class, impl, plugin, ServicePriority.Normal);
        this.api = impl;
        ctx.logger().info("uTags API " + UTagsApi.VERSION + " registered.");
        ctx.storageReady().whenComplete((v, ex) -> {
            if (ex != null) {
                ready.completeExceptionally(ex);
                return;
            }
            if (disabled) return;
            ownership.attach(ctx.database(), store);
            for (Player online : Bukkit.getOnlinePlayers()) ownership.reload(online.getUniqueId());
            plugin.setLedger(service);
            ready.complete(null);
        });
    }

    @Override
    public void disable() {
        disabled = true;
        ready.completeExceptionally(new IllegalStateException("uTags is disabled"));
        if (api != null) {
            Bukkit.getServicesManager().unregister(UTagsApi.class, api);
            api = null;
        }
        ctx.plugin().setLedger(null);
        ctx.plugin().ownership().detach();
    }
}
