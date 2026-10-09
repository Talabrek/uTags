package com.blockworlds.utags.sync;

import com.blockworlds.utags.StorageType;
import com.blockworlds.utags.TagType;
import com.blockworlds.utags.display.ColorPreferences;
import com.blockworlds.utags.module.ChangeFeed;
import com.blockworlds.utags.module.ChangeFeed.Kind;
import com.blockworlds.utags.selection.Selection;
import com.blockworlds.utags.selection.SelectionService;
import com.blockworlds.utags.selection.SelectionStore;
import com.blockworlds.utags.storage.Database;
import com.blockworlds.utags.storage.Migrations;
import com.blockworlds.utags.tags.TagCodec;
import com.blockworlds.utags.tags.TagRegistry;
import com.blockworlds.utags.Tag;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import net.luckperms.api.LuckPerms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The core services against a real SQLite file and a fake feed: each write calls {@code changed(...)} after the
 * commit, and a remote change makes the cache reload. The Bukkit scheduler does not exist here, so the player-facing
 * half (re-applying the LuckPerms output) is not covered.
 */
class ServicesSyncTest {

    @TempDir
    Path dir;

    private record Change(Kind kind, UUID player) {
    }

    private Database db;
    private final Logger log = Logger.getAnonymousLogger();
    private final ChangeFeed feed = new ChangeFeed(log);
    private final List<Change> published = new CopyOnWriteArrayList<>();
    /** What the database held at the moment each change was published (proves "after commit"). */
    private final List<String> snapshots = new CopyOnWriteArrayList<>();
    private TagRegistry tags;
    private ColorPreferences colors;

