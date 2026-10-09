package com.blockworlds.utags.spi;

/**
 * Pro extension point: files for the generated resource pack (fonts, glyph textures, language files). uTags Pro owns
 * the glyph range U+E400-E4FF and the font {@code utags:badges}; a contributor writes nothing under
 * {@code minecraft:default} and nothing in glyph_E0-E3. Called off the main thread whenever the pack is rebuilt.
 */
public interface PackContributor {

    /** Where the files go; paths are pack-relative, for example {@code assets/utags/font/badges.json}. */
    interface Files {
        /** Adds a file; a second file at the same path fails with IllegalStateException. */
        void add(String path, byte[] content);
    }

    /** A short stable name for logs. */
    String id();

    void contribute(Files files);
}
