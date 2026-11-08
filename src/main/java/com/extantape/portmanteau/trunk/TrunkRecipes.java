package com.extantape.portmanteau.trunk;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The key and trunk recipes. Each comes from a preset ({@code standard}, {@code easy}, {@code endgame}) or a custom recipe
 * in config, with optional overrides per inventory group. Every recipe in use is registered, and when a player crafts,
 * only the one for the group they're in gives a result. The key ingredient must be a real Multiversal Key. Crafters
 * can't make either, because there's no player and so no group.
 */
final class TrunkRecipes implements Listener {
    static final String KEY_TOKEN = "portmanteau:multiversal_key";

    /** Built-in recipes. Rows top to bottom; letters map to items. */
    static final Map<String, TrunkConfig.RecipeSpec> KEY_PRESETS = Map.of(
            "standard", new TrunkConfig.RecipeSpec(List.of("E", "R"), Map.of('E', "minecraft:ender_eye", 'R', "minecraft:recovery_compass")),
            "easy", new TrunkConfig.RecipeSpec(List.of("P", "C"), Map.of('P', "minecraft:ender_pearl", 'C', "minecraft:compass")),
            "endgame", new TrunkConfig.RecipeSpec(List.of(" N ", "ERE"), Map.of('N', "minecraft:nether_star", 'E', "minecraft:ender_eye", 'R', "minecraft:recovery_compass")));
    static final Map<String, TrunkConfig.RecipeSpec> TRUNK_PRESETS = Map.of(
            "standard", new TrunkConfig.RecipeSpec(List.of("K", "E", "C"), Map.of('K', KEY_TOKEN, 'E', "minecraft:ender_chest", 'C', "minecraft:chest")),
            "easy", new TrunkConfig.RecipeSpec(List.of("K", "C"), Map.of('K', KEY_TOKEN, 'C', "minecraft:chest")),
            "endgame", new TrunkConfig.RecipeSpec(List.of("K", "E", "S"), Map.of('K', KEY_TOKEN, 'E', "minecraft:ender_chest", 'S', "minecraft:shulker_box")));

    private final Plugin plugin;
    private final TrunkItems items;
    private final TrunkConfig.Recipes config;
    private final Scopes scopes;
    private final List<String> problems = new ArrayList<>();
    /** Registered recipe key to what it makes ("key" or "trunk") and its recipe name. */
    private final Map<NamespacedKey, String[]> registered = new HashMap<>();
    /** How many key ingredients each registered recipe has. */
    private final Map<NamespacedKey, Integer> keySlots = new HashMap<>();

    TrunkRecipes(Plugin plugin, TrunkItems items, TrunkConfig.Recipes config, Scopes scopes) {
        this.plugin = plugin;
        this.items = items;
        this.config = config;
        this.scopes = scopes;
    }

    List<String> register() {
        Set<String> keyNames = new LinkedHashSet<>(), trunkNames = new LinkedHashSet<>();
        keyNames.add(config.key());
        trunkNames.add(config.trunk());
        for (var g : config.groups().values()) {
            if (g.containsKey("key")) keyNames.add(g.get("key"));
            if (g.containsKey("trunk")) trunkNames.add(g.get("trunk"));
        }
        for (String name : keyNames) add("key", name, KEY_PRESETS, items.key());
        for (String name : trunkNames) add("trunk", name, TRUNK_PRESETS, items.trunk());
        return problems;
    }

    private void add(String what, String name, Map<String, TrunkConfig.RecipeSpec> presets, ItemStack result) {
        TrunkConfig.RecipeSpec spec = config.custom().containsKey(name) ? config.custom().get(name) : presets.get(name);
        if (spec == null) {
            problems.add("The " + what + " recipe \"" + name + "\" isn't a preset (standard, easy, endgame) or a custom recipe in trunk.recipes.custom. It's skipped.");
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, what + "_" + name.toLowerCase().replaceAll("[^a-z0-9_.-]", "_"));
        try {
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape(spec.shape().toArray(String[]::new));
            int keys = 0;
            for (var e : spec.ingredients().entrySet()) {
                String id = e.getValue();
                if (KEY_TOKEN.equals(id)) {
                    recipe.setIngredient(e.getKey(), new RecipeChoice.MaterialChoice(TrunkItems.KEY_MATERIAL));
                    keys += count(spec.shape(), e.getKey());
                    continue;
                }
                NamespacedKey mk = NamespacedKey.fromString(id);
                Material m = mk == null ? null : Registry.MATERIAL.get(mk);
                if (m == null || !m.isItem()) throw new IllegalArgumentException("\"" + id + "\" isn't an item");
                recipe.setIngredient(e.getKey(), m);
            }
            Bukkit.removeRecipe(key);
            if (!Bukkit.addRecipe(recipe)) throw new IllegalStateException("the server refused it");
            registered.put(key, new String[]{what, name});
            keySlots.put(key, keys);
        } catch (RuntimeException e) {
            problems.add("The " + what + " recipe \"" + name + "\" couldn't be made: " + e.getMessage() + ". It's skipped.");
        }
    }

    private static int count(List<String> shape, char c) {
        int n = 0;
        for (String row : shape) for (char x : row.toCharArray()) if (x == c) n++;
        return n;
    }

    void unregister() {
        for (NamespacedKey key : registered.keySet()) Bukkit.removeRecipe(key);
        registered.clear();
    }

    /** The recipe name for "key" or "trunk" in a player's current group. */
    private String expected(String what, Player p) {
        String group = scopes.groupOf(p.getWorld());
        Map<String, String> override = group == null ? null : config.groups().get(group);
        if (override != null && override.containsKey(what)) return override.get(what);
        return what.equals("key") ? config.key() : config.trunk();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareItemCraftEvent e) {
        CraftingInventory inv = e.getInventory();
        Recipe recipe = e.getRecipe();
        NamespacedKey key = recipe instanceof org.bukkit.Keyed k ? k.getKey() : null;
        String[] ours = key == null ? null : registered.get(key);
        int realKeys = 0, ourItems = 0;
        for (ItemStack item : inv.getMatrix()) {
            if (items.isKey(item)) realKeys++;
            if (items.isOurs(item)) ourItems++;
        }
        if (ours == null) {
            // A key or trunk is never used up in someone else's recipe.
            if (ourItems > 0) inv.setResult(null);
            return;
        }
        if (!(e.getView().getPlayer() instanceof Player p) || !ours[1].equals(expected(ours[0], p))) {
            inv.setResult(null);
            return;
        }
        // Every key slot must hold a real key, and nothing else of ours may be in the grid.
        int needKeys = keySlots.getOrDefault(key, 0);
        if (realKeys != needKeys || ourItems != realKeys) inv.setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent e) {
        if (registered.containsKey(e.getRecipe().getKey())) e.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.getPlayer().discoverRecipes(registered.keySet());
    }

    List<NamespacedKey> keys() {
        return List.copyOf(registered.keySet());
    }
}
