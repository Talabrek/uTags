package com.blockworlds.utags.spi;

import com.blockworlds.utags.TagType;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * Pro extension point: adds buttons to the tag selection menu. A button is a slot, an item and one single click
 * action (no right or shift click: Bedrock players cannot do those). Buttons may use the top row (0-8, apart from the
 * custom tag slots 1, 3, 5, 7 of the prefix menu) or the bottom row slots uTags leaves free (46, 52); a slot uTags
 * already fills is ignored. A button that opens a page of its own builds that page as a {@code MenuHolder} subclass
 * (package {@code menu}), so it gets the same click cancelling and routing. Both methods run on the main thread.
 */
public interface MenuExtension {

    /** One button: a slot (0-53), its item, and what a click does (main thread; the menu stays open until it closes it). */
    record Button(int slot, ItemStack item, Consumer<Player> onClick) {
    }

    /** A named page an extension offers; the tag menu shows {@code icon} in the next free button slot. */
    record Page(String id, Component title, ItemStack icon, Consumer<Player> open) {
    }

    /** Buttons to add to the prefix or suffix selection menu the viewer is about to see. */
    default List<Button> buttons(Player viewer, TagType type) {
        return List.of();
    }

    /** Pages to link from the tag menu. */
    default List<Page> pages(Player viewer, TagType type) {
        return List.of();
    }
}
