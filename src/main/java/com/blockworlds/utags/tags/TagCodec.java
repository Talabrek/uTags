package com.blockworlds.utags.tags;

import com.blockworlds.utags.CustomTags;
import com.blockworlds.utags.Tag;
import com.blockworlds.utags.TagType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

/** Pure row/column conversions for the {@code tags} table (no plugin instance needed). */
public final class TagCodec {

    private TagCodec() {
    }

    /** The value stored in {@code tags.type}: lower case, which both dialects accept. */
    public static String typeToDb(TagType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    /** Reads a stored or typed tag type in any case; throws IllegalArgumentException when it is none of the three. */
    public static TagType typeFromDb(String value) {
        if (value == null) throw new IllegalArgumentException("null tag type");
        return TagType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public static PreparedStatement prepareInsertTagStatement(Connection connection, Tag tag, String serializedMaterial) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("REPLACE INTO tags (name, display, type, public, color, colorable, material, weight, owner_uuid, slot) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        statement.setString(1, tag.getName());
        statement.setString(2, tag.getDisplay());
        statement.setString(3, typeToDb(tag.getType()));
        statement.setBoolean(4, tag.isPublic());
        statement.setBoolean(5, tag.isColor());
        statement.setBoolean(6, tag.isColorable());
        statement.setString(7, serializedMaterial);
        statement.setInt(8, tag.getWeight());
        if (tag.getOwnerUuid() != null) statement.setString(9, tag.getOwnerUuid().toString());
        else statement.setNull(9, Types.VARCHAR);
        if (tag.getSlot() != null) statement.setInt(10, tag.getSlot());
        else statement.setNull(10, Types.INTEGER);
        return statement;
    }

    /** Binds the new value of a tag attribute as parameter 1 of its UPDATE; false when the value is invalid for it. */
    public static boolean bindAttributeValue(PreparedStatement statement, String attribute, String newValue) throws SQLException {
        switch (attribute.toLowerCase(Locale.ROOT)) {
            case "weight":
                try {
                    statement.setInt(1, Integer.parseInt(newValue));
                } catch (NumberFormatException e) {
                    return false;
                }
                return true;
            case "public":
            case "color":
            case "colorable":
                statement.setBoolean(1, Boolean.parseBoolean(newValue));
                return true;
            case "type":
                // Stored in the canonical lower case, as inserts do
                try {
                    statement.setString(1, typeToDb(typeFromDb(newValue)));
                } catch (IllegalArgumentException e) {
                    return false; // not PREFIX, SUFFIX or BOTH
                }
                return true;
            default:
                statement.setString(1, newValue);
                return true;
        }
    }

    public static String serializeMaterial(ItemStack material) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
                dataOutput.writeObject(material);
            }
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ItemStack deserializeMaterial(String base64Material, Logger log) {
        if (base64Material == null || base64Material.isEmpty()) {
            log.warning("Empty or null material data encountered, using default material.");
            return new ItemStack(Material.NAME_TAG);
        }
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(base64Material));
            try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
                return (ItemStack) dataInput.readObject();
            }
        } catch (Exception e) {
            log.warning("Error deserializing material: " + e.getMessage());
            return new ItemStack(Material.NAME_TAG);
        }
    }

    /** A Tag from the current row of a {@code SELECT * FROM tags} result. */
    public static Tag fromRow(ResultSet resultSet, Logger log) throws SQLException {
        String name = resultSet.getString("name");
        String display = resultSet.getString("display");

        String typeStr = resultSet.getString("type");
        TagType type;
        try {
            type = typeFromDb(typeStr);
        } catch (IllegalArgumentException e) {
            log.warning("Invalid tag type found in database: " + typeStr + ". Defaulting to PREFIX.");
            type = TagType.PREFIX;
        }

        boolean isPublic = resultSet.getBoolean("public");
        boolean color = resultSet.getBoolean("color");
        boolean colorable = resultSet.getBoolean("colorable");
        ItemStack material = deserializeMaterial(resultSet.getString("material"), log);
        int weight = resultSet.getInt("weight");

        // owner_uuid/slot exist from schema 5; a row read before the migration ran simply has no owner
        UUID ownerUuid = null;
        Integer slot = null;
        try {
            String owner = resultSet.getString("owner_uuid");
            if (owner != null && !owner.isEmpty()) ownerUuid = UUID.fromString(owner);
            int s = resultSet.getInt("slot");
            if (!resultSet.wasNull()) slot = s;
        } catch (SQLException | IllegalArgumentException ignored) {
            // column missing (older schema) or a malformed uuid: treat as unowned
        }
        if (ownerUuid == null) {
            CustomTags.Owned parsed = CustomTags.parseNewName(name);
            if (parsed != null) {
                ownerUuid = parsed.owner();
                slot = parsed.slot();
            }
        }
        return new Tag(name, display, type, isPublic, color, colorable, material, weight, ownerUuid, slot);
    }
}
