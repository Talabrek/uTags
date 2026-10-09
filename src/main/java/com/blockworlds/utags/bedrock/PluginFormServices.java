package com.blockworlds.utags.bedrock;

import com.blockworlds.utags.CustomTags;
import com.blockworlds.utags.PlayerTagColorPreference;
import com.blockworlds.utags.Tag;
import com.blockworlds.utags.TagType;
import com.blockworlds.utags.display.DisplayService;
import com.blockworlds.utags.uTags;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** {@link FormServices} on the live uTags services: the same calls the chest menus make. */
final class PluginFormServices implements FormServices {

    private final uTags plugin;

    PluginFormServices(uTags plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<Tag> tags(TagType type) {
        return plugin.tags().available(type).stream()
                .filter(t -> t.getType() == type || t.getType() == TagType.BOTH).toList();
    }

    @Override
    public Tag tag(String name) {
        return plugin.tags().byName(name);
    }

    @Override
    public boolean owns(Player player, String tagName) {
        return plugin.ownership().owns(player, tagName);
    }

    @Override
    public boolean canRecolor(Player player) {
        return player.hasPermission("utags.color.change");
    }

    @Override
    public boolean showLocked(UUID player) {
        return plugin.menuState().showAllPublicTags(player);
    }

    @Override
    public void toggleShowLocked(UUID player) {
        plugin.menuState().toggleShowAllPublicTags(player);
    }

    @Override
    public PlayerTagColorPreference preference(UUID player, String tagName) {
        return plugin.colors().tagPreference(player, tagName);
    }

    @Override
    public void ownedCustomTags(Player player, Consumer<Map<Integer, Tag>> then) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<Integer, Tag> owned = CustomTags.ownedBy(plugin.database(), uuid, name);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) then.accept(owned);
            });
        });
    }

    @Override
    public void applyTag(Player player, String tagName, TagType type) {
        Tag tag = plugin.tags().byName(tagName);
        if (tag == null) {
            player.sendMessage(ChatColor.RED + "Error: Tag data not found for ID: " + tagName);
            return;
        }
        PlayerTagColorPreference pref = plugin.colors().tagPreference(player.getUniqueId(), tagName);
        String shown = DisplayService.formatTagDisplayWithColor(tag.getDisplay(), pref);
        plugin.selection().select(player, tagName, type);
        player.sendMessage(ChatColor.GREEN + "Your " + type.name().toLowerCase() + " has been updated to: "
                + ChatColor.translateAlternateColorCodes('&', shown));
    }

    @Override
    public void removeTag(Player player, TagType type) {
        var cached = plugin.selection().cached(player.getUniqueId());
        boolean had = type == TagType.PREFIX ? cached.prefixTag() != null : cached.suffixTag() != null;
        if (!had) {
            player.sendMessage(ChatColor.YELLOW + "You did not have a " + type.name().toLowerCase() + " set.");
            return;
        }
        plugin.selection().clear(player, type).thenRunAsync(() ->
                        player.sendMessage(ChatColor.GREEN + "Your " + type.name().toLowerCase() + " has been removed."),
                r -> Bukkit.getScheduler().runTask(plugin, r));
    }

    @Override
    public void setTagColors(Player player, String tagName, ChatColor bracket, ChatColor content) {
        plugin.selection().setTagColor(player.getUniqueId(), tagName, bracket, content);
    }

    @Override
    public Map<String, String> nameColors(Player player) {
        Map<String, String> out = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("name-colors");
        if (section == null) return out;
        for (String key : section.getKeys(false)) {
            String code = section.getString(key);
            if (code == null || code.isEmpty() || ChatColor.getByChar(code.replace("&", "")) == null) continue;
            if (player.hasPermission("utags.namecolor." + key.toLowerCase().replace(" ", "_"))) out.put(key, code);
        }
        return out;
    }

    @Override
    public void setNameColor(Player player, String label, String code) {
        if (!player.hasPermission("utags.namecolor." + label.toLowerCase().replace(" ", "_"))) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this name color.");
            return;
        }
        plugin.selection().setNameColor(player.getUniqueId(), code);
        player.sendMessage(ChatColor.GREEN + "Your name color has been updated to "
                + ChatColor.translateAlternateColorCodes('&', code) + label + ChatColor.GREEN + "!");
    }

    @Override
    public void submitRequest(Player player, String display) {
        plugin.requests().submitAsync(player, display);
    }

    @Override
    public void tell(Player player, String legacyMessage) {
        player.sendMessage(legacyMessage);
    }
}
