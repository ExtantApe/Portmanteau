package com.extantape.portmanteau;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import java.util.List;

/**
 * One of the plugin's features. A feature that's switched off is never constructed, so nothing of it runs: no
 * listeners, recipes, commands, tasks or files.
 */
public interface Feature {
    /** Registers listeners, recipes and tasks. */
    void start();

    /** Saves what needs saving and unregisters everything it registered. */
    void stop();

    /** Re-reads the config. Returns problems found, in words, for the admin. */
    List<String> reload();

    /** Registers the feature's own player commands. */
    void registerCommands(Commands commands);

    /** Adds the feature's admin subcommands under {@code /portmanteau}. */
    void addAdminCommands(LiteralArgumentBuilder<CommandSourceStack> root);

    /** Lines for {@code /portmanteau status}. */
    List<String> status();
}
