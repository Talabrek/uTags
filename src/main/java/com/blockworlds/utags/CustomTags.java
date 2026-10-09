package com.blockworlds.utags;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.matcher.NodeMatcher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom tags (the ones a player requests for an unlocked slot) are owned by a UUID and a slot number.
 *
 * Until 1.1.0 a custom tag was named after its owner's name plus the slot ("Steve2"), and its permission was
 * utags.tag.Steve2. That breaks for Bedrock players, whose Floodgate names start with "." (a permission node
 * separator), and when a player renames. New custom tags are named {@code c<uuid>-<slot>}; the tags table keeps
 * owner_uuid and slot for both kinds. Legacy rows keep their name and permission, so nothing anyone owns changes:
 * {@link #backfillOwners} only records who owns them.
 */
public final class CustomTags {

    public static final String PERMISSION_PREFIX = "utags.tag.";
    /** The most custom tag slots a player can have (utags.custom1 .. utags.customN). */
    public static final int MAX_SLOTS = 9;

    private static final Pattern NEW_NAME = Pattern.compile(
            "c([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})-([1-9][0-9]?)");

    private CustomTags() {
    }

    /** The internal name of a new custom tag: {@code c<uuid>-<slot>}. */
    public static String tagName(UUID owner, int slot) {
        return "c" + owner.toString().toLowerCase(Locale.ROOT) + "-" + slot;
    }

    public static String permission(String tagName) {
        return PERMISSION_PREFIX + tagName;
    }

    /** Owner and slot of a new-style name, or null for any other name. */
    public static Owned parseNewName(String tagName) {
        if (tagName == null) return null;
        Matcher m = NEW_NAME.matcher(tagName.toLowerCase(Locale.ROOT));
        if (!m.matches()) return null;
        return new Owned(UUID.fromString(m.group(1)), Integer.parseInt(m.group(2)));
    }

    /**
     * The slot of a legacy custom tag named after {@code playerName} ("Steve" + "2"), or -1. Exact: "Steve2" is
     * slot 2 of Steve, but not a tag of "Stev" and not of "Steve2" (the old LIKE 'Steve%' count matched both).
     */
    public static int legacySlot(String tagName, String playerName) {
        if (tagName == null || playerName == null || playerName.isEmpty()) return -1;
        if (tagName.length() <= playerName.length()) return -1;
        if (!tagName.regionMatches(true, 0, playerName, 0, playerName.length())) return -1;
        String rest = tagName.substring(playerName.length());
        if (!rest.matches("[1-9][0-9]?")) return -1;
        int slot = Integer.parseInt(rest);
        return slot <= MAX_SLOTS ? slot : -1;
    }

    /** The lowest slot from 1 that holds no custom tag. */
    public static int nextFreeSlot(Map<Integer, Tag> owned) {
        int slot = 1;
        while (owned.containsKey(slot)) slot++;
        return slot;
    }

    /**
     * The custom tags a player owns, by slot. Rows with owner_uuid set are matched by UUID; rows from before 1.2.0
     * that haven't been backfilled yet are matched by the player's current name. Runs a query: call it off the main
     * thread where you can.
     */
    public static Map<Integer, Tag> ownedBy(com.blockworlds.utags.storage.Database db, UUID owner, String currentName) {
        try {
            return ownedByOrThrow(db, owner, currentName);
        } catch (SQLException e) {
            db.logger().severe("Could not load custom tags of " + owner + ": " + e.getMessage());
            return new TreeMap<>();
        }
    }

    /**
     * Like {@link #ownedBy}, but a failed query throws instead of looking like "owns nothing". Use it wherever the
     * result picks a slot to write (accepting a request): an empty map would hand out slot 1 again.
     */
    public static Map<Integer, Tag> ownedByOrThrow(com.blockworlds.utags.storage.Database db, UUID owner, String currentName) throws SQLException {
        Map<Integer, Tag> bySlot = new TreeMap<>();
        try (Connection c = db.connection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM tags WHERE owner_uuid = ?")) {
                ps.setString(1, owner.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Tag tag = com.blockworlds.utags.tags.TagCodec.fromRow(rs, db.logger());
                        if (tag.getSlot() != null) bySlot.putIfAbsent(tag.getSlot(), tag);
                    }
                }
            }
            if (currentName != null && !currentName.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT * FROM tags WHERE owner_uuid IS NULL AND name LIKE ? ESCAPE '!'")) {
                    ps.setString(1, likeEscape(currentName) + "%");
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Tag tag = com.blockworlds.utags.tags.TagCodec.fromRow(rs, db.logger());
                            int slot = legacySlot(tag.getName(), currentName);
                            // non-public only: see isOwnCustom
                            if (slot > 0 && !tag.isPublic()) bySlot.putIfAbsent(slot, tag);
                        }
                    }
                }
            }
        }
        return bySlot;
    }

    /** True when the tag is the one shown in one of the four custom slots of the prefix menu's top row. */
    public static boolean shownInTopRow(Tag tag, Map<Integer, Tag> ownedCustomTags) {
        for (Map.Entry<Integer, Tag> e : ownedCustomTags.entrySet()) {
            if (e.getKey() >= 1 && e.getKey() <= 4 && e.getValue().getName().equalsIgnoreCase(tag.getName())) return true;
        }
        return false;
    }

    /**
     * Could this row be a legacy custom tag? Accepted requests (every version since 2023) create non-public tags
     * with color = false whose name ends in the slot digit; /tag admin create always writes color = true, and so
     * does the wizard unless staff switch it off. A public tag is never a custom tag, even when its name is a
     * player's name plus a digit ("VIP2"). Not the colorable column: it was added with DEFAULT TRUE, so every
     * custom tag accepted before it existed (or by a pre-2025 jar, which doesn't write it) is colorable.
     */
    static boolean mayBeLegacyCustom(String name, boolean isPublic, boolean color) {
        return name != null && !name.isEmpty() && !isPublic && !color
                && Character.isDigit(name.charAt(name.length() - 1));
    }

    /**
     * True when the tag is this player's own custom tag: owned by their UUID, or (before the backfill has recorded an
     * owner) a non-public legacy tag named after their current name plus a slot. Custom tags are created non-public,
     * so a public tag that happens to be called "VIP2" is never taken for player VIP's slot 2.
     */
    public static boolean isOwnCustom(Tag tag, UUID player, String currentName) {
        if (tag.getOwnerUuid() != null) return tag.getOwnerUuid().equals(player);
        return !tag.isPublic() && legacySlot(tag.getName(), currentName) > 0;
    }

    /**
     * Records owner_uuid and slot on legacy custom tags. Every holder of a utags.tag.* permission whose LuckPerms
     * username plus a slot number equals the tag's name owns that tag; a non-public tag held by exactly one user
     * whose name ends in a digit is theirs too (they renamed since). Idempotent: rows that already have an owner
     * are skipped. Blocking; run it async.
     */
    public static int backfillOwners(com.blockworlds.utags.storage.Database db, LuckPerms luckPerms) {
        Logger log = db.logger();
        Map<UUID, Collection<Node>> holders;
        try {
            holders = luckPerms.getUserManager()
                    .searchAll(NodeMatcher.keyStartsWith(PERMISSION_PREFIX)).join();
        } catch (RuntimeException e) {
            log.warning("Custom tag owner backfill skipped: LuckPerms search failed: " + e.getMessage());
            return 0;
        }

        // tag name (lower case) -> holders
        Map<String, java.util.List<UUID>> byTag = new java.util.HashMap<>();
        holders.forEach((uuid, nodes) -> nodes.forEach(node -> {
            String tagName = node.getKey().substring(PERMISSION_PREFIX.length()).toLowerCase(Locale.ROOT);
            byTag.computeIfAbsent(tagName, k -> new java.util.ArrayList<>()).add(uuid);
        }));

        int updated = 0;
        try (Connection c = db.connection();
             PreparedStatement select = c.prepareStatement(
                     "SELECT name, public, color FROM tags WHERE owner_uuid IS NULL");
             PreparedStatement update = c.prepareStatement(
                     "UPDATE tags SET owner_uuid = ?, slot = ? WHERE name = ? AND owner_uuid IS NULL")) {
            java.util.List<String[]> candidates = new java.util.ArrayList<>();
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    candidates.add(new String[]{rs.getString("name"), String.valueOf(rs.getBoolean("public")),
                            String.valueOf(rs.getBoolean("color"))});
                }
            }
            for (String[] row : candidates) {
                String name = row[0];
                boolean isPublic = Boolean.parseBoolean(row[1]);
                boolean color = Boolean.parseBoolean(row[2]);
                Owned owned = parseNewName(name);
                // Staff-made and public tags are never custom tags; skipping them also saves a username lookup per
                // holder on every start (a public tag can have thousands of holders)
                if (owned == null && !mayBeLegacyCustom(name, isPublic, color)) continue;
                java.util.List<UUID> owners = byTag.get(name.toLowerCase(Locale.ROOT));
                if (owners == null || owners.isEmpty()) continue;

                if (owned == null) {
                    for (UUID uuid : owners) {
                        String username = luckPerms.getUserManager().lookupUsername(uuid).join();
                        int slot = legacySlot(name, username);
                        if (slot > 0) {
                            owned = new Owned(uuid, slot);
                            break;
                        }
                    }
                }
                if (owned == null && owners.size() == 1) {
                    int slot = name.charAt(name.length() - 1) - '0';
                    if (slot >= 1) owned = new Owned(owners.get(0), slot);
                }
                if (owned == null) continue;

                update.setString(1, owned.owner().toString());
                update.setInt(2, owned.slot());
                update.setString(3, name);
                if (update.executeUpdate() > 0) {
                    updated++;
                    log.info("Custom tag '" + name + "' belongs to " + owned.owner() + " (slot " + owned.slot() + ")");
                }
            }
        } catch (SQLException e) {
            log.severe("Custom tag owner backfill failed: " + e.getMessage());
        }
        return updated;
    }

    static String likeEscape(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    public record Owned(UUID owner, int slot) {
    }
}
