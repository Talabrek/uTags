package com.blockworlds.utags.command;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Brigadier trees (built without a plugin) against the README command table: the same commands, none missing,
 * none undocumented, and /tag admin create / edit take the number of arguments the README gives.
 */
class CommandTreeTest {

    private static final Pattern COMMAND_CELL = Pattern.compile("^\\| `(/[^`]+)`");
    /** Catch-all argument nodes that only print the usage message; they are not commands. */
    private static final Set<String> TAILS = Set.of("extra", "anything");

    private static UTagsCommands commands() {
        return new UTagsCommands(null);
    }

    private static List<String> readmeRows() throws Exception {
        List<String> rows = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of("README.md"))) {
            Matcher m = COMMAND_CELL.matcher(line);
            if (m.find()) rows.add(m.group(1).replace("\\|", "|"));
        }
        return rows;
    }

    /** Literal word sequences of every node of the tree, arguments dropped ("tag admin purge confirm"). */
    private static void literalPaths(CommandNode<CommandSourceStack> node, String path, Set<String> out) {
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            if (child instanceof LiteralCommandNode<CommandSourceStack> lit) {
                String next = path.isEmpty() ? lit.getLiteral() : path + " " + lit.getLiteral();
                out.add(next);
                literalPaths(child, next, out);
            } else if (!TAILS.contains(child.getName())) {
                literalPaths(child, path, out);
            }
        }
    }

    private static Set<String> treePaths() {
        Set<String> paths = new TreeSet<>();
        UTagsCommands commands = commands();
        paths.add(commands.tagTree().getLiteral());
        literalPaths(commands.tagTree(), commands.tagTree().getLiteral(), paths);
        paths.add(commands.nameTree().getLiteral());
        return paths;
    }

    /** The README rows as literal sequences, with every prefix ("tag admin" is a node but has no row of its own). */
    private static Set<String> readmePaths() throws Exception {
        Set<String> paths = new TreeSet<>();
        for (String row : readmeRows()) {
            StringBuilder path = new StringBuilder();
            for (String word : row.substring(1).split(" ")) {
                if (word.startsWith("<") || word.startsWith("[")) continue;
                if (path.length() > 0) path.append(' ');
                path.append(word);
                paths.add(path.toString());
            }
        }
        return paths;
    }

    @Test
    void theTreeAndTheReadmeListTheSameCommands() throws Exception {
        assertTrue(readmeRows().size() >= 10, "the README command table was parsed: " + readmeRows());
        assertEquals(readmePaths(), treePaths());
    }

    private static CommandNode<CommandSourceStack> child(CommandNode<CommandSourceStack> node, String name) {
        CommandNode<CommandSourceStack> found = node.getChild(name);
        assertNotNull(found, "no node " + name);
        return found;
    }

    /** Arguments in a chain below a literal, following the one that is not a catch-all tail. */
    private static int argumentDepth(CommandNode<CommandSourceStack> node) {
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            if (child instanceof ArgumentCommandNode && !TAILS.contains(child.getName())) return 1 + argumentDepth(child);
        }
        return 0;
    }

    @Test
    void createAndEditTakeTheDocumentedNumberOfArguments() throws Exception {
        CommandNode<CommandSourceStack> admin = child(commands().tagTree(), "admin");
        String create = readmeRows().stream().filter(c -> c.startsWith("/tag admin create")).findFirst().orElseThrow();
        String edit = readmeRows().stream().filter(c -> c.startsWith("/tag admin edit")).findFirst().orElseThrow();
        assertEquals(create.split(" ").length - 3, argumentDepth(child(admin, "create")));
        assertEquals(edit.split(" ").length - 3, argumentDepth(child(admin, "edit")));
    }

    @Test
    void everyDocumentedPathCanBeRun() throws Exception {
        // the leaf of each README row (with its optional arguments left out) must carry a command
        for (String row : readmeRows()) {
            CommandNode<CommandSourceStack> node = row.startsWith("/name") ? commands().nameTree() : commands().tagTree();
            String[] words = row.substring(1).split(" ");
            for (int i = 1; i < words.length; i++) {
                if (words[i].startsWith("[")) continue;
                if (words[i].startsWith("<")) {
                    // an argument: the first one below that is not a catch-all
                    CommandNode<CommandSourceStack> arg = null;
                    for (CommandNode<CommandSourceStack> c : node.getChildren()) {
                        if (c instanceof ArgumentCommandNode && !TAILS.contains(c.getName())) arg = c;
                    }
                    assertNotNull(arg, "no argument node for " + words[i] + " in " + row);
                    node = arg;
                } else {
                    node = child(node, words[i]);
                }
            }
            assertNotNull(node.getCommand(), "nothing runs for " + row);
        }
    }
}
