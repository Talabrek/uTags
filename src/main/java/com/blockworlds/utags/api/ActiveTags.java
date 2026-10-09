package com.blockworlds.utags.api;

/**
 * The tags a player has selected: one prefix tag and one suffix tag, each null when none is selected.
 *
 * @param prefixTagId the selected prefix tag's id, or null
 * @param suffixTagId the selected suffix tag's id, or null
 */
public record ActiveTags(String prefixTagId, String suffixTagId) {
    public static final ActiveTags NONE = new ActiveTags(null, null);
}
