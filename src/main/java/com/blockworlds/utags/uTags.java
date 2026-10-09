package com.blockworlds.utags;

import com.blockworlds.utags.command.UTagsCommands;
import com.blockworlds.utags.display.ColorPreferences;
import com.blockworlds.utags.display.DisplayService;
import com.blockworlds.utags.menu.AdminMenuManager;
import com.blockworlds.utags.menu.MenuListener;
import com.blockworlds.utags.menu.MenuState;
import com.blockworlds.utags.menu.NameColorMenuManager;
import com.blockworlds.utags.menu.PreviewChatListener;
import com.blockworlds.utags.menu.RequestMenus;
import com.blockworlds.utags.menu.TagColorMenuManager;
import com.blockworlds.utags.menu.TagMenuManager;
import com.blockworlds.utags.module.ChangeFeed;
import com.blockworlds.utags.module.Extensions;
import com.blockworlds.utags.module.UTagsContext;
import com.blockworlds.utags.module.UTagsModule;
import com.blockworlds.utags.ledger.LedgerModule;
import com.blockworlds.utags.ledger.LedgerService;
import com.blockworlds.utags.ledger.Ownership;
import com.blockworlds.utags.selection.SelectionService;
import com.blockworlds.utags.storage.Database;
import com.blockworlds.utags.sync.SyncModule;
import com.blockworlds.utags.storage.Migrations;
import com.blockworlds.utags.tags.TagRegistry;
import com.blockworlds.utags.tags.TagRequests;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Wiring only: builds the services (storage, tags, selection, display), registers the listeners and commands, and
 * shuts down. The work lives in {@code storage}, {@code tags}, {@code selection} and {@code display}; menus and
 * commands call those services through the accessors below. No database call runs on the main thread.
 */
public class uTags extends JavaPlugin {

    private LuckPerms luckPerms;
    private Database database;
    private TagRegistry tags;
    private TagRequests requests;
    private ColorPreferences colors;
    private DisplayService display;
    private SelectionService selection;
    private RequestMenus requestMenus;
    private Ownership ownership;
    private volatile LedgerService ledger;

    private TagMenuManager tagMenuManager;
    private AdminMenuManager adminMenuManager;
    private TagColorMenuManager tagColorMenuManager;
    private NameColorMenuManager nameColorMenuManager;

