package com.blockworlds.utags.module;

import com.blockworlds.utags.api.Edition;

/**
 * A part of uTags that wires itself in (ledger, sync, Bedrock, unlocks, later Pro). The main class enables an
 * ordered list of modules, one line each; a module that throws in {@link #enable} is logged and skipped, it does not
 * stop the plugin. Modules are disabled in reverse order.
 */
public interface UTagsModule {

    /** Short stable name for logs and config. */
    String id();

    /** Called once, on the main thread, after the services exist but before the database is migrated. */
    void enable(UTagsContext ctx);

    default void disable() {
    }

    /** PRO for the modules of uTags Pro; any enabled PRO module makes {@code UTagsApi.edition()} answer PRO. */
    default Edition edition() {
        return Edition.FREE;
    }
}
