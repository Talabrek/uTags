package com.blockworlds.utags.api;

/** What a source-scoped revoke did. A replay of the same op id reports the original outcome. */
public enum RevokeOutcome {
    /** The source's rows were revoked and nothing else grants the tag any more (the selection resets if it was this tag). */
    REVOKED_GONE,
    /** The source's rows were revoked, but another source (or a permission) still grants the tag. */
    REVOKED_STILL_OWNED,
    /** That source had no unrevoked row for the tag; nothing changed. */
    NOT_HELD
}
