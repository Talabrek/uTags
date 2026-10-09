package com.blockworlds.utags.spi;

import com.blockworlds.utags.Tag;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Pro extension point: changes how a tag looks (a badge glyph in front, a gradient, a hover text) for one viewer in
 * one place. Decorators run in {@link #priority} order (low first), each on the previous one's result, on the thread
 * that asks for the display (chat: async; menus: main), so an implementation must be quick and must not touch the
 * database or Bukkit state that is main-thread only. Return the input unchanged to leave a tag alone.
 */
public interface DisplayDecorator {

    /** Where the tag is shown. */
    enum Context { CHAT, MENU, TAB_LIST, API }

    /**
     * @param tag    the current Component of the tag (already built from its display and the owner colours)
     * @param def    the tag definition
     * @param viewer who sees it; null when there is no single viewer (the API, console)
     */
    Component decorate(Component tag, Tag def, Player viewer, Context context);

    /** Lower runs first. */
    default int priority() {
        return 100;
    }
}
