package com.extantape.portmanteau;

import com.extantape.portmanteau.sorting.SortingFeature;
import com.extantape.portmanteau.text.Messages;
import com.extantape.portmanteau.trunk.TrunkFeature;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/** Portmanteau: sorting and the multiversal trunk, each with its own switch, sharing only the transaction code. */
public final class PortmanteauPlugin extends JavaPlugin {
    private Messages messages;
    private final List<Feature> features = new ArrayList<>();
    private boolean sortingOn, trunkOn;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        sortingOn = getConfig().getBoolean("features.sorting", true);
        trunkOn = getConfig().getBoolean("features.trunk", true);
        if (sortingOn) features.add(new SortingFeature(this, messages));
        if (trunkOn) features.add(new TrunkFeature(this, messages));
        for (Feature f : features) f.start();
        getLogger().info("Sorting is " + (sortingOn ? "on" : "off") + ", the multiversal trunk is " + (trunkOn ? "on" : "off") + ".");

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            for (Feature f : features) f.registerCommands(commands);
            commands.register(adminCommand().build(), "Portmanteau admin commands", List.of());
        });
    }

    @Override
    public void onDisable() {
        for (Feature f : features) {
            try {
                f.stop();
            } catch (RuntimeException e) {
                getLogger().log(java.util.logging.Level.SEVERE, "Stopping a feature failed", e);
            }
        }
        features.clear();
    }

    private LiteralArgumentBuilder<CommandSourceStack> adminCommand() {
        var root = Commands.literal("portmanteau").requires(s -> s.getSender().hasPermission("portmanteau.admin"));
        root.then(Commands.literal("status").executes(ctx -> {
            var out = ctx.getSource().getSender();
            out.sendMessage(Component.text("Portmanteau " + getPluginMeta().getVersion(), NamedTextColor.GOLD));
            out.sendMessage(Component.text("Sorting: " + (sortingOn ? "on" : "off") + ". Multiversal trunk: " + (trunkOn ? "on" : "off") + "."));
            for (Feature f : features) for (String line : f.status()) out.sendMessage(Component.text("  " + line));
            return Command.SINGLE_SUCCESS;
        }));
        root.then(Commands.literal("reload").executes(ctx -> {
            var out = ctx.getSource().getSender();
            reloadConfig();
            messages.reload();
            List<String> problems = new ArrayList<>();
            if (getConfig().getBoolean("features.sorting", true) != sortingOn
                    || getConfig().getBoolean("features.trunk", true) != trunkOn) {
                problems.add("Switching a feature on or off takes a restart. The other changes are loaded.");
            }
            for (Feature f : features) problems.addAll(f.reload());
            out.sendMessage(Component.text("Reloaded Portmanteau's config and messages.", NamedTextColor.GREEN));
            for (String p : problems) out.sendMessage(Component.text("  " + p, NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }));
        for (Feature f : features) f.addAdminCommands(root);
        return root;
    }

    public Messages messages() {
        return messages;
    }
}
