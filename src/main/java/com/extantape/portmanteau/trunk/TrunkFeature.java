package com.extantape.portmanteau.trunk;

import com.extantape.portmanteau.Feature;
import com.extantape.portmanteau.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** The multiversal trunk: a craftable block that opens the player's own 54 slots, shared across linked worlds. */
public final class TrunkFeature implements Feature {
    private final JavaPlugin plugin;
    private final Messages messages;
    private final Logger log;
    private TrunkConfig config;
    private Scopes scopes;
    private TrunkSessions sessions;
    private TrunkItems items;
    private TrunkBlocks blocks;
    private TrunkRecipes recipes;
    private TrunkListeners listeners;
    private AdminCopies copies;
    private ScheduledTask timer;

    public TrunkFeature(JavaPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.log = plugin.getLogger();
    }

    @Override
    public void start() {
        List<String> problems = new ArrayList<>();
        config = TrunkConfig.read(plugin.getConfig().getConfigurationSection("trunk"));
        problems.addAll(config.problems());

        boolean mvi = Bukkit.getPluginManager().isPluginEnabled("Multiverse-Inventories");
        GroupSource source = switch (config.source()) {
            case CONFIG -> new ConfigGroups(config.configGroups());
            case MULTIVERSE_INVENTORIES -> mvi ? new MultiverseGroups() : null;
            case AUTO -> mvi ? new MultiverseGroups() : new ConfigGroups(config.configGroups());
        };
        if (source == null) problems.add("trunk.groups-from is multiverse-inventories, but Multiverse-Inventories isn't running, so every trunk stays shut.");
        scopes = new Scopes(source, config);
        if (source != null) {
            log.info("Multiversal trunk groups come from " + source.describe() + ".");
            var links = scopes.links();
            for (String e : links.errors()) problems.add("Trunk links refused: " + e);
        }

        copies = new AdminCopies(plugin, AdminCopies.serverId(plugin));
        sessions = new TrunkSessions(plugin, new TrunkStore(plugin), copies, config.adminCopies());
        items = new TrunkItems(plugin, messages, config.look());
        blocks = new TrunkBlocks(plugin, items);
        recipes = new TrunkRecipes(plugin, items, config.recipes(), scopes);
        problems.addAll(recipes.register());
        listeners = new TrunkListeners(this, messages, log);
        var pm = Bukkit.getPluginManager();
        pm.registerEvents(blocks, plugin);
        pm.registerEvents(recipes, plugin);
        pm.registerEvents(listeners, plugin);
        for (Player p : Bukkit.getOnlinePlayers()) p.discoverRecipes(recipes.keys());
        // A full check every half second, on top of the save after each click, catches changes no event reported.
        timer = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> sessions.commitAll("timer"), 10, 10);
        for (String p : problems) log.warning(p);
        lastProblems = problems;
    }

    private List<String> lastProblems = List.of();

    @Override
    public void stop() {
        if (timer != null) timer.cancel();
        if (sessions != null) sessions.shutdown();
        if (recipes != null) recipes.unregister();
        for (var l : new org.bukkit.event.Listener[]{blocks, recipes, listeners}) if (l != null) HandlerList.unregisterAll(l);
        if (copies != null) copies.close();
    }

    @Override
    public List<String> reload() {
        stop();
        start();
        return lastProblems;
    }

    @Override
    public void registerCommands(Commands commands) {
        // The trunk has no player commands: it's crafted, placed and opened.
    }

    @Override
    public void addAdminCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        var trunk = Commands.literal("trunk");
        trunk.then(Commands.literal("give").then(Commands.argument("player", ArgumentTypes.player())
                .then(Commands.literal("key").executes(ctx -> give(ctx, false, 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64)).executes(ctx -> give(ctx, false, IntegerArgumentType.getInteger(ctx, "amount")))))
                .then(Commands.literal("trunk").executes(ctx -> give(ctx, true, 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64)).executes(ctx -> give(ctx, true, IntegerArgumentType.getInteger(ctx, "amount")))))));
        trunk.then(Commands.literal("view").then(Commands.argument("player", ArgumentTypes.player()).executes(this::view)));
        trunk.then(Commands.literal("scopes").executes(ctx -> {
            var out = ctx.getSource().getSender();
            out.sendMessage(Component.text("Multiversal trunk scopes, by world:", NamedTextColor.GOLD));
            for (var w : Bukkit.getWorlds()) {
                var r = scopes.resolve(w);
                out.sendMessage(Component.text("  " + w.getName() + ": " + (r.scope() == null ? "shut (" + r.problem() + ")"
                        : r.scope().label() + " [" + r.scope().id() + "]")));
            }
            for (String e : scopes.links().errors()) out.sendMessage(Component.text("  " + e, NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }));
        root.then(trunk);
    }

    private int give(CommandContext<CommandSourceStack> ctx, boolean trunk, int amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Player target = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
        ItemStack item = trunk ? items.trunk() : items.key();
        item.setAmount(amount);
        Map<Integer, ItemStack> left = target.getInventory().addItem(item);
        for (ItemStack rest : left.values()) target.getWorld().dropItemNaturally(target.getLocation(), rest);
        ctx.getSource().getSender().sendMessage(Component.text("Gave " + amount + " " + (trunk ? "multiversal trunk" : "Multiversal Key")
                + (amount == 1 ? "" : "s") + " to " + target.getName() + (left.isEmpty() ? "." : ", dropping what didn't fit at their feet.")));
        return Command.SINGLE_SUCCESS;
    }

    /** A read-only copy of a player's storage for the scope of the world they're in now. Admin views never save. */
    private int view(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!(ctx.getSource().getExecutor() instanceof Player admin)) {
            ctx.getSource().getSender().sendMessage(Component.text("Only a player can open a view."));
            return 0;
        }
        Player target = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
        var r = scopes.resolve(target.getWorld());
        if (r.scope() == null) {
            admin.sendMessage(Component.text("No trunk scope in " + target.getWorld().getName() + ": " + r.problem(), NamedTextColor.YELLOW));
            return 0;
        }
        ItemStack[] contents;
        TrunkSessions.Session live = sessions.all().stream()
                .filter(s -> s.owner.equals(target.getUniqueId()) && s.scope.equals(r.scope().id())).findFirst().orElse(null);
        if (live != null) {
            contents = live.inventory.getContents();
        } else if (new TrunkStore(plugin).load(target, r.scope().id()) instanceof TrunkStore.Contents c) {
            contents = c.items();
        } else {
            admin.sendMessage(Component.text("That trunk's stored data can't be read. See the console.", NamedTextColor.RED));
            return 0;
        }
        ReadOnlyHolder holder = new ReadOnlyHolder();
        Inventory inv = Bukkit.createInventory(holder, TrunkStore.SLOTS,
                Component.text(target.getName() + " · " + r.scope().label() + " (read-only)"));
        holder.attach(inv);
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) copy[i] = contents[i] == null ? null : contents[i].clone();
        inv.setContents(copy);
        admin.openInventory(inv);
        return Command.SINGLE_SUCCESS;
    }

    @Override
    public List<String> status() {
        List<String> out = new ArrayList<>();
        GroupSource source = scopes.source();
        out.add("Trunk groups from: " + (source == null ? "nothing (trunks stay shut)" : source.describe()) + ". Links: "
                + config.links().size() + (scopes.links().errors().isEmpty() ? "" : ", REFUSED (see /portmanteau trunk scopes)") + ".");
        out.add("Open trunks: " + sessions.open() + ". Game modes: " + config.gameModes() + ". Admin copies: "
                + (config.adminCopies() ? "on" : "off") + ".");
        out.add("Recipes: key " + config.recipes().key() + ", trunk " + config.recipes().trunk()
                + (config.recipes().groups().isEmpty() ? "" : ", with overrides for " + Set.copyOf(config.recipes().groups().keySet())) + ".");
        return out;
    }

    TrunkConfig config() { return config; }
    Scopes scopes() { return scopes; }
    TrunkSessions sessions() { return sessions; }
    TrunkBlocks blocks() { return blocks; }
}
