package com.blockworlds.utags.sync;

import com.blockworlds.utags.StorageType;
import com.blockworlds.utags.module.ChangeFeed;
import com.blockworlds.utags.module.Extensions;
import com.blockworlds.utags.module.UTagsContext;
import com.blockworlds.utags.uTags;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SyncModuleTest {

    @TempDir
    Path dir;

    private final List<String> logged = new ArrayList<>();

    private Logger logger() {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override public void publish(LogRecord r) { logged.add(r.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        return log;
    }

    private UTagsContext context(StorageType type, String yaml, ChangeFeed feed, CompletableFuture<Void> ready) throws Exception {
        uTags plugin = mock(uTags.class);
        Logger log = logger();
        when(plugin.getLogger()).thenReturn(log);
        when(plugin.getStorageType()).thenReturn(type);
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(yaml);
        when(plugin.getConfig()).thenReturn(cfg);
        when(plugin.getDataFolder()).thenReturn(dir.toFile());
        return new UTagsContext(plugin, new Extensions(), feed, ready);
    }

    @Test
    void sqliteTurnsTheModuleOffWithOneLine() throws Exception {
        ChangeFeed feed = new ChangeFeed(logger());
        CompletableFuture<Void> ready = new CompletableFuture<>();
        SyncModule module = new SyncModule();
        module.enable(context(StorageType.SQLITE, "", feed, ready));
        assertEquals(List.of("sync off (SQLite is single-server)"), logged);
        assertFalse(new File(dir.toFile(), "server-id").exists());
        // no publisher was installed: a change goes nowhere and does not fail
        feed.changed(ChangeFeed.Kind.TAGS, null);
        module.disable();
    }

    @Test
    void disabledInConfigDoesNothing() throws Exception {
        SyncModule module = new SyncModule();
        module.enable(context(StorageType.MYSQL, "sync:\n  enabled: false\n", new ChangeFeed(logger()), new CompletableFuture<>()));
        assertEquals(1, logged.size());
        assertTrue(logged.get(0).startsWith("sync off"));
    }

    @Test
    void theServerIdIsRandomAndKept() {
        File file = new File(dir.toFile(), "sub/server-id");
        String first = SyncModule.serverId(file, logger());
        assertEquals(first, UUID.fromString(first).toString());
        assertEquals(first, SyncModule.serverId(file, logger()));
        assertTrue(file.isFile());
    }
}
