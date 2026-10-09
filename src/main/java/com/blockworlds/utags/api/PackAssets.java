package com.blockworlds.utags.api;

import java.util.Map;

/**
 * The resource-pack files uTags Pro needs, for a server that builds one pack of its own instead of letting uTags send
 * a second pack (set {@code pack.send: false} in uTags' config then).
 *
 * <ul>
 *   <li>{@code javaFiles}: Java pack paths, all under {@code assets/utags/} (for example
 *       {@code assets/utags/font/badges.json} and its PNG sheets). uTags never writes into {@code minecraft:default}.</li>
 *   <li>{@code bedrockFiles}: Bedrock pack paths; only {@code font/glyph_E4.png} (code points U+E400-E4FF).</li>
 *   <li>{@code contentHash}: changes whenever any file changes; {@link UTagsPackChangedEvent} carries the new one.</li>
 * </ul>
 *
 * @param javaFiles    path to bytes, unmodifiable
 * @param bedrockFiles path to bytes, unmodifiable
 * @param contentHash  lower-case hex SHA-256 over all files
 */
public record PackAssets(Map<String, byte[]> javaFiles, Map<String, byte[]> bedrockFiles, String contentHash) {
}
