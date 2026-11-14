package com.extantape.portmanteau.sorting;

import com.extantape.portmanteau.Feature;
import com.extantape.portmanteau.sorting.category.Categories;
import com.extantape.portmanteau.sorting.category.CategoryFile;
import com.extantape.portmanteau.sorting.claims.ClaimCheck;
import com.extantape.portmanteau.sorting.claims.GriefPreventionCheck;
import com.extantape.portmanteau.text.Messages;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Sorting: containers on close, hotkeys, {@code /isort}, and the player setup. */
public final class SortingFeature implements Feature {
    /** The default category files, shipped in the jar and copied out on first start. */
    static final List<String> DEFAULT_FILES = List.of("010-tools.txt", "020-weapons.txt", "030-armor.txt", "040-food.txt",
            "050-potions-and-enchanting.txt", "060-redstone.txt", "070-transport.txt", "080-wood.txt",
            "090-stone-and-building.txt", "100-colorful-blocks.txt", "110-nature-and-farming.txt",
            "120-ores-and-materials.txt", "130-mob-drops.txt", "140-workstations-and-decor.txt", "150-music-and-collectibles.txt");

    private final JavaPlugin plugin;
    private final Messages messages;
    private final Logger log;
    private SortingConfig config;
    private FactsCache facts;
    private ContainerGate gate;
    private Sorter sorter;
    private SortListeners listeners;
    private SetupDialog dialog;
    private PrismItems prism;

    public SortingFeature(JavaPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.log = plugin.getLogger();
    }

    @Override
    public void start() {
        List<String> problems = load();
        ClaimCheck claims = ClaimCheck.NONE;
        if (Bukkit.getPluginManager().isPluginEnabled("GriefPrevention")) {
            claims = new GriefPreventionCheck();
            log.info("Sorting checks GriefPrevention container trust.");
        }
        prism = new PrismItems(log);
        sorter = new Sorter(plugin, facts, config, claims);
        listeners = new SortListeners(plugin, this, messages);
        dialog = new SetupDialog(plugin, this, messages);
        Bukkit.getPluginManager().registerEvents(listeners, plugin);
        com.extantape.portmanteau.sorting.api.SortingApi.install((inv, player) -> {
            ContainerGate.Target target;
            if (inv instanceof org.bukkit.inventory.PlayerInventory) {
                target = new ContainerGate.Target(ContainerGate.Kind.PLAYER, inv, java.util.Set.of(), null);
            } else if (inv.getHolder(false) instanceof org.bukkit.entity.ChestedHorse) {
                target = new ContainerGate.Target(ContainerGate.Kind.ALLOWED, inv, java.util.Set.of(0, 1), null);
            } else if (java.util.EnumSet.of(org.bukkit.event.inventory.InventoryType.CHEST, org.bukkit.event.inventory.InventoryType.BARREL,
                    org.bukkit.event.inventory.InventoryType.SHULKER_BOX, org.bukkit.event.inventory.InventoryType.ENDER_CHEST).contains(inv.getType())) {
                target = new ContainerGate.Target(ContainerGate.Kind.ALLOWED, inv, java.util.Set.of(), null);
            } else {
                // Furnaces, brewing stands and the like have slots with rules. Plain storage only.
                return com.extantape.portmanteau.sorting.api.SortingApi.Result.NOT_ALLOWED;
            }
            var o = sorter.sort(player, target, com.extantape.portmanteau.sorting.api.SortReason.API, Sorter.Part.BOTH);
            return switch (o.status()) {
                case SORTED -> com.extantape.portmanteau.sorting.api.SortingApi.Result.SORTED;
                case ALREADY_SORTED -> com.extantape.portmanteau.sorting.api.SortingApi.Result.ALREADY_SORTED;
                case LEFT_SAFE -> com.extantape.portmanteau.sorting.api.SortingApi.Result.LEFT_UNCHANGED;
                case FAILED -> com.extantape.portmanteau.sorting.api.SortingApi.Result.FAILED;
                case NOT_ALLOWED -> com.extantape.portmanteau.sorting.api.SortingApi.Result.NOT_ALLOWED;
            };
        });
        for (String p : problems) log.warning(p);
        log.info("Sorting is ready: " + facts.items() + " items in " + facts.categories().size() + " categories.");
    }

