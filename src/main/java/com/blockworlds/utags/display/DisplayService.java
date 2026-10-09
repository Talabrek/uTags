package com.blockworlds.utags.display;

import com.blockworlds.utags.PlayerTagColorPreference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * How tags look. Text is MiniMessage internally; legacy {@code &} / {@code §} codes are accepted wherever an admin
 * or player types a tag. The LuckPerms prefix is produced in legacy form for chat plugins, exactly as 1.3.0 did,
 * so chat output does not change.
 */
public final class DisplayService {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer AMPERSAND_HEX = LegacyComponentSerializer.builder()
            .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final Logger FALLBACK_LOG = Logger.getLogger("UTags");

    private final JavaPlugin plugin;
    private final LuckPerms luckPerms;

    public DisplayService(JavaPlugin plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
    }

    // --- text (pure) ---

    /** True when the text uses legacy codes ({@code &x} or {@code §x}). */
    static boolean isLegacy(String input) {
        for (int i = 0; i + 1 < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '&' || c == ChatColor.COLOR_CHAR) && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(input.charAt(i + 1)) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** A tag's text as a Component: legacy input through Adventure's legacy serializer, anything else as MiniMessage. */
    public static Component component(String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        if (isLegacy(input)) return AMPERSAND.deserialize(input.replace(ChatColor.COLOR_CHAR, '&'));
        return MINI.deserialize(input);
    }

    /**
     * The tag text in legacy {@code &} form. Legacy input is returned untouched (that is what 1.3.0 stored in
     * LuckPerms); MiniMessage input is serialised to the same form.
     */
    public static String toLegacy(String input) {
        if (input == null || input.isEmpty() || isLegacy(input) || input.indexOf('<') < 0) return input;
        return AMPERSAND_HEX.serialize(MINI.deserialize(input));
    }

    /** The LuckPerms prefix value: the tag text with the player's colours, then the name colour code. */
    public static String lpPrefix(String tagDisplay, PlayerTagColorPreference pref, String nameColorCode) {
        return formatTagDisplayWithColor(tagDisplay, pref) + nameColorCode;
    }

    /**
     * Applies a player's bracket and text colours to a display such as {@code &a[VIP]}.
     * Returns the (legacy form of the) original when the player has set no colours.
     */
    public static String formatTagDisplayWithColor(String originalDisplay, PlayerTagColorPreference preference) {
        originalDisplay = toLegacy(originalDisplay);
        if (preference == null || preference.isDefault()) {
            return originalDisplay; // No custom colors set
        }

        ChatColor bracketColor = preference.getBracketColor();
        ChatColor contentColor = preference.getContentColor();

        int openBracketIndex = originalDisplay.indexOf('[');
        int closeBracketIndex = originalDisplay.indexOf(']');
        if (openBracketIndex == -1 || closeBracketIndex == -1 || openBracketIndex >= closeBracketIndex) {
            FALLBACK_LOG.warning("Could not parse brackets in tag display: " + originalDisplay);
            return originalDisplay;
        }

        String prefix = originalDisplay.substring(0, openBracketIndex); // Colors before the opening bracket
        String content = originalDisplay.substring(openBracketIndex + 1, closeBracketIndex);
        String suffix = originalDisplay.substring(closeBracketIndex + 1); // Text/colors after the closing bracket

        StringBuilder formatted = new StringBuilder();
        formatted.append(prefix);

        if (bracketColor != null) {
            formatted.append(bracketColor).append('[');
        } else {
            // Need to re-apply prefix colors if bracket color is default
            formatted.append(ChatColor.getLastColors(prefix)).append('[');
        }

        if (contentColor != null) {
            formatted.append(contentColor).append(content);
        } else {
            String lastColorInside = ChatColor.getLastColors(content);
            if (!lastColorInside.isEmpty()) {
                formatted.append(lastColorInside).append(content);
            } else {
                // Fallback: the color active just before the content (bracket color, or the prefix's)
                String colorBeforeContent = ChatColor.getLastColors(prefix);
                if (bracketColor != null) colorBeforeContent = bracketColor.toString();
                formatted.append(colorBeforeContent).append(content);
            }
        }

        if (bracketColor != null) {
            formatted.append(bracketColor).append(']');
        } else {
            formatted.append(ChatColor.getLastColors(prefix)).append(']');
        }

        formatted.append(suffix);
        return formatted.toString();
    }

    // --- messages ---

    /** A message from config.yml ({@code messages.<key>}) with colour codes translated. */
    public String message(String key) {
        String message = plugin.getConfig().getString("messages." + key);
        if (message == null) {
            plugin.getLogger().warning("Missing message key in config.yml: messages." + key);
            return ChatColor.RED + "Error: Missing message for " + key;
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    // --- display name ---

    /**
     * Rebuilds the player's display name and tab-list name from their LuckPerms prefix and suffix. Call on the main
     * thread; the user data is reloaded in the background and the names are set back on the main thread.
     */
    public void refreshDisplayName(Player player) {
        if (player == null || !player.isOnline()) return;
        luckPerms.getUserManager().loadUser(player.getUniqueId()).thenAcceptAsync(user -> {
            if (user == null || !player.isOnline()) return;
            net.luckperms.api.cacheddata.CachedMetaData meta = user.getCachedData().getMetaData();
            String rawPrefix = meta.getPrefix() == null ? "" : meta.getPrefix();
            String suffix = meta.getSuffix() == null ? "" : meta.getSuffix();
            String prefix = rawPrefix.isEmpty() ? "" : rawPrefix + " ";
            String name = prefix + player.getName() + suffix + ChatColor.RESET;
            Component display = component(name);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                player.displayName(display);
                player.playerListName(display);
            });
        }, r -> Bukkit.getScheduler().runTask(plugin, r)).exceptionally(ex -> {
            plugin.getLogger().severe("Error refreshing the display name of " + player.getName() + ": " + ex.getMessage());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.displayName(Component.text(player.getName()));
                    player.playerListName(Component.text(player.getName()));
                }
            });
            return null;
        });
    }
}
