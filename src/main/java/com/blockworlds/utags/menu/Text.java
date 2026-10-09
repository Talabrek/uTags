package com.blockworlds.utags.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu text as Adventure Components. {@link #c} takes a string with legacy section codes (the menus build their
 * strings with ChatColor, as before) or {@code &} codes; {@link #mini} takes MiniMessage. Both leave Minecraft's
 * default italic on item names, exactly as the old {@code setDisplayName} did.
 */
public final class Text {

    // no extractUrls(): item text cannot be clicked, and it turned prices such as "100.00" into links
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('\u00a7').build();
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    /** Legacy text (section or ampersand codes) to a Component. */
    public static Component c(String legacy) {
        if (legacy == null) return Component.empty();
        return legacy.indexOf('\u00a7') >= 0 ? LEGACY.deserialize(legacy) : AMPERSAND.deserialize(legacy);
    }

    public static List<Component> cl(List<String> legacy) {
        List<Component> out = new ArrayList<>(legacy.size());
        for (String line : legacy) out.add(c(line));
        return out;
    }

    public static Component mini(String miniMessage) {
        return MiniMessage.miniMessage().deserialize(miniMessage);
    }
}
