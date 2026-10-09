package com.blockworlds.utags.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Who sees /tag admin, and the ledger commands' source and op id arguments (findings of the 1.4.0 staging check). */
class CommandAccessTest {

    private static CommandDispatcher<CommandSourceStack> dispatcher() {
        CommandDispatcher<CommandSourceStack> d = new CommandDispatcher<>();
        d.getRoot().addChild(new UTagsCommands(null).tagTree());
        return d;
    }

    private static CommandSourceStack source(boolean admin) {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("utags.admin")).thenReturn(admin);
        CommandSourceStack stack = mock(CommandSourceStack.class);
        when(stack.getSender()).thenReturn(sender);
        return stack;
    }

    @Test
    void adminIsOnlyUsableByAdmins() {
        CommandNode<CommandSourceStack> admin = new UTagsCommands(null).tagTree().getChild("admin");
        assertFalse(admin.canUse(source(false)));
        assertTrue(admin.canUse(source(true)));
        CommandNode<CommandSourceStack> set = new UTagsCommands(null).tagTree().getChild("set");
        assertTrue(set.canUse(source(false)));
    }

    @Test
    void grantTakesASourceWithAColon() {
        CommandDispatcher<CommandSourceStack> d = dispatcher();
        ParseResults<CommandSourceStack> parse = d.parse("tag admin grant Bob vip tebex:tbx-123 tebex:tbx-123:vip", source(true));
        assertTrue(parse.getExceptions().isEmpty(), parse.getExceptions().toString());
        assertFalse(parse.getReader().canRead(), "unparsed: " + parse.getReader().getRemaining());
        assertEquals("tebex:tbx-123", parse.getContext().getArguments().get("source").getResult());
        assertEquals("tebex:tbx-123:vip", parse.getContext().getArguments().get("opId").getResult());
    }

    @Test
    void grantTakesABedrockNameWithAFloodgatePrefix() {
        CommandDispatcher<CommandSourceStack> d = dispatcher();
        for (String name : new String[]{"*br45entei", ".Steve_2"}) {
            ParseResults<CommandSourceStack> parse = d.parse("tag admin grant " + name + " vip tebex:1 op-1", source(true));
            assertTrue(parse.getExceptions().isEmpty(), parse.getExceptions().toString());
            assertFalse(parse.getReader().canRead(), "unparsed: " + parse.getReader().getRemaining());
            assertEquals(name, parse.getContext().getArguments().get("player").getResult());
        }
        ParseResults<CommandSourceStack> grants = d.parse("tag admin grants *br45entei", source(true));
        assertFalse(grants.getReader().canRead(), "unparsed: " + grants.getReader().getRemaining());
    }

    @Test
    void tokenStopsAtASpaceAndRefusesNothing() throws CommandSyntaxException {
        LedgerAdminCommands.Token token = new LedgerAdminCommands.Token();
        StringReader reader = new StringReader("progress:reward_1 next");
        assertEquals("progress:reward_1", token.parse(reader));
        assertEquals(' ', reader.peek());
        assertThrows(CommandSyntaxException.class, () -> token.parse(new StringReader("")));
    }
}
