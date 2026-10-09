package com.blockworlds.utags.importer;

import com.blockworlds.utags.TagType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagImporterTest {

    private static TagImporter.Parsed parse(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return TagImporter.parse(config);
    }

    @Test
    void parsesTheHeritageExampleWithDefaults() throws Exception {
        var parsed = parse("""
                tags:
                  - { name: islander-2012, display: "&6[Islander since 2012]", type: suffix, weight: 2012 }
                  - { name: islander-2026, display: "&6[Islander since 2026]", type: suffix, weight: 2026, public: true, colorable: false }
                """);
        assertTrue(parsed.errors().isEmpty(), parsed.errors().toString());
        assertEquals(2, parsed.specs().size());
        var first = parsed.specs().get(0);
        assertEquals("islander-2012", first.name());
        assertEquals(TagType.SUFFIX, first.type());
        assertFalse(first.isPublic());
        assertTrue(first.colorable());
        assertEquals(2012, first.weight());
        assertEquals("NAME_TAG", first.material());
        var second = parsed.specs().get(1);
        assertTrue(second.isPublic());
        assertFalse(second.colorable());
    }

    @Test
    void badEntriesAreReportedAndTheRestIsKept() throws Exception {
        var parsed = parse("""
                tags:
                  - { name: ok, display: "x" }
                  - { display: "no name" }
                  - { name: ok, display: "dup" }
                  - { name: badtype, display: "x", type: middle }
                  - { name: badweight, display: "x", weight: heavy }
                """);
        assertEquals(1, parsed.specs().size());
        assertEquals(4, parsed.errors().size(), parsed.errors().toString());
    }

    @Test
    void anEmptyFileIsAnError() throws Exception {
        assertEquals(1, parse("other: 1\n").errors().size());
    }

    @Test
    void importFilesAreResolvedInsideTheImportFolderOnly() {
        File data = new File("data");
        assertNotNull(TagImporter.resolve(data, "heritage"));
        assertEquals(new File(new File(data, "import"), "heritage.yml"), TagImporter.resolve(data, "heritage.yml"));
        assertNull(TagImporter.resolve(data, "../config"));
        assertNull(TagImporter.resolve(data, "a/b"));
        assertNull(TagImporter.resolve(data, "..\\x"));
    }
}
