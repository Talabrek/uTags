package com.blockworlds.utags.sync;

import com.blockworlds.utags.StorageType;
import com.blockworlds.utags.module.UTagsContext;
import com.blockworlds.utags.module.UTagsModule;
import com.blockworlds.utags.storage.Database;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Logger;

/** Network sync of tags, selections and colours across servers sharing one MySQL database (config {@code sync.*}). */
public final class SyncModule implements UTagsModule {

    private SyncEngine engine;

    @Override
    public String id() {
        return "sync";
    }

    @Override
    public void enable(UTagsContext ctx) {
        Logger log = ctx.logger();
        if (ctx.plugin().getStorageType() == StorageType.SQLITE) {
            log.info("sync off (SQLite is single-server)");
            return;
        }
        FileConfiguration cfg = ctx.plugin().getConfig();
        if (!cfg.getBoolean("sync.enabled", true)) {
            log.info("sync off (sync.enabled is false)");
            return;
        }
        int pollSeconds = Math.max(1, cfg.getInt("sync.poll-seconds", 3));
        String serverId = serverId(new File(ctx.plugin().getDataFolder(), "server-id"), log);
        Database db = ctx.database();
        SyncEngine created = new SyncEngine(db::connection, serverId, ctx.changes(), log);
        engine = created;
        // the table exists once the migrations have run
        ctx.storageReady().thenAccept(v -> {
            created.start(pollSeconds);
            log.info("sync on: server " + serverId + ", polling every " + pollSeconds + " s");
        });
    }

    @Override
    public void disable() {
        if (engine != null) engine.close();
        engine = null;
    }

    /** The random id of this server, kept in a file so it survives restarts. */
    static String serverId(File file, Logger log) {
        try {
            if (file.isFile()) {
                String existing = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
                if (!existing.isEmpty()) return existing;
            }
            String created = UUID.randomUUID().toString();
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), created + "\n", StandardCharsets.UTF_8);
            return created;
        } catch (IOException e) {
            log.warning("Could not keep the sync server id in " + file + ": " + e.getMessage());
            return UUID.randomUUID().toString();
        }
    }
}
