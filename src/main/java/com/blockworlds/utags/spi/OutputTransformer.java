package com.blockworlds.utags.spi;

import com.blockworlds.utags.Tag;

import java.util.UUID;

/**
 * Pro extension point: changes the legacy text uTags writes into the LuckPerms prefix or suffix (what every chat plugin
 * reads), for example to fill in a live statistic. Runs on LuckPerms or database threads, so it must be quick and must
 * not touch main-thread-only Bukkit state (read values cached on the main thread instead). Transformers run in
 * registration order; return the text unchanged to leave it alone. To show a new value, the module calls
 * {@code SelectionService.applyStored(player)} on the main thread; uTags rewrites the node only when the text changed.
 */
public interface OutputTransformer {

    String transform(UUID player, Tag tag, boolean prefix, String legacyText);
}
