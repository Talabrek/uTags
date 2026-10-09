package com.blockworlds.utags.selection;

import java.util.function.Function;

/** The tag names a player wears (either may be null). The database row is the truth; LuckPerms only gets the output. */
public record Selection(String prefixTag, String suffixTag) {

    public static final Selection NONE = new Selection(null, null);

    /** What the LuckPerms prefix and suffix nodes at priority 10000 held (raw values; null when there is no node). */
    public record LuckPermsValues(String prefix, String suffix) {
    }

    /**
     * The one-time upgrade from 1.2/1.3: which tags the LuckPerms nodes at priority 10000 stand for. The prefix
     * value is the tag display plus a trailing colour code (the name colour), so a trailing {@code §x} / {@code &x}
     * is cut off before the display is matched, as the 1.3.0 login code did. The suffix is the display itself.
     *
     * @param tagNameByDisplay the tag name whose display equals the text, or null
     */
    public static Selection deriveFromLuckPerms(LuckPermsValues lp, Function<String, String> tagNameByDisplay) {
        String prefixTag = null;
        String current = lp == null ? null : lp.prefix();
        if (current != null && current.length() > 2) {
            String display = current;
            char section = current.charAt(current.length() - 2);
            char code = current.charAt(current.length() - 1);
            if ((section == '§' || section == '&') && "0123456789abcdefABCDEFkKlLmMnNoOrR".indexOf(code) != -1) {
                display = current.substring(0, current.length() - 2);
            }
            prefixTag = tagNameByDisplay.apply(display);
        }
        String suffixTag = null;
        if (lp != null && lp.suffix() != null && !lp.suffix().isEmpty()) {
            suffixTag = tagNameByDisplay.apply(lp.suffix());
        }
        return new Selection(prefixTag, suffixTag);
    }
}
