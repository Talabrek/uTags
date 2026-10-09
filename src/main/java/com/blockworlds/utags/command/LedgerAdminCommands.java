package com.blockworlds.utags.command;

import com.blockworlds.utags.Tag;
import com.blockworlds.utags.api.UTagsApi;
import com.blockworlds.utags.importer.TagImporter;
import com.blockworlds.utags.ledger.LedgerService;
import com.blockworlds.utags.uTags;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * {@code /tag admin grant|revoke|grants|import}: the ledger commands for stores and staff, usable from the console.
 * Grant and revoke go through the public {@link UTagsApi} (validation, replays, events) like any other plugin would.
 */
final class LedgerAdminCommands {

    private final uTags plugin;

    LedgerAdminCommands(uTags plugin) {
        this.plugin = plugin;
    }

    void addTo(LiteralArgumentBuilder<CommandSourceStack> admin) {
        admin.then(Commands.literal("grant").then(change("grant")));
        admin.then(Commands.literal("revoke").then(change("revoke")));
        admin.then(Commands.literal("grants").then(Commands.argument("player", new Token())
                .executes(ctx -> run(ctx, this::list))));
        admin.then(Commands.literal("import").then(Commands.argument("file", StringArgumentType.word())
                .executes(ctx -> run(ctx, this::importFile))));
    }

    /** {@code <player> <tag> <source> <opId>}, the same for grant and revoke. */
    private RequiredArgumentBuilder<CommandSourceStack, String> change(String verb) {
        // Token, not word(): Floodgate names start with a prefix such as "*" or ".", which word() refuses
        return Commands.argument("player", new Token())
                .then(Commands.argument("tag", new Token())
                        .suggests((ctx, b) -> {
                            String typed = b.getRemainingLowerCase();
                            if (plugin != null) {
                                for (Tag t : plugin.tags().all()) {
                                    if (t.getName().toLowerCase().startsWith(typed)) b.suggest(t.getName());
                                }
                            }
                            return b.buildFuture();
                        })
                        .then(Commands.argument("source", new Token())
                                .then(Commands.argument("opId", new Token())
                                        .executes(ctx -> run(ctx, (sender, c) -> change(sender, c, verb))))));
    }

    /**
     * Everything up to the next space. Brigadier's word() stops at a colon, and sources look like {@code tebex:<txn>};
     * the API validates the format. Clients see a plain word argument.
     */
    static final class Token implements CustomArgumentType<String, String> {
        private static final SimpleCommandExceptionType EMPTY = new SimpleCommandExceptionType(new LiteralMessage("Expected a value"));

        @Override
        public String parse(StringReader reader) throws CommandSyntaxException {
            int start = reader.getCursor();
            while (reader.canRead() && reader.peek() != ' ') reader.skip();
            if (reader.getCursor() == start) throw EMPTY.createWithContext(reader);
            return reader.getString().substring(start, reader.getCursor());
        }

        @Override
        public ArgumentType<String> getNativeType() {
            return StringArgumentType.word();
        }
    }

    private static int run(CommandContext<CommandSourceStack> ctx, BiConsumer<CommandSender, CommandContext<CommandSourceStack>> action) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission("utags.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return 1;
        }
        action.accept(sender, ctx);
        return 1;
    }

    private void change(CommandSender sender, CommandContext<CommandSourceStack> ctx, String verb) {
        UUID player = resolve(sender, StringArgumentType.getString(ctx, "player"));
        UTagsApi api = api(sender);
        if (player == null || api == null) return;
        String tag = StringArgumentType.getString(ctx, "tag");
        String source = StringArgumentType.getString(ctx, "source");
        String opId = StringArgumentType.getString(ctx, "opId");
        try {
            if (verb.equals("grant")) {
                api.grant(player, tag, source, opId).whenComplete((r, ex) -> reply(sender, ex,
                        r == null ? null : "grant " + tag + " to " + player + ": " + r.outcome() + ", replayed=" + r.replayed()));
            } else {
                api.revoke(player, tag, source, opId).whenComplete((r, ex) -> reply(sender, ex,
                        r == null ? null : "revoke " + tag + " from " + player + ": " + r.outcome() + ", replayed=" + r.replayed()));
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + e.getMessage());
        }
    }

    private void list(CommandSender sender, CommandContext<CommandSourceStack> ctx) {
        UUID player = resolve(sender, StringArgumentType.getString(ctx, "player"));
        LedgerService ledger = plugin.ledger();
        if (player == null) return;
        if (ledger == null) {
            sender.sendMessage(ChatColor.RED + "The ledger is not available yet.");
            return;
        }
        ledger.rows(player).whenComplete((rows, ex) -> {
            if (ex != null) {
                reply(sender, ex, null);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(ChatColor.GOLD + "Grants of " + player + " (" + rows.size() + "):");
                for (var row : rows) {
                    sender.sendMessage(ChatColor.GRAY + row.tagId() + " source=" + row.source() + " op=" + row.opId()
                            + (row.revokedAt() == null ? " active" : " revoked by " + row.revokeOpId())
                            + (row.copiedFromOp() == null ? "" : " copied from " + row.copiedFromOp()));
                }
            });
        });
    }

    private void importFile(CommandSender sender, CommandContext<CommandSourceStack> ctx) {
        File file = TagImporter.resolve(plugin.getDataFolder(), StringArgumentType.getString(ctx, "file"));
        if (file == null || !file.isFile()) {
            sender.sendMessage(ChatColor.RED + "No such file in plugins/UTags/import/ (use a plain name, without a folder).");
            return;
        }
        new TagImporter(plugin.tags()).run(file).whenComplete((report, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null) {
                sender.sendMessage(ChatColor.RED + "Import failed: " + ex.getMessage());
                return;
            }
            sender.sendMessage(ChatColor.GREEN + "Import " + file.getName() + ": created " + report.created() + ", updated "
                    + report.updated() + ", skipped " + report.skipped() + " (unchanged).");
            for (String error : report.errors()) sender.sendMessage(ChatColor.RED + "  " + error);
        }));
    }

    // --- helpers ---

    private UTagsApi api(CommandSender sender) {
        UTagsApi api = UTagsApi.find().orElse(null);
        if (api == null) sender.sendMessage(ChatColor.RED + "The ledger is not available yet.");
        return api;
    }

    /** A UUID, an online player or a player the server has seen; prints the error and returns null otherwise. */
    private UUID resolve(CommandSender sender, String text) {
        try {
            if (text.length() == 36) return UUID.fromString(text);
        } catch (IllegalArgumentException ignored) {
            // not a UUID: try it as a name
        }
        var online = Bukkit.getPlayerExact(text);
        if (online != null) return online.getUniqueId();
        OfflinePlayer known = Bukkit.getOfflinePlayerIfCached(text);
        if (known != null) return known.getUniqueId();
        sender.sendMessage(ChatColor.RED + "Unknown player " + text + " (use the UUID for a player who never joined).");
        return null;
    }

    private void reply(CommandSender sender, Throwable ex, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                sender.sendMessage(ChatColor.RED + "Failed, outcome unknown: " + cause.getMessage() + " (retry with the same op id)");
            } else {
                sender.sendMessage(ChatColor.GREEN + message);
            }
        });
    }
}
