package com.blockworlds.utags.bedrock;

import com.blockworlds.utags.PlayerTagColorPreference;
import com.blockworlds.utags.Tag;
import com.blockworlds.utags.TagType;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * What the forms need from uTags: the same services the chest menus use, behind one interface so tests can fake them.
 * Every method runs on the main thread and never blocks; database work happens inside the services, off it.
 */
public interface FormServices {

    /** Tags of this type (and BOTH), from the registry cache. */
    List<Tag> tags(TagType type);

    Tag tag(String name);

    /** True when the player may use the tag (permission {@code utags.tag.<name>}). */
    boolean owns(Player player, String tagName);

    /** True when the player may change tag colours ({@code utags.color.change}). */
    boolean canRecolor(Player player);

    boolean showLocked(UUID player);

    void toggleShowLocked(UUID player);

    PlayerTagColorPreference preference(UUID player, String tagName);

    /** The player's own custom tags by slot, loaded off the main thread; {@code then} runs on the main thread. */
    void ownedCustomTags(Player player, Consumer<Map<Integer, Tag>> then);

    /** Makes the tag the prefix or suffix and tells the player. */
    void applyTag(Player player, String tagName, TagType type);

    void removeTag(Player player, TagType type);

    void setTagColors(Player player, String tagName, ChatColor bracket, ChatColor content);

    /** Name colours the player may use: label to colour code (for example {@code Red} to {@code &c}), in menu order. */
    Map<String, String> nameColors(Player player);

    void setNameColor(Player player, String label, String code);

    void submitRequest(Player player, String display);

    void tell(Player player, String legacyMessage);
}
