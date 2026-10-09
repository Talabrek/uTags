package com.blockworlds.utags.tags;

import com.blockworlds.utags.Tag;
import com.blockworlds.utags.TagType;
import com.blockworlds.utags.module.ChangeFeed;
import com.blockworlds.utags.storage.Database;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * Every defined tag, cached in memory. Reads ({@link #all}, {@link #byName}, ...) never touch the database, so the
 * main thread may call them. Writes run on the database executor, reload the cache, and complete their future on the
 * main thread. The cache is reloaded on start and every {@link #REFRESH_TICKS} so edits from another server show up.
 */
public final class TagRegistry {

    public static final long REFRESH_TICKS = 20L * 60;
    private static final Set<String> EDITABLE = Set.of("name", "display", "type", "public", "color", "colorable", "material", "weight");

    private final Database db;
    private final Logger log;
    private final Executor mainThread;
    private final ChangeFeed changes;
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private volatile List<Tag> cache = List.of();

    public TagRegistry(Database db, Executor mainThread, ChangeFeed changes) {
        this.db = db;
        this.log = db.logger();
        this.mainThread = mainThread;
        this.changes = changes;
        changes.subscribe(ChangeFeed.Kind.TAGS, who -> reload()); // another server changed a tag definition
    }

    /** Completes once the first load has finished (also when it failed, so waiters never hang). */
    public CompletableFuture<Void> ready() {
        return ready;
    }

    public CompletableFuture<Void> reload() {
        return db.supply(c -> {
            List<Tag> tags = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM tags ORDER BY weight DESC")) {
                while (rs.next()) tags.add(TagCodec.fromRow(rs, log));
            }
            return List.copyOf(tags);
        }).handle((tags, ex) -> {
            if (ex != null) log.severe("Could not load the tags: " + (ex.getCause() != null ? ex.getCause() : ex).getMessage());
            else cache = tags;
            ready.complete(null);
            return null;
        });
    }

    // --- reads (cache) ---

    public List<Tag> all() {
        return cache;
    }

    /** Tags that can be worn as the type (prefix or suffix, plus "both"); null means every tag. */
    public List<Tag> available(TagType type) {
        if (type == null) return cache;
        return cache.stream().filter(t -> t.getType() == type || t.getType() == TagType.BOTH).toList();
    }

    public Tag byName(String name) {
        if (name == null) return null;
        for (Tag t : cache) if (t.getName().equals(name)) return t;
        for (Tag t : cache) if (t.getName().equalsIgnoreCase(name)) return t; // MySQL and SQLite names are case-insensitive
        return null;
    }

    public Tag byDisplay(String display) {
        if (display == null) return null;
        for (Tag t : cache) if (t.getDisplay().equals(display)) return t;
        return null;
    }

    public CompletableFuture<String> nameByDisplayAsync(String display) {
        Tag t = byDisplay(display);
        return CompletableFuture.completedFuture(t == null ? null : t.getName());
    }

    public CompletableFuture<String> displayByNameAsync(String name) {
        Tag t = byName(name);
        return CompletableFuture.completedFuture(t == null ? null : t.getDisplay());
    }

    // --- writes ---

    /** Writes the tag (REPLACE); false and logged when the database refused it. Blocking: not for the main thread. */
    public boolean insertBlocking(Tag tag) {
        try (Connection c = db.connection(); PreparedStatement ps = TagCodec.prepareInsertTagStatement(c, tag, TagCodec.serializeMaterial(tag.getMaterial()))) {
            ps.executeUpdate();
            changes.changed(ChangeFeed.Kind.TAGS, null);
            return true;
        } catch (SQLException e) {
            log.severe("Could not save tag '" + tag.getName() + "': " + e.getMessage());
            return false;
        }
    }

    public CompletableFuture<Boolean> create(Tag tag) {
        // serialise the icon on the calling (main) thread: ItemStack is a Bukkit object
        String material = TagCodec.serializeMaterial(tag.getMaterial());
        return written(CompletableFuture.supplyAsync(() -> {
            try (Connection c = db.connection(); PreparedStatement ps = TagCodec.prepareInsertTagStatement(c, tag, material)) {
                ps.executeUpdate();
                changes.changed(ChangeFeed.Kind.TAGS, null);
                return true;
            } catch (SQLException e) {
                log.severe("Could not save tag '" + tag.getName() + "': " + e.getMessage());
                return false;
            }
        }, db.executor()));
    }

    public CompletableFuture<Boolean> edit(String tagName, String attribute, String newValue) {
        String attr = attribute.toLowerCase(Locale.ROOT);
        if (!EDITABLE.contains(attr)) return CompletableFuture.completedFuture(false).thenApplyAsync(b -> b, mainThread);
        return written(CompletableFuture.supplyAsync(() -> {
            try (Connection c = db.connection();
                 PreparedStatement exists = c.prepareStatement("SELECT 1 FROM tags WHERE name = ?")) {
                exists.setString(1, tagName);
                try (ResultSet rs = exists.executeQuery()) {
                    if (!rs.next()) return false;
                }
                // the attribute is one of the fixed names above, never user text
                try (PreparedStatement update = c.prepareStatement("UPDATE tags SET " + attr + " = ? WHERE name = ?")) {
                    if (!TagCodec.bindAttributeValue(update, attr, newValue)) {
                        log.warning("Invalid value for " + attribute + ": " + newValue);
                        return false;
                    }
                    update.setString(2, tagName);
                    update.executeUpdate();
                    changes.changed(ChangeFeed.Kind.TAGS, null);
                    return true;
                }
            } catch (SQLException e) {
                log.severe("Error updating tag attribute: " + e.getMessage());
                return false;
            }
        }, db.executor()));
    }

    public CompletableFuture<Void> delete(String tagName) {
        return written(CompletableFuture.supplyAsync(() -> {
            try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement("DELETE FROM tags WHERE name = ?")) {
                ps.setString(1, tagName);
                ps.executeUpdate();
                changes.changed(ChangeFeed.Kind.TAGS, null);
            } catch (SQLException e) {
                log.severe("Could not delete tag '" + tagName + "': " + e.getMessage());
            }
            return true;
        }, db.executor())).thenApply(b -> null);
    }

    /** Drops and recreates the tags table (/tag admin purge tags). */
    public CompletableFuture<Void> purge() {
        return written(CompletableFuture.supplyAsync(() -> {
            try (Connection c = db.connection(); Statement st = c.createStatement()) {
                st.executeUpdate("DROP TABLE IF EXISTS tags");
                for (String sql : db.dialect().recreateTags()) st.executeUpdate(sql);
                changes.changed(ChangeFeed.Kind.TAGS, null);
            } catch (SQLException e) {
                log.severe("Could not purge the tags table: " + e.getMessage());
            }
            return true;
        }, db.executor())).thenApply(b -> null);
    }

    /** After a write: reload the cache, then hand the result to the main thread. */
    private CompletableFuture<Boolean> written(CompletableFuture<Boolean> write) {
        return write.thenCompose(ok -> reload().thenApply(v -> ok)).thenApplyAsync(ok -> ok, mainThread);
    }

    /** Replaces icons that no longer deserialize with a name tag. Runs on the database executor. */
    public CompletableFuture<Void> cleanupInvalidMaterials() {
        return db.run(c -> {
            int repaired = 0;
            List<String[]> broken = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT name, material FROM tags")) {
                while (rs.next()) {
                    try (BukkitObjectInputStream in = new BukkitObjectInputStream(
                            new ByteArrayInputStream(Base64.getDecoder().decode(rs.getString("material"))))) {
                        in.readObject();
                    } catch (Exception e) {
                        log.warning("Error deserializing material for tag '" + rs.getString("name") + "': " + e.getMessage());
                        broken.add(new String[]{rs.getString("name")});
                    }
                }
            }
            String replacement = TagCodec.serializeMaterial(new ItemStack(Material.NAME_TAG));
            for (String[] row : broken) {
                try (PreparedStatement ps = c.prepareStatement("UPDATE tags SET material = ? WHERE name = ?")) {
                    ps.setString(1, replacement);
                    ps.setString(2, row[0]);
                    if (ps.executeUpdate() > 0) {
                        repaired++;
                        log.info("Repaired the icon of tag '" + row[0] + "'");
                    }
                }
            }
            if (repaired > 0) log.info("Repaired " + repaired + " invalid material entries.");
        });
    }
}
