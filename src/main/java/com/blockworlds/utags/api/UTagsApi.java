package com.blockworlds.utags.api;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * uTags' public API, registered in Bukkit's ServicesManager by both the free and the Pro edition.
 *
 * <h2>Ownership</h2>
 * A player owns a tag when they hold at least one unrevoked grant in the ledger, or the permission
 * {@code utags.tag.<tagId>}. This API only reads and writes ledger grants; permission-based tags are managed by the
 * permission plugin.
 *
 * <h2>Op ids and replays</h2>
 * Every changing call takes an op id. The first call with an op id does the work and records its outcome; any later
 * call with the same op id writes nothing and returns {@code replayed = true} with the original outcome. When a future
 * completes exceptionally the outcome is unknown: call again with the SAME op id, never a new one. The one exception
 * is an {@link IllegalArgumentException} (also as the cause of a failed future): a caller bug, such as an op id
 * already used for a different kind of call. Retrying it never succeeds. Op ids are case-sensitive.
 * Op ids: 1-100 characters of {@code [A-Za-z0-9_.:-]}. Sources: 1-64 characters of {@code [a-z0-9_.:-]}, for example
 * {@code tebex:<transactionId>} (refundable per purchase), {@code progress:<rewardId>}, {@code heritage}.
 *
 * <h2>Threads</h2>
 * Every method may be called from any thread except {@link #openMenu}. Futures complete off the main thread.
 * The {@code *Cached} methods and {@link #display} answer from memory for online players and never block.
 */
public interface UTagsApi {

    /** Semantic version of this API (not of the plugin). */
    String VERSION = "1.0.0";

    /** The installed uTags API, or empty when uTags is not loaded. */
    static Optional<UTagsApi> find() {
        return Optional.ofNullable(Bukkit.getServicesManager().load(UTagsApi.class));
    }

    Edition edition();

    /** Ids of every defined tag (custom player tags included). */
    Set<String> tagIds();

    /**
     * Grants a tag from a source. Writes one ledger row per new op id, even when the player already owns the tag, so a
     * later revoke of one source never removes a tag another source still grants.
     */
    CompletableFuture<GrantResult> grant(UUID player, String tagId, String source, String opId);

    /** Revokes every unrevoked ledger row of {@code tagId} with exactly this {@code source}; other sources are untouched. */
    CompletableFuture<RevokeResult> revoke(UUID player, String tagId, String source, String opId);

    /** Every tag the player owns: unrevoked ledger grants plus permission-based tags (read from storage). */
    CompletableFuture<Set<String>> owned(UUID player);

    /** The player's selected tags (read from storage). */
    CompletableFuture<ActiveTags> active(UUID player);

    /** {@link #owned} from memory for an online player; empty for an offline one. */
    Set<String> ownedCached(UUID player);

    /** {@link #active} from memory for an online player; {@link ActiveTags#NONE} for an offline one. */
    ActiveTags activeCached(UUID player);

    /**
     * Copies every unrevoked ledger grant of {@code from} into {@code into} (each copy keeps its source), as one
     * idempotent operation. Never creates a grant {@code from} did not hold and never changes {@code from}'s rows.
     * Keeps {@code into}'s selected tags; when {@code into} has none, it adopts {@code from}'s. Permission-based tags are
     * not copied. Use it when two accounts of one person merge (for example Java and Bedrock).
     */
    CompletableFuture<TransferResult> transferAll(UUID from, UUID into, String opId);

    /** The selected tags as Components for an online player; {@link TagDisplay#EMPTY} for an offline one. */
    TagDisplay display(UUID player);

    /** The Pro edition's pack files; empty in the free edition or when no badge images exist. */
    Optional<PackAssets> packAssets();

    /** Opens the tag menu (a Floodgate form for Bedrock players). Call on the main thread. */
    void openMenu(Player player);
}
