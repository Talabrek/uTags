package com.blockworlds.utags.command;

import com.blockworlds.utags.Tag;
import com.blockworlds.utags.TagType;
import com.blockworlds.utags.uTags;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The /tag and /name command trees (Paper Brigadier). The tree is the 1.3.0 one; the messages and permission checks
 * are those of {@link TagCommand} and {@link NameCommand}, so a player sees what they saw before (the checks run
 * inside the commands, not as {@code requires}, to keep those messages). The one exception is {@code admin}: it
 * {@code requires} utags.admin so other players never see it in tab completion, and a non-admin who types it gets
 * the invalid-usage message instead of "no permission". Tab completion reads the tag registry cache, never the
 * database. The trees can be built with a null plugin to inspect their shape.
 */
public final class UTagsCommands {

    private static final List<String> TYPES = List.of("PREFIX", "SUFFIX", "BOTH");
    private static final List<String> EDIT_ATTRIBUTES = List.of("name", "display", "type", "public", "color", "colorable", "weight");
    private static final List<String> COLOR_CODES = Arrays.asList("&0", "&1", "&2", "&3", "&4", "&5", "&6", "&7", "&8", "&9",
            "&a", "&b", "&c", "&d", "&e", "&f", "reset");
    private static final String EDIT_USAGE = ChatColor.RED + "Usage: /tag admin edit [tagname] [attribute] [newvalue]";

    private final uTags plugin;
    private final TagCommand tag;
    private final NameCommand name;

    public UTagsCommands(uTags plugin) {
        this.plugin = plugin;
        this.tag = new TagCommand(plugin);
        this.name = new NameCommand(plugin, plugin == null ? null : plugin.getNameColorMenuManager());
    }