    /** Reads the config and category files. Returns problems in words. */
    private List<String> load() {
        config = SortingConfig.read(plugin.getConfig().getConfigurationSection("sorting"));
        List<String> problems = new ArrayList<>(config.problems());
        Categories categories = new Categories(readCategories(problems));
        facts = new FactsCache(categories);
        problems.addAll(facts.problems());
        for (var preset : config.presets().values()) {
            for (String id : preset.first()) {
                if (categories.indexOf(id) < 0) {
                    problems.add("Preset " + preset.id() + " puts \"" + id + "\" first, but there's no category with that name.");
                }
            }
        }
        gate = new ContainerGate(config.allowedHolders(), true);
        return problems;
    }

    File categoryFolder() {
        return new File(plugin.getDataFolder(), "categories");
    }

    private List<CategoryFile> readCategories(List<String> problems) {
        File folder = categoryFolder();
        if (!folder.isDirectory()) {
            if (!folder.mkdirs()) problems.add("Couldn't make " + folder + ". Using the built-in categories.");
            for (String name : DEFAULT_FILES) {
                try (InputStream in = plugin.getResource("categories/" + name)) {
                    if (in != null) Files.copy(in, new File(folder, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    problems.add("Couldn't copy the default category " + name + ": " + e.getMessage());
                }
            }
        }
        List<CategoryFile> files = new ArrayList<>();
        File[] found = folder.listFiles((d, n) -> n.toLowerCase().endsWith(".txt"));
        if (found != null) {
            List<String> skipped = new ArrayList<>();
            for (File f : found) {
                if (!CategoryFile.isCategoryFileName(f.getName())) {
                    skipped.add(f.getName());
                    continue;
                }
                try {
                    files.add(CategoryFile.parse(f.getName(), Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)));
                } catch (IOException e) {
                    problems.add("Couldn't read category file " + f.getName() + ": " + e.getMessage());
                }
            }
            if (!skipped.isEmpty()) {
                log.info("Not category files, so not read (a category file's name starts with a number, such as 010-tools.txt): "
                        + String.join(", ", skipped));
            }
        }
        if (files.isEmpty()) {
            problems.add("No category files in " + folder + ". Using the built-in ones.");
            for (String name : DEFAULT_FILES) {
                try (InputStream in = plugin.getResource("categories/" + name)) {
                    if (in != null) files.add(CategoryFile.parse(name, new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList()));
                } catch (IOException ignored) {
                    // Nothing more to fall back to; the catch-all still sorts everything by name.
                }
            }
        }
        return files;
    }

    /** Prism, when it's installed, for recording quick transfers. */
    PrismItems prism() { return prism; }

    @Override
    public void stop() {
        com.extantape.portmanteau.sorting.api.SortingApi.install(null);
        if (listeners != null) HandlerList.unregisterAll(listeners);
        listeners = null;
    }

    @Override
    public List<String> reload() {
        List<String> problems = load();
        sorter.update(facts, config);
        for (String p : problems) log.warning(p);
        return problems;
    }

    @Override
    public void registerCommands(Commands commands) {
        SortCommands.register(commands, this, dialog, messages);
    }

    @Override
    public void addAdminCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        SortCommands.addAdmin(root, this, plugin);
    }

    @Override
    public List<String> status() {
        List<String> out = new ArrayList<>();
        out.add("Containers: " + mode(config.containers()) + ". Inventory: " + mode(config.inventory())
                + ". Player choices: " + config.playerChoices() + "%.");
        out.add("Hotkeys: " + hotkeys() + ". Quick transfer: " + (config.quickTransfer() ? "on" : "off") + ".");
        out.add("Order: " + config.method().text() + ", " + facts.categories().size() + " categories, "
                + facts.items() + " items known.");
        out.add(sorter.timing());
        return out;
    }

    private String hotkeys() {
        var h = config.hotkeys();
        List<String> on = new ArrayList<>();
        if (h.middleClick()) on.add("middle-click");
        if (h.shiftClick()) on.add("shift-click");
        if (h.doubleClick()) on.add("double-click");
        if (h.shiftRightClick()) on.add("shift-right-click");
        return on.isEmpty() ? "none" : String.join(", ", on) + " (" + h.cooldownMillis() + " ms apart)";
    }

    static String mode(SortingConfig.Mode m) {
        return m.name().toLowerCase().replace('_', '-');
    }

    /** Whether the setup has anything for a player to change. */
    boolean anythingToChoose() {
        int c = config.playerChoices();
        if (c >= SortingConfig.CHOICES_PRESET) return true;
        return c >= SortingConfig.CHOICES_SWITCHES && (config.containers().playerMayChange() || config.inventory().playerMayChange());
    }

    SortingConfig config() { return config; }
    ContainerGate gate() { return gate; }
    Sorter sorter() { return sorter; }
    FactsCache facts() { return facts; }
    SortListeners listeners() { return listeners; }
    JavaPlugin plugin() { return plugin; }
}
