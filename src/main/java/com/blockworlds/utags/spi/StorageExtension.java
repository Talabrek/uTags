package com.blockworlds.utags.spi;

import com.blockworlds.utags.storage.Migrations;

import java.util.List;

/**
 * Pro extension point: schema migrations of its own. Numbers are 100 and up (core uses 1-99) and may only touch
 * tables named {@code utags_pro_*}; the version lives in the same {@code utags_schema} row, so a Pro migration runs
 * once after the core ones. Every step must be safe to run twice and tolerate another server running it at the same
 * moment (check the live schema first; use {@link Migrations#execTolerating} for ALTERs). Extensions are registered
 * before the database is migrated, so they cannot depend on any loaded data.
 */
public interface StorageExtension {

    /** First number a Pro migration may use. */
    int FIRST_EXTENSION_VERSION = 100;

    List<Migrations.Migration> migrations();
}
