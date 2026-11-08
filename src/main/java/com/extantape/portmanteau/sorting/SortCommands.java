package com.extantape.portmanteau.sorting;

import com.extantape.portmanteau.sorting.Sorter.Part;
import com.extantape.portmanteau.sorting.api.SortReason;
import com.extantape.portmanteau.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** {@code /sort}, {@code /isort} and the sorting admin commands. */
final class SortCommands {
    private SortCommands() {}

    static void register(Commands commands, SortingFeature feature, SetupDialog dialog, Messages messages) {
        var sort = Commands.literal("sort")
                .requires(s -> s.getExecutor() instanceof Player p && p.hasPermission("portmanteau.sort"))
                .executes(ctx -> {
                    dialog.open((Player) ctx.getSource().getExecutor());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("reset").executes(ctx -> {
                    Player p = (Player) ctx.getSource().getExecutor();
                    PlayerChoices c = PlayerChoices.load(feature.plugin(), p);
                    c.reset();
                    c.save(feature.plugin(), p);
                    messages.send(p, "setup.reset-done");
                    return Command.SINGLE_SUCCESS;
                }));
        commands.register(sort.build(), "Choose how your chests and inventory are sorted", List.of());

        var isort = Commands.literal("isort")
                .requires(s -> s.getExecutor() instanceof Player p && p.hasPermission("portmanteau.inventory"))
                .executes(ctx -> sortInventory(feature, messages, (Player) ctx.getSource().getExecutor(), null))
                .then(Commands.literal("hotbar").executes(ctx -> sortInventory(feature, messages, (Player) ctx.getSource().getExecutor(), Part.HOTBAR)))
                .then(Commands.literal("all").executes(ctx -> sortInventory(feature, messages, (Player) ctx.getSource().getExecutor(), Part.BOTH)));
        commands.register(isort.build(), "Sort your inventory", List.of("invsort"));
    }

    private static int sortInventory(SortingFeature feature, Messages messages, Player p, Part part) {
        // Only from the player's own screen or a real container, never under another plugin's menu.
        var top = p.getOpenInventory().getTopInventory();
        if (top.getType() != InventoryType.CRAFTING && feature.gate().container(p, top) == null) {
            messages.send(p, "sort.not-here");
            return 0;
        }
        if (part == null) {
            part = Effective.hotbar(feature.config(), PlayerChoices.load(feature.plugin(), p)) ? Part.BOTH : Part.MAIN;
        }
        var target = feature.gate().playerInventory(p, p.getInventory());
        var outcome = feature.sorter().sort(p, target, SortReason.COMMAND, part);
        feature.listeners().report(p, outcome, true);
        return Command.SINGLE_SUCCESS;
    }

    static void addAdmin(LiteralArgumentBuilder<CommandSourceStack> root, SortingFeature feature, JavaPlugin plugin) {
        root.then(Commands.literal("import").then(Commands.literal("chestsort").executes(ctx -> {
            var out = ctx.getSource().getSender();
            List<String> report = ChestSortImport.run(plugin, feature);
            for (String line : report) out.sendMessage(Component.text(line));
            return Command.SINGLE_SUCCESS;
        })));
        root.then(Commands.literal("categories").executes(ctx -> {
            var out = ctx.getSource().getSender();
            var cats = feature.facts().categories();
            int[] counts = feature.facts().census();
            out.sendMessage(Component.text("Categories, in order, with how many items each holds:", NamedTextColor.GOLD));
            for (int i = 0; i < cats.size(); i++) {
                out.sendMessage(Component.text("  " + (i + 1) + ". " + cats.display(i) + " (" + cats.id(i) + "): " + counts[i]));
            }
            for (String p : feature.facts().problems()) out.sendMessage(Component.text("  " + p, NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }));
    }
}