    private final MenuState menuState = new MenuState();
    private final Extensions extensions = new Extensions();
    private ChangeFeed changes; // created on first use: the plugin logger is not set during field initialisation
    private final CompletableFuture<Void> storageReady = new CompletableFuture<>();
    private final List<UTagsModule> enabledModules = new ArrayList<>();
    private boolean debugModeEnabled = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            getLogger().warning("LuckPerms not found! Disabling uTags...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        luckPerms = LuckPermsProvider.get();
        try {
            database = Database.open(this);
        } catch (SQLException | RuntimeException e) {
            getLogger().severe("uTags is disabled: could not open the database ("
                    + e.getMessage() + "). Check storage.type and the database section in plugins/UTags/config.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        java.util.concurrent.Executor mainThread = r -> Bukkit.getScheduler().runTask(this, r);
        tags = new TagRegistry(database, mainThread, changes());
        requests = new TagRequests(this, database, tags, luckPerms);
        colors = new ColorPreferences(database, changes());
        display = new DisplayService(this, luckPerms);
        selection = new SelectionService(this, database, tags, colors, display, luckPerms, changes());
        requestMenus = new RequestMenus(this);
        ownership = new Ownership(luckPerms, getLogger());

        tagColorMenuManager = new TagColorMenuManager(this);
        nameColorMenuManager = new NameColorMenuManager(this);
        tagMenuManager = new TagMenuManager(this);
        adminMenuManager = new AdminMenuManager(this, tagMenuManager);
        registerCommandsAndEvents();
        enableModules();

        // Migrate, then load the caches, all off the main thread; the main thread only gets the final step.
        int legacySchema = getConfig().getInt("database.schema"); // where 1.3.0 kept the MySQL schema version
        database.run(c -> Migrations.run(c, database.dialect(), extensions.migrations(), legacySchema, getLogger()))
                .thenCompose(v -> tags.cleanupInvalidMaterials())
                .thenCompose(v -> tags.reload())
                .thenCompose(v -> colors.loadAllNameColors())
                .thenRunAsync(this::afterStorageReady, mainThread)
                .exceptionally(ex -> {
                    getLogger().severe("uTags is disabled: the database could not be set up (" + Database.reason(ex) + ").");
                    storageReady.completeExceptionally(ex);
                    Bukkit.getScheduler().runTask(this, () -> getServer().getPluginManager().disablePlugin(this));
                    return null;
                });
    }

    /**
     * The modules, in the order they are enabled: one line each (the 1b parts add theirs here). They are enabled
     * after the services exist and before the database is migrated, so a module can register its storage extension.
     */
    private List<UTagsModule> modules() {
        return List.of(
                new LedgerModule(),
                new SyncModule(),
                new com.blockworlds.utags.bedrock.BedrockModule(),
                new com.blockworlds.utags.unlock.UnlockModule());
    }

    private void enableModules() {
        UTagsContext ctx = new UTagsContext(this, extensions, changes(), storageReady);
        for (UTagsModule module : modules()) {
            try {
                module.enable(ctx);
                enabledModules.add(module);
            } catch (RuntimeException | LinkageError e) {
                getLogger().severe("Module '" + module.id() + "' failed to enable and is skipped: " + e);
            }
        }
    }

    private void afterStorageReady() {
        storageReady.complete(null);
        CompletableFuture.runAsync(() -> {
            int n = CustomTags.backfillOwners(database, luckPerms);
            if (n > 0) getLogger().info("Recorded the owners of " + n + " custom tags.");
        }, database.executor());
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, tags::reload, TagRegistry.REFRESH_TICKS, TagRegistry.REFRESH_TICKS);
        requests.startReminder();
        for (Player online : getServer().getOnlinePlayers()) selection.onJoin(online);
    }

    @Override
    public void onDisable() {
        for (int i = enabledModules.size() - 1; i >= 0; i--) {
            try {
                enabledModules.get(i).disable();
            } catch (RuntimeException e) {
                getLogger().warning("Module '" + enabledModules.get(i).id() + "' failed to disable: " + e);
            }
        }
        enabledModules.clear();
        if (database != null) {
            database.close();
            getLogger().info("Database connection pool closed.");
        }
    }

    private void registerCommandsAndEvents() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(adminMenuManager.clicks(), this); // the creation wizard's chat input
        pm.registerEvents(new PreviewChatListener(this), this);
        pm.registerEvents(new LoginListener(this), this);
        new UTagsCommands(this).register();
    }

    // --- services ---

    public LuckPerms getLuckPerms() { return luckPerms; }
    public Database database() { return database; }
    public TagRegistry tags() { return tags; }
    public TagRequests requests() { return requests; }
    public ColorPreferences colors() { return colors; }
    public DisplayService display() { return display; }
    public SelectionService selection() { return selection; }
    public RequestMenus requestMenus() { return requestMenus; }
    /** Who owns which tag: ledger grant or permission (menus and commands ask this, not hasPermission). */
    public Ownership ownership() { return ownership; }
    /** The grants ledger; null until the schema is ready. */
    public LedgerService ledger() { return ledger; }
    public void setLedger(LedgerService ledger) { this.ledger = ledger; }
    public Extensions extensions() { return extensions; }

    public synchronized ChangeFeed changes() {
        if (changes == null) changes = new ChangeFeed(getLogger());
        return changes;
    }

    public StorageType getStorageType() { return database.type(); }
    public SqlDialect getDialect() { return database.dialect(); }

    // --- menus ---

    public TagMenuManager getTagMenuManager() { return tagMenuManager; }
    public TagColorMenuManager getTagColorMenuManager() { return tagColorMenuManager; }
    public AdminMenuManager getAdminMenuManager() { return adminMenuManager; }
    public NameColorMenuManager getNameColorMenuManager() { return nameColorMenuManager; }

    /** The internal tag name stored on every tag item in the menus (the listener reads this, not the lore). */
    public NamespacedKey tagKey() { return new NamespacedKey(this, "tag"); }

    public NamespacedKey requestIdKey() { return new NamespacedKey(this, "request_id"); }

    public MenuState menuState() { return menuState; }

    // --- debug ---

    public boolean isDebugModeEnabled() { return debugModeEnabled; }

    public void setDebugModeEnabled(boolean enabled) {
        this.debugModeEnabled = enabled;
        getLogger().info(enabled ? "Debug mode enabled." : "Debug mode disabled.");
    }

    public void logDebug(String message) {
        if (debugModeEnabled) getLogger().info("[DEBUG] " + message);
    }
}
