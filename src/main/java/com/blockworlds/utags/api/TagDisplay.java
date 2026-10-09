package com.blockworlds.utags.api;

import net.kyori.adventure.text.Component;

/**
 * How a player's selected tags look, ready for a chat renderer: the prefix goes before the name, the suffix after it.
 * Either part is {@link Component#empty()} when nothing is selected. Image badges use the font {@code utags:badges},
 * so they show wherever the uTags pack (or a server pack that includes {@link UTagsApi#packAssets()}) is loaded.
 *
 * @param prefix the prefix part, never null
 * @param suffix the suffix part, never null
 */
public record TagDisplay(Component prefix, Component suffix) {
    public static final TagDisplay EMPTY = new TagDisplay(Component.empty(), Component.empty());
}
