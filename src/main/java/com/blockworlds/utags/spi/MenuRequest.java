package com.blockworlds.utags.spi;

import com.blockworlds.utags.Tag;
import com.blockworlds.utags.TagType;

/** A player menu uTags is about to open as a chest menu; a {@link MenuOpenerOverride} may open something else instead. */
public sealed interface MenuRequest {

    /** The main tag menu (the entry point of /tag and the API). */
    record Main() implements MenuRequest {
    }

    /** The prefix or suffix picker at a page. */
    record Selection(TagType type, int page) implements MenuRequest {
    }

    /** Use / change colours for a colourable tag; {@code display} is the tag text with the player's colours. */
    record Options(Tag tag, String display, TagType type, int returnPage) implements MenuRequest {
    }

    /** The tag colour picker; {@code editingBrackets} says whether the bracket or the content colour is being set. */
    record TagColors(Tag tag, boolean editingBrackets) implements MenuRequest {
    }

    /** The name colour picker. */
    record NameColors() implements MenuRequest {
    }

    /** Confirmation of a custom tag request. */
    record RequestConfirm(String display) implements MenuRequest {
    }
}