    /** Registers /tag and /name when the server fires the commands lifecycle event. */
    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(tagTree(), "Opens the tag menu or sends a custom tag request");
            event.registrar().register(nameTree(), "Opens the name color selection menu.");
        });
    }

    // --- /name ---

    public LiteralCommandNode<CommandSourceStack> nameTree() {
        return Commands.literal("name").executes(ctx -> run(ctx, name::open)).build();
    }

    // --- /tag ---

    public LiteralCommandNode<CommandSourceStack> tagTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("tag")
                .executes(ctx -> tagRun(ctx, tag::open));

        root.then(Commands.literal("help")
                .executes(ctx -> tagRun(ctx, p -> tag.displayHelp(p, 1)))
                .then(Commands.argument("page", IntegerArgumentType.integer())
                        .executes(ctx -> tagRun(ctx, p -> tag.displayHelp(p, IntegerArgumentType.getInteger(ctx, "page"))))));

        root.then(Commands.literal("set")
                .executes(ctx -> tagRun(ctx, tag::invalidUsage))
                .then(Commands.argument("tag", StringArgumentType.word())
                        .suggests(this::suggestPermittedPrefixes)
                        .executes(ctx -> tagRun(ctx, p -> tag.set(p, StringArgumentType.getString(ctx, "tag"))))
                        .then(Commands.argument("extra", StringArgumentType.greedyString())
                                .executes(ctx -> tagRun(ctx, tag::invalidUsage)))));

        root.then(Commands.literal("request")
                .executes(ctx -> tagRun(ctx, tag::requestRules))
                .then(Commands.argument("tag", StringArgumentType.word())
                        .executes(ctx -> tagRun(ctx, p -> tag.request(p, StringArgumentType.getString(ctx, "tag"))))
                        .then(Commands.argument("extra", StringArgumentType.greedyString())
                                .executes(ctx -> tagRun(ctx, tag::requestRules)))));

        root.then(Commands.literal("namecolor")
                .executes(ctx -> tagRun(ctx, p -> tag.nameColor(p, null)))
                .then(Commands.argument("color", StringArgumentType.word())
                        .suggests(list(COLOR_CODES))
                        .executes(ctx -> tagRun(ctx, p -> tag.nameColor(p, StringArgumentType.getString(ctx, "color"))))
                        .then(Commands.argument("extra", StringArgumentType.greedyString())
                                .executes(ctx -> tagRun(ctx, p -> tag.nameColor(p, null))))));

        root.then(adminTree());

        // Anything else under /tag
        root.then(Commands.argument("anything", StringArgumentType.greedyString())
                .executes(ctx -> tagRun(ctx, tag::invalidUsage)));
        return root.build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> adminTree() {
        LiteralArgumentBuilder<CommandSourceStack> admin = Commands.literal("admin")
                .requires(src -> src.getSender().hasPermission("utags.admin")) // hidden from completion too
                .executes(ctx -> admin(ctx, tag::displayAdminUsage));

        admin.then(Commands.literal("gui").executes(ctx -> admin(ctx, tag::adminGui)));

        // create <name> <display> <type> <weight> <public>, built from the inside out; a short command prints the usage
        Consumer<Player> createUsage = p -> tag.createTag(p, new String[0]);
        ArgumentBuilder<CommandSourceStack, ?> isPublic = Commands.argument("public", BoolArgumentType.bool())
                .executes(ctx -> admin(ctx, p -> tag.createTag(p, new String[]{
                        StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "display"),
                        StringArgumentType.getString(ctx, "type"), String.valueOf(IntegerArgumentType.getInteger(ctx, "weight")),
                        String.valueOf(BoolArgumentType.getBool(ctx, "public"))})))
                .then(usageTail("extra", createUsage));
        ArgumentBuilder<CommandSourceStack, ?> weight = Commands.argument("weight", IntegerArgumentType.integer())
                .executes(ctx -> admin(ctx, createUsage)).then(isPublic);
        ArgumentBuilder<CommandSourceStack, ?> type = Commands.argument("type", StringArgumentType.word())
                .suggests(list(TYPES)).executes(ctx -> admin(ctx, createUsage)).then(weight);
        ArgumentBuilder<CommandSourceStack, ?> display = Commands.argument("display", StringArgumentType.word())
                .executes(ctx -> admin(ctx, createUsage)).then(type);
        ArgumentBuilder<CommandSourceStack, ?> createName = Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> admin(ctx, createUsage)).then(display);
        admin.then(Commands.literal("create").executes(ctx -> admin(ctx, createUsage)).then(createName));

        Consumer<Player> deleteUsage = p -> tag.deleteTag(p, new String[0]);
        admin.then(Commands.literal("delete")
                .executes(ctx -> admin(ctx, deleteUsage))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(this::suggestAllTags)
                        .executes(ctx -> admin(ctx, p -> tag.deleteTag(p, new String[]{StringArgumentType.getString(ctx, "name")})))
                        .then(usageTail("extra", deleteUsage))));

        Consumer<Player> editUsage = p -> p.sendMessage(EDIT_USAGE);
        admin.then(Commands.literal("edit")
                .executes(ctx -> admin(ctx, editUsage))
                .then(Commands.argument("tag", StringArgumentType.word())
                        .suggests(this::suggestAllTags)
                        .executes(ctx -> admin(ctx, editUsage))
                        .then(Commands.argument("attribute", StringArgumentType.word())
                                .suggests(list(EDIT_ATTRIBUTES))
                                .executes(ctx -> admin(ctx, editUsage))
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> admin(ctx, p -> tag.editTag(p,
                                                StringArgumentType.getString(ctx, "tag"),
                                                StringArgumentType.getString(ctx, "attribute"),
                                                StringArgumentType.getString(ctx, "value"))))))));

        admin.then(Commands.literal("purge")
                .executes(ctx -> admin(ctx, tag::purgeUsage))
                .then(Commands.argument("type", StringArgumentType.word())
                        .suggests(list(List.of("tags", "requests")))
                        .executes(ctx -> admin(ctx, p -> tag.purge(p, StringArgumentType.getString(ctx, "type"), false)))
                        .then(Commands.literal("confirm")
                                .executes(ctx -> admin(ctx, p -> tag.purge(p, StringArgumentType.getString(ctx, "type"), true)))
                                .then(usageTail("extra", tag::purgeUsage)))));

        new LedgerAdminCommands(plugin).addTo(admin);

        admin.then(Commands.literal("requests").executes(ctx -> admin(ctx, tag::adminRequests)));
        admin.then(Commands.literal("debug").executes(ctx -> admin(ctx, tag::adminDebug)));

        admin.then(Commands.argument("anything", StringArgumentType.greedyString())
                .executes(ctx -> admin(ctx, tag::displayAdminUsage)));
        return admin;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> usageTail(String argName, Consumer<Player> usage) {
        return Commands.argument(argName, StringArgumentType.greedyString()).executes(ctx -> admin(ctx, usage));
    }

    // --- running ---

    /** Runs an action for a player sender; the console gets the 1.3.0 message. */
    private static int run(CommandContext<CommandSourceStack> ctx, Consumer<Player> action) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return 1;
        }
        action.accept(player);
        return 1;
    }

    /** Like run, for /tag: needs utags.tag (1.3.0 declared that in plugin.yml, with this message). */
    private static int tagRun(CommandContext<CommandSourceStack> ctx, Consumer<Player> action) {
        return run(ctx, p -> {
            if (!p.hasPermission("utags.tag")) {
                p.sendMessage("You don't have permission to use this command.");
                return;
            }
            action.accept(p);
        });
    }

    /** Like run, for the /tag admin commands: staff only, with the 1.3.0 message. */
    private static int admin(CommandContext<CommandSourceStack> ctx, Consumer<Player> action) {
        return tagRun(ctx, p -> {
            if (!p.hasPermission("utags.admin")) {
                p.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return;
            }
            action.accept(p);
        });
    }

    // --- suggestions (registry cache only) ---

    private static SuggestionProvider<CommandSourceStack> list(List<String> values) {
        return (ctx, builder) -> {
            String typed = builder.getRemainingLowerCase();
            for (String v : values) if (v.toLowerCase().startsWith(typed)) builder.suggest(v);
            return builder.buildFuture();
        };
    }

    private CompletableFuture<Suggestions> suggestPermittedPrefixes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        CommandSender sender = ctx.getSource().getSender();
        String typed = builder.getRemainingLowerCase();
        for (Tag t : plugin.tags().available(TagType.PREFIX)) {
            boolean owned = sender instanceof Player p ? plugin.ownership().owns(p, t.getName()) : sender.hasPermission("utags.tag." + t.getName());
            if (owned && t.getName().toLowerCase().startsWith(typed)) {
                builder.suggest(t.getName());
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestAllTags(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String typed = builder.getRemainingLowerCase();
        plugin.tags().available(null).stream().map(Tag::getName).distinct()
                .filter(n -> n.toLowerCase().startsWith(typed)).forEach(builder::suggest);
        return builder.buildFuture();
    }
}
