package com.blockworlds.utags;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tag write paths against a real SQLite file with the real schema (whose CHECK on {@code type} is
 * case-sensitive): the insert statement addTagToDatabase uses, and the type edit of editTagAttribute.
 */
class SqliteTagWriteTest {

    @TempDir
    Path dir;

    private HikariDataSource open() throws Exception {
        HikariConfig config = new HikariConfig();
        com.blockworlds.utags.storage.Database.applySqlitePool(config, new File(dir.toFile(), "tags.db"));
        HikariDataSource ds = new HikariDataSource(config);
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            for (String sql : SqlDialect.SQLITE.createTags()) st.executeUpdate(sql);
        }
        return ds;
    }

    private static void insert(Connection c, Tag tag) throws Exception {
        try (PreparedStatement ps = com.blockworlds.utags.tags.TagCodec.prepareInsertTagStatement(c, tag, "NAME_TAG")) {
            ps.executeUpdate();
        }
    }

    private static boolean edit(Connection c, String tag, String attribute, String value) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("UPDATE tags SET " + attribute + " = ? WHERE name = ?")) {
            if (!com.blockworlds.utags.tags.TagCodec.bindAttributeValue(ps, attribute, value)) return false;
            ps.setString(2, tag);
            return ps.executeUpdate() > 0;
        }
    }

    private static String stored(Connection c, String tag) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT type FROM tags WHERE name = ?")) {
            ps.setString(1, tag);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    @Test
    void everyTypeCanBeInsertedAndIsStoredLowerCase() throws Exception {
        try (HikariDataSource ds = open(); Connection c = ds.getConnection()) {
            for (TagType type : TagType.values()) {
                String name = "t_" + type;
                insert(c, new Tag(name, "&a[X]", type, true, true, true, null, 5));
                assertEquals(type.name().toLowerCase(), stored(c, name));
                assertEquals(type, com.blockworlds.utags.tags.TagCodec.typeFromDb(stored(c, name)));
            }
        }
    }

    @Test
    void typeEditCyclesThroughAllThreeTypesInAnyCase() throws Exception {
        try (HikariDataSource ds = open(); Connection c = ds.getConnection()) {
            insert(c, new Tag("cyc", "&a[C]", TagType.PREFIX, true, true, true, null, 1));
            // the order the admin menu's Type button cycles in, passing the enum name as it does
            for (TagType next : new TagType[]{TagType.SUFFIX, TagType.BOTH, TagType.PREFIX}) {
                assertTrue(edit(c, "cyc", "type", next.name()));
                assertEquals(next, com.blockworlds.utags.tags.TagCodec.typeFromDb(stored(c, "cyc")));
            }
            assertTrue(edit(c, "cyc", "type", "Suffix"));
            assertEquals("suffix", stored(c, "cyc"));
            assertFalse(edit(c, "cyc", "type", "nonsense"), "an unknown type is refused, not stored");
            assertEquals("suffix", stored(c, "cyc"));
        }
    }

    @Test
    void approvingARequestSavesTheNewCustomTag() throws Exception {
        try (HikariDataSource ds = open(); Connection c = ds.getConnection()) {
            UUID owner = UUID.randomUUID();
            // the Tag acceptCustomTagRequest builds
            Tag tag = new Tag(CustomTags.tagName(owner, 1), "&a[Mine]", TagType.PREFIX, false, false, false, null, 1, owner, 1);
            insert(c, tag);
            assertEquals("prefix", stored(c, tag.getName()));
        }
    }

    @Test
    void typesReadBackInAnyCase() {
        assertEquals(TagType.BOTH, com.blockworlds.utags.tags.TagCodec.typeFromDb("both"));
        assertEquals(TagType.BOTH, com.blockworlds.utags.tags.TagCodec.typeFromDb("BOTH"));
        assertEquals(TagType.SUFFIX, com.blockworlds.utags.tags.TagCodec.typeFromDb(" Suffix "));
    }
}
