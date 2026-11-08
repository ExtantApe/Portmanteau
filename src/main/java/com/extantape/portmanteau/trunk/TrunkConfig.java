package com.extantape.portmanteau.trunk;

import com.extantape.portmanteau.trunk.LinkRules.Audience;
import com.extantape.portmanteau.trunk.LinkRules.Link;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The {@code trunk} section of config.yml. */
public record TrunkConfig(
        Source source,
        Map<String, Set<String>> configGroups,
        Map<String, Audience> groupAudiences,
        Map<String, Audience> worldAudiences,
        List<Link> links,
        Map<String, String> names,
        Set<GameMode> gameModes,
        Look look,
        Recipes recipes,
        boolean adminCopies,
        List<String> problems) {

    /** Where inventory groups come from. */
    public enum Source {
        /** Multiverse-Inventories if it's installed, otherwise the groups in this config. */
        AUTO,
        /** Multiverse-Inventories only. If it isn't running, trunks stay shut. */
        MULTIVERSE_INVENTORIES,
        /** The groups in this config only. */
        CONFIG
    }

    /**
     * How the trunk looks. Item models come from the server's resource pack; without one, leave them empty and the
     * trunk shows as a glinting lodestone.
     */
    public record Look(String trunkModel, String keyModel, String blockModel, String sound, String particle) {}

    /**
     * @param key     the preset or custom recipe name for the key, everywhere by default
     * @param trunk   the same for the trunk
     * @param custom  custom recipes by name: a shape and ingredients
     * @param groups  per-group overrides: group name to a map of "key" or "trunk" to a recipe name
     */
    public record Recipes(String key, String trunk, Map<String, RecipeSpec> custom, Map<String, Map<String, String>> groups) {}

    public record RecipeSpec(List<String> shape, Map<Character, String> ingredients) {}

    public static TrunkConfig read(ConfigurationSection s) {
        if (s == null) s = new MemoryConfiguration();
        List<String> problems = new ArrayList<>();

        Source source;
        try {
            source = Source.valueOf(s.getString("groups-from", "auto").strip().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            problems.add("trunk.groups-from is \"" + s.getString("groups-from") + "\", which isn't auto, multiverse-inventories or config. Using auto.");
            source = Source.AUTO;
        }

        Map<String, Set<String>> groups = new HashMap<>();
        ConfigurationSection gs = s.getConfigurationSection("groups");
        if (gs != null) for (String g : gs.getKeys(false)) groups.put(g, new LinkedHashSet<>(gs.getStringList(g)));

        Map<String, Audience> groupAud = new HashMap<>(), worldAud = new HashMap<>();
        readAudiences(s.getConfigurationSection("audiences.groups"), groupAud, "trunk.audiences.groups", problems);
        readAudiences(s.getConfigurationSection("audiences.worlds"), worldAud, "trunk.audiences.worlds", problems);

        List<Link> links = new ArrayList<>();
        for (Object o : s.getList("links", List.of())) {
            List<String> pair = o instanceof List<?> l ? l.stream().map(String::valueOf).toList()
                    : List.of(String.valueOf(o).split("\\s*\\+\\s*"));
            if (pair.size() != 2) {
                problems.add("trunk.links has an entry that isn't two groups: " + o + ". It's skipped.");
                continue;
            }
            links.add(new Link(pair.get(0).strip(), pair.get(1).strip()));
        }

        Map<String, String> names = new HashMap<>();
        ConfigurationSection ns = s.getConfigurationSection("names");
        if (ns != null) for (String k : ns.getKeys(false)) names.put(k, ns.getString(k));

        Set<GameMode> modes = EnumSet.noneOf(GameMode.class);
        for (String m : s.getStringList("game-modes")) {
            try {
                modes.add(GameMode.valueOf(m.strip().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                problems.add("trunk.game-modes has \"" + m + "\", which isn't a game mode.");
            }
        }
        if (modes.isEmpty()) modes = EnumSet.of(GameMode.SURVIVAL, GameMode.ADVENTURE);

        ConfigurationSection ls = s.getConfigurationSection("look");
        if (ls == null) ls = new MemoryConfiguration();
        Look look = new Look(ls.getString("trunk-item-model", ""), ls.getString("key-item-model", ""),
                ls.getString("block-model", ""), ls.getString("sound", "minecraft:block.ender_chest.open"),
                ls.getString("particle", "minecraft:portal"));

        ConfigurationSection rs = s.getConfigurationSection("recipes");
        if (rs == null) rs = new MemoryConfiguration();
        Map<String, RecipeSpec> custom = new HashMap<>();
        ConfigurationSection cs = rs.getConfigurationSection("custom");
        if (cs != null) {
            for (String name : cs.getKeys(false)) {
                ConfigurationSection r = cs.getConfigurationSection(name);
                if (r == null) continue;
                Map<Character, String> ingredients = new HashMap<>();
                ConfigurationSection is = r.getConfigurationSection("ingredients");
                if (is != null) for (String k : is.getKeys(false)) {
                    if (k.length() != 1) problems.add("Recipe " + name + ": ingredient key \"" + k + "\" must be one character.");
                    else ingredients.put(k.charAt(0), is.getString(k));
                }
                custom.put(name, new RecipeSpec(r.getStringList("shape"), ingredients));
            }
        }
        Map<String, Map<String, String>> perGroup = new HashMap<>();
        ConfigurationSection pg = rs.getConfigurationSection("groups");
        if (pg != null) {
            for (String g : pg.getKeys(false)) {
                Map<String, String> m = new HashMap<>();
                ConfigurationSection gsec = pg.getConfigurationSection(g);
                if (gsec == null) continue;
                if (gsec.isString("key")) m.put("key", gsec.getString("key"));
                if (gsec.isString("trunk")) m.put("trunk", gsec.getString("trunk"));
                perGroup.put(g, m);
            }
        }
        Recipes recipes = new Recipes(rs.getString("key", "standard"), rs.getString("trunk", "standard"), custom, perGroup);

        return new TrunkConfig(source, groups, groupAud, worldAud, List.copyOf(links), names, modes, look, recipes,
                s.getBoolean("admin-copies", true), List.copyOf(problems));
    }

    private static void readAudiences(ConfigurationSection s, Map<String, Audience> into, String where, List<String> problems) {
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            String v = s.getString(k);
            Audience a = Audience.parse(v);
            if (a == Audience.UNLABELLED) problems.add(where + "." + k + " is \"" + v + "\", which isn't kids, adults or mixed. It counts as unlabelled.");
            else into.put(k, a);
        }
    }
}
