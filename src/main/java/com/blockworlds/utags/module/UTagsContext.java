package com.blockworlds.utags.module;

import com.blockworlds.utags.display.ColorPreferences;
import com.blockworlds.utags.display.DisplayService;
import com.blockworlds.utags.selection.SelectionService;
import com.blockworlds.utags.storage.Database;
import com.blockworlds.utags.tags.TagRegistry;
import com.blockworlds.utags.tags.TagRequests;
import com.blockworlds.utags.uTags;
import net.luckperms.api.LuckPerms;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * What a module may use: the services and the extension registry. The services are live objects but their data is
 * not loaded when {@link UTagsModule#enable} runs; wait on {@link #storageReady()} before using the database.
 */
public final class UTagsContext {

    private final uTags plugin;
    private final Extensions extensions;
    private final ChangeFeed changes;
    private final CompletableFuture<Void> storageReady;

    public UTagsContext(uTags plugin, Extensions extensions, ChangeFeed changes, CompletableFuture<Void> storageReady) {
        this.plugin = plugin;
        this.extensions = extensions;
        this.changes = changes;
        this.storageReady = storageReady;
    }

    public uTags plugin() { return plugin; }

    public Logger logger() { return plugin.getLogger(); }

    public Database database() { return plugin.database(); }

    public TagRegistry tags() { return plugin.tags(); }

    public TagRequests requests() { return plugin.requests(); }

    public SelectionService selection() { return plugin.selection(); }

    public DisplayService display() { return plugin.display(); }

    public ColorPreferences colors() { return plugin.colors(); }

    public LuckPerms luckPerms() { return plugin.getLuckPerms(); }

    public Extensions extensions() { return extensions; }

    /** Tell network sync what changed, and hear what other servers changed (see {@link ChangeFeed}). */
    public ChangeFeed changes() { return changes; }

    /** Completes (on the main thread) once the schema is migrated and the tag cache is loaded; fails if setup failed. */
    public CompletableFuture<Void> storageReady() { return storageReady; }
}
