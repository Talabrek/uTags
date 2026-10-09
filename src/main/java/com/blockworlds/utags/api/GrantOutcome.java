package com.blockworlds.utags.api;

/** What a grant did. A replay of the same op id reports the original outcome (see {@link GrantResult#replayed()}). */
public enum GrantOutcome {
    /** The player did not own the tag before; a ledger row was written. */
    GRANTED,
    /** The player already owned the tag (from any source); a ledger row was still written for this source and op id. */
    ALREADY_OWNED,
    /** No tag with that id is defined; nothing was written. */
    UNKNOWN_TAG
}
