package com.blockworlds.utags;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** plugin.yml declares no commands (Brigadier registers them) and declares every permission the commands check. */
class PluginYmlTest {

    @Test
    void declaresThePermissionsTheCommandsUseAndNoCommandsBlock() throws Exception {
        try (Reader reader = new InputStreamReader(getClass().getResourceAsStream("/plugin.yml"), StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
            assertFalse(yaml.contains("commands"), "commands are registered through Paper Brigadier");
            for (String permission : new String[]{"utags.tag", "utags.name.gui", "utags.command.namecolor", "utags.color.change"}) {
                // YamlConfiguration reads the dots of a key as nesting, so look the node up by its path
                assertTrue(yaml.contains("permissions." + permission + ".default"), "undeclared permission " + permission);
            }
            assertTrue(yaml.getStringList("authors").contains("Talabrek"));
        }
    }
}
