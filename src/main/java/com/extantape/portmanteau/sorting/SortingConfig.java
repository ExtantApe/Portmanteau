package com.extantape.portmanteau.sorting;

import com.extantape.portmanteau.sorting.order.SortMethod;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The {@code sorting} section of config.yml, read once and checked. Problems fall back to safe values and are listed. */
public record SortingConfig(
        Mode containers,
        Mode inventory,
        boolean hotbarByDefault,
        int playerChoices,
        SortMethod method,
        Map<String, Preset> presets,
        String defaultPreset,
        Hotkeys hotkeys,
        boolean quickTransfer,
        Set<String> disabledWorlds,
        Set<String> allowedHolders,
        boolean claims,
        List<String> problems) {

    /** Whether something sorts by itself, and whether players may change that. */
    public enum Mode {
        /** Always, and players can't turn it off. */
        FORCED,
        /** On unless a player turns it off. */
        DEFAULT_ON,
        /** Off unless a player turns it on. */
        DEFAULT_OFF,
        /** Never by itself. Hotkeys and commands still work. */
        OFF;

        static Mode parse(String text, Mode fallback, String where, List<String> problems) {
            if (text == null) return fallback;
            try {
                return valueOf(text.strip().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException e) {
                problems.add(where + " is \"" + text + "\", which isn't forced, default-on, default-off or off. Using "
                        + fallback.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".");
                return fallback;
            }
        }

        public boolean playerMayChange() {
            return this == DEFAULT_ON || this == DEFAULT_OFF;
        }

        public boolean defaultOn() {
            return this == FORCED || this == DEFAULT_ON;
        }
    }

    /** How much players may choose for themselves. Each level includes the ones below it. */
    public static final int CHOICES_NONE = 0, CHOICES_SWITCHES = 25, CHOICES_PRESET = 50, CHOICES_SECTIONS = 75,
            CHOICES_ALL = 100;

    /** A named order players can pick: a method, and categories to put first. */
    public record Preset(String id, String name, SortMethod method, List<String> first) {}

    public record Hotkeys(boolean middleClick, boolean shiftClick, boolean doubleClick, boolean shiftRightClick,
                          long cooldownMillis) {}

    public static SortingConfig read(ConfigurationSection s) {
        if (s == null) s = new MemoryConfiguration();
        List<String> problems = new ArrayList<>();
        Mode containers = Mode.parse(modeText(s.get("containers")), Mode.DEFAULT_ON, "sorting.containers", problems);
        Mode inventory = Mode.parse(modeText(s.get("inventory")), Mode.DEFAULT_ON, "sorting.inventory", problems);

        int choices = readChoices(s.get("player-choices", CHOICES_ALL), problems);

        SortMethod method = SortMethod.parse(s.getString("method", SortMethod.DEFAULT));
        if (!method.unknown().isEmpty()) problems.add("sorting.method has parts that aren't known and are skipped: " + method.unknown());
        if (method.criteria().isEmpty()) {
            problems.add("sorting.method has nothing usable. Using " + SortMethod.DEFAULT + ".");
            method = SortMethod.parse(SortMethod.DEFAULT);
        }

        Map<String, Preset> presets = new LinkedHashMap<>();
        presets.put("server", new Preset("server", "Server order", method, List.of()));
        ConfigurationSection ps = s.getConfigurationSection("presets");
        if (ps != null) {
            for (String id : ps.getKeys(false)) {
                ConfigurationSection p = ps.getConfigurationSection(id);
                if (p == null) continue;
                SortMethod m = p.isString("method") ? SortMethod.parse(p.getString("method")) : method;
                if (!m.unknown().isEmpty()) problems.add("sorting.presets." + id + ".method has unknown parts: " + m.unknown());
                if (m.criteria().isEmpty()) m = method;
                presets.put(id, new Preset(id, p.getString("name", id), m, p.getStringList("first")));
            }
        }
        String defaultPreset = s.getString("default-preset", "server");
        if (!presets.containsKey(defaultPreset)) {
            problems.add("sorting.default-preset is \"" + defaultPreset + "\", which isn't a preset. Using server.");
            defaultPreset = "server";
        }

        ConfigurationSection hk = s.getConfigurationSection("hotkeys");
        if (hk == null) hk = new MemoryConfiguration();
        long cooldown = Math.max(0, hk.getLong("cooldown-ms", 200));
        Hotkeys hotkeys = new Hotkeys(hk.getBoolean("middle-click", true), hk.getBoolean("shift-click", true),
                hk.getBoolean("double-click", true), hk.getBoolean("shift-right-click", true), cooldown);

        return new SortingConfig(containers, inventory, s.getBoolean("hotbar-by-default", false), choices, method,
                Map.copyOf(presets), defaultPreset, hotkeys, s.getBoolean("quick-transfer", false),
                Set.copyOf(s.getStringList("disabled-worlds")), Set.copyOf(s.getStringList("allowed-holders")),
                s.getBoolean("claims", true), List.copyOf(problems));
    }

    /** YAML reads a bare off as false and on as true, so those mean off and default-on. */
    private static String modeText(Object value) {
        if (value instanceof Boolean b) return b ? "default-on" : "off";
        return value == null ? null : String.valueOf(value);
    }

    private static int readChoices(Object value, List<String> problems) {
        int n;
        if (value instanceof Number num) {
            n = num.intValue();
        } else {
            String text = String.valueOf(value).strip().toLowerCase(Locale.ROOT).replace("%", "");
            n = switch (text) {
                case "none" -> CHOICES_NONE;
                case "switches" -> CHOICES_SWITCHES;
                case "preset", "presets" -> CHOICES_PRESET;
                case "sections" -> CHOICES_SECTIONS;
                case "all", "everything" -> CHOICES_ALL;
                default -> {
                    try {
                        yield Integer.parseInt(text);
                    } catch (NumberFormatException e) {
                        problems.add("sorting.player-choices is \"" + value + "\", which isn't 0 to 100. Using 0, so players choose nothing.");
                        yield CHOICES_NONE;
                    }
                }
            };
        }
        if (n < 0 || n > 100) {
            problems.add("sorting.player-choices is " + n + ", outside 0 to 100. Using " + Math.clamp(n, 0, 100) + ".");
            n = Math.clamp(n, 0, 100);
        }
        // Round down to the nearest level, so 60 means "a preset" and nothing from the next level.
        return n >= 100 ? 100 : n >= 75 ? 75 : n >= 50 ? 50 : n >= 25 ? 25 : 0;
    }

    public Preset preset(String id) {
        Preset p = id == null ? null : presets.get(id);
        return p != null ? p : presets.get(defaultPreset);
    }
}
