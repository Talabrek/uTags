package com.blockworlds.utags.spi;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;

/**
 * Pro extension point: adds subcommands to {@code /tag} and {@code /tag admin}. Called while the trees are built, which
 * Paper does after every plugin is enabled, so modules register it in {@code enable}. {@code admin} already requires
 * utags.admin; add nodes, never replace or remove the core's. Document every command in the Pro README.
 */
public interface CommandExtension {

    void extend(LiteralArgumentBuilder<CommandSourceStack> tag, LiteralArgumentBuilder<CommandSourceStack> admin);
}
