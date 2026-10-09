package com.blockworlds.utags.impl;

import com.blockworlds.utags.Tag;
import com.blockworlds.utags.api.ActiveTags;
import com.blockworlds.utags.api.Edition;
import com.blockworlds.utags.api.GrantResult;
import com.blockworlds.utags.api.PackAssets;
import com.blockworlds.utags.api.RevokeResult;
import com.blockworlds.utags.api.TagDisplay;
import com.blockworlds.utags.api.TransferResult;
import com.blockworlds.utags.api.UTagsApi;
import com.blockworlds.utags.display.ColorPreferences;
import com.blockworlds.utags.display.DisplayService;
import com.blockworlds.utags.ledger.LedgerService;
import com.blockworlds.utags.ledger.Ownership;
import com.blockworlds.utags.selection.Selection;
import com.blockworlds.utags.selection.SelectionService;
import com.blockworlds.utags.selection.SelectionStore;
import com.blockworlds.utags.storage.Database;
import com.blockworlds.utags.tags.TagRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** The {@link UTagsApi} the free edition registers in the ServicesManager; the work lives in {@link LedgerService}. */
public final class UTagsApiImpl implements UTagsApi {

    private final Database db;
    private final TagRegistry tags;
    private final LedgerService ledger;
    private final Ownership ownership;
    private final SelectionService selection;
    private final ColorPreferences colors;
    private final CompletableFuture<Void> ready;
    private volatile Consumer<Player> menuOpener;
    private volatile java.util.function.Supplier<Edition> edition = () -> Edition.FREE;
    private volatile com.blockworlds.utags.module.Extensions extensions;
    private volatile java.util.function.Supplier<Optional<PackAssets>> packAssets = Optional::empty;

    /**
     * @param ready      completes once the schema exists and the ledger is attached; the service is registered before
     *                   that (so plugins find it in their onEnable), and calls that touch the database wait on it
     * @param menuOpener opens the tag menu for a player (main thread); the Bedrock part swaps in its form opener
     */
    public UTagsApiImpl(Database db, TagRegistry tags, LedgerService ledger, Ownership ownership,
                        SelectionService selection, ColorPreferences colors, CompletableFuture<Void> ready,
                        Consumer<Player> menuOpener) {
        this.db = db;
        this.tags = tags;
        this.ledger = ledger;
        this.ownership = ownership;
        this.selection = selection;
        this.colors = colors;
        this.ready = ready;
        this.menuOpener = menuOpener;
    }

    @Override
    public Edition edition() {
        return edition.get();
    }

    @Override
    public Set<String> tagIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (Tag tag : tags.all()) ids.add(tag.getName());
        return Set.copyOf(ids);
    }

    @Override
    public CompletableFuture<GrantResult> grant(UUID player, String tagId, String source, String opId) {
        ApiValidation.check(player, tagId, requireSource(source), opId);
        return ready.thenCompose(v -> ledger.grant(player, tagId, source, opId));
    }

    @Override
    public CompletableFuture<RevokeResult> revoke(UUID player, String tagId, String source, String opId) {
        ApiValidation.check(player, tagId, requireSource(source), opId);
        return ready.thenCompose(v -> ledger.revoke(player, tagId, source, opId));
    }

    private static String requireSource(String source) {
        if (source == null) throw new IllegalArgumentException("source is null");
        return source;
    }

    @Override
    public CompletableFuture<Set<String>> owned(UUID player) {
        return ready.thenCompose(v -> ledger.owned(player)).thenApply(Set::copyOf);
    }

    @Override
    public CompletableFuture<ActiveTags> active(UUID player) {
        return ready.thenCompose(v -> db.supply(c -> SelectionStore.find(c, player))).thenApply(UTagsApiImpl::toActive);
    }

    @Override
    public Set<String> ownedCached(UUID player) {
        Player online = Bukkit.getPlayer(player);
        if (online == null) return Set.of();
        Set<String> owned = new HashSet<>();
        for (Tag tag : tags.all()) {
            if (ownership.owns(online, tag.getName())) owned.add(tag.getName());
        }
        return Set.copyOf(owned);
    }

    @Override
    public ActiveTags activeCached(UUID player) {
        return Bukkit.getPlayer(player) == null ? ActiveTags.NONE : toActive(selection.cached(player));
    }

    @Override
    public CompletableFuture<TransferResult> transferAll(UUID from, UUID into, String opId) {
        ApiValidation.check(from, "-", null, opId);
        if (into == null) throw new IllegalArgumentException("into is null");
        if (from.equals(into)) throw new IllegalArgumentException("from and into are the same player");
        return ready.thenCompose(v -> ledger.transferAll(from, into, opId));
    }

    @Override
    public TagDisplay display(UUID player) {
        if (Bukkit.getPlayer(player) == null) return TagDisplay.EMPTY;
        Selection now = selection.cached(player);
        Tag prefix = tags.byName(now.prefixTag());
        Tag suffix = tags.byName(now.suffixTag());
        Component prefixPart = prefix == null ? Component.empty() : DisplayService.component(
                DisplayService.formatTagDisplayWithColor(prefix.getDisplay(), colors.tagPreference(player, prefix.getName())));
        Component suffixPart = suffix == null ? Component.empty() : DisplayService.component(suffix.getDisplay());
        com.blockworlds.utags.module.Extensions ext = extensions;
        if (ext != null) {
            if (prefix != null) prefixPart = ext.decorate(prefixPart, prefix, player, null, com.blockworlds.utags.spi.DisplayDecorator.Context.API);
            if (suffix != null) suffixPart = ext.decorate(suffixPart, suffix, player, null, com.blockworlds.utags.spi.DisplayDecorator.Context.API);
        }
        return new TagDisplay(prefixPart, suffixPart);
    }

    @Override
    public Optional<PackAssets> packAssets() {
        return packAssets.get();
    }

    /** Where {@link #packAssets()} comes from (uTags Pro's pack builder); empty in the free edition. */
    public void setPackAssets(java.util.function.Supplier<Optional<PackAssets>> packAssets) {
        this.packAssets = packAssets;
    }

    @Override
    public void openMenu(Player player) {
        menuOpener.accept(player);
    }

    /** Where {@link #edition()} comes from (the main class: PRO once a Pro module is enabled). */
    public void setEdition(java.util.function.Supplier<Edition> edition) {
        this.edition = edition;
    }

    /** The display decorators {@link #display} runs (context API, no single viewer). */
    public void setExtensions(com.blockworlds.utags.module.Extensions extensions) {
        this.extensions = extensions;
    }

    /** Lets another part (the Bedrock forms) take over {@link #openMenu}; it must fall back to the Java menu itself. */
    public void setMenuOpener(Consumer<Player> menuOpener) {
        this.menuOpener = menuOpener;
    }

    private static ActiveTags toActive(Selection selection) {
        return selection == null ? ActiveTags.NONE : new ActiveTags(selection.prefixTag(), selection.suffixTag());
    }
}