    @BeforeEach
    void setUp() throws Exception {
        HikariConfig config = new HikariConfig();
        Database.applySqlitePool(config, new File(dir.toFile(), "u.db"));
        db = Database.of(new HikariDataSource(config), StorageType.SQLITE, log);
        try (Connection c = db.connection()) {
            Migrations.run(c, db.dialect(), Migrations.core(), 0, log);
        }
        feed.setPublisher((kind, player) -> {
            published.add(new Change(kind, player));
            snapshots.add(snapshot());
        });
        tags = new TagRegistry(db, Runnable::run, feed);
        colors = new ColorPreferences(db, feed);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private String snapshot() {
        try (Connection c = db.connection(); Statement st = c.createStatement()) {
            StringBuilder sb = new StringBuilder();
            try (ResultSet rs = st.executeQuery("SELECT name FROM tags ORDER BY name")) {
                while (rs.next()) sb.append(rs.getString(1)).append(',');
            }
            sb.append('|');
            try (ResultSet rs = st.executeQuery("SELECT prefix_tag FROM utags_selection")) {
                while (rs.next()) sb.append(rs.getString(1));
            }
            sb.append('|');
            try (ResultSet rs = st.executeQuery("SELECT name_color_code FROM player_preferences")) {
                while (rs.next()) sb.append(rs.getString(1));
            }
            return sb.toString();
        } catch (Exception e) {
            return "error " + e;
        }
    }

    private void insertTag(String name) throws Exception {
        try (Connection c = db.connection(); PreparedStatement ps = TagCodec.prepareInsertTagStatement(
                c, new Tag(name, "&a[" + name + "]", TagType.BOTH, true, true, true, null, 1), "NAME_TAG")) {
            ps.executeUpdate();
        }
    }

    @Test
    void tagEditDeleteAndPurgeAnnounceTagsAfterTheCommit() throws Exception {
        insertTag("vip");
        assertTrue(tags.edit("vip", "weight", "9").get());
        assertEquals(new Change(Kind.TAGS, null), published.get(0));
        tags.delete("vip").get();
        assertEquals(new Change(Kind.TAGS, null), published.get(1));
        assertTrue(snapshots.get(1).startsWith("|"), "the tag was already deleted when the change was announced: " + snapshots.get(1));
        tags.purge().get();
        assertEquals(3, published.size());
    }

    @Test
    void aFailedEditAnnouncesNothing() throws Exception {
        assertEquals(false, tags.edit("nobody", "weight", "9").get());
        assertTrue(published.isEmpty());
    }

    @Test
    void colourWritesAnnounceColorsForThatPlayerAfterTheCommit() throws Exception {
        UUID p = UUID.randomUUID();
        assertTrue(colors.setNameColorCode(p, "&a").get());
        assertEquals(new Change(Kind.COLORS, p), published.get(0));
        assertTrue(snapshots.get(0).endsWith("|&a"), snapshots.get(0));
        colors.setTagColors(p, "vip", ChatColor.RED, ChatColor.BLUE).get();
        colors.setTagColors(p, "vip", null, null).get(); // reset
        assertEquals(List.of(new Change(Kind.COLORS, p), new Change(Kind.COLORS, p), new Change(Kind.COLORS, p)), published);
    }

    @Test
    void aRemoteColourChangeReloadsTheCaches() throws Exception {
        UUID p = UUID.randomUUID();
        try (Connection c = db.connection(); Statement st = c.createStatement()) {
            st.executeUpdate("REPLACE INTO player_preferences (player_uuid, name_color_code) VALUES ('" + p + "', '&c')");
            st.executeUpdate("INSERT INTO player_tag_color_preferences (player_uuid, tag_name, bracket_color_code, content_color_code) VALUES ('" + p + "', 'vip', '&1', NULL)");
        }
        colors.reloadPlayer(p, true).get();
        assertEquals("&c", colors.nameColorCode(p));
        assertEquals(ChatColor.DARK_BLUE, colors.tagPreference(p, "vip").getBracketColor());
        try (Connection c = db.connection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM player_preferences");
            st.executeUpdate("DELETE FROM player_tag_color_preferences");
        }
        colors.reloadPlayer(p, true).get();
        assertNull(colors.nameColorCode(p));
        assertNull(colors.tagPreference(p, "vip").getBracketColor());
    }

    @Test
    void aRemoteTagsChangeReloadsTheRegistry() throws Exception {
        // Tag rows need Bukkit to deserialise, so prove the reload by making it fail: with the table gone it logs
        List<String> logged = new CopyOnWriteArrayList<>();
        log.setUseParentHandlers(false);
        log.addHandler(new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord r) { logged.add(r.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        try (Connection c = db.connection(); Statement st = c.createStatement()) {
            st.executeUpdate("DROP TABLE tags");
        }
        feed.deliverRemote(Kind.TAGS, null);
        long end = System.currentTimeMillis() + 3000;
        while (logged.stream().noneMatch(m -> m.startsWith("Could not load the tags")) && System.currentTimeMillis() < end) Thread.sleep(20);
        assertTrue(logged.stream().anyMatch(m -> m.startsWith("Could not load the tags")), logged.toString());
    }

    private SelectionService selectionService() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(log);
        return new SelectionService(plugin, db, tags, colors, null, mock(LuckPerms.class), feed);
    }

    @Test
    void clearingASelectionAnnouncesSelectionAfterTheCommit() throws Exception {
        UUID p = UUID.randomUUID();
        try (Connection c = db.connection()) {
            SelectionStore.set(c, p, true, "vip");
        }
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(p);
        selectionService().clear(player, TagType.PREFIX).handle((v, ex) -> null).get(); // LuckPerms is a mock: the tail fails, the write is done
        assertEquals(List.of(new Change(Kind.SELECTION, p)), published);
        assertEquals("|null|", snapshots.get(0));
        try (Connection c = db.connection()) {
            assertNull(SelectionStore.find(c, p).prefixTag());
        }
    }

    @Test
    void aRemoteSelectionChangeReloadsTheCacheKeepingLocalPicks() throws Exception {
        UUID p = UUID.randomUUID();
        SelectionService service = selectionService();
        try (Connection c = db.connection()) {
            SelectionStore.set(c, p, true, "remote-prefix");
        }
        Selection loaded = service.reloadSelection(p).get();
        assertEquals("remote-prefix", loaded.prefixTag());
        assertEquals("remote-prefix", service.cached(p).prefixTag());
    }
}
