package com.extantape.portmanteau.sorting;

import com.extantape.portmanteau.sorting.category.Categories;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The parts of a ChestSort import that don't need a server, kept apart so they can be tested on their own. */
final class ImportEdits {
    private ImportEdits() {}

    /**
     * Makes the presets fit a new set of categories. Category names that no longer exist are left out of each preset's
     * {@code first} list. A preset left with nothing to change (no categories first and no method of its own) is
     * removed, and if it was the default, the default goes back to {@code server}. Returns one line per change.
     */
    static List<String> fitPresets(ConfigurationSection sorting, Set<String> categoryIds) {
        List<String> report = new ArrayList<>();
        ConfigurationSection presets = sorting.getConfigurationSection("presets");
        if (presets == null) return report;
        for (String id : List.copyOf(presets.getKeys(false))) {
            ConfigurationSection p = presets.getConfigurationSection(id);
            if (p == null || !p.isList("first")) continue;
            List<String> first = p.getStringList("first");
            List<String> kept = first.stream().filter(c -> categoryIds.contains(c) || Categories.CATCH_ALL.equals(c)).toList();
            if (kept.size() == first.size()) continue;
            List<String> gone = first.stream().filter(c -> !kept.contains(c)).toList();
            if (kept.isEmpty() && !p.isString("method")) {
                presets.set(id, null);
                report.add("Removed the preset " + id + ", because none of its categories exist now (" + String.join(", ", gone)
                        + "). Add it back under sorting.presets with the new category names if you want it.");
                if (id.equals(sorting.getString("default-preset"))) {
                    sorting.set("default-preset", "server");
                    report.add("It was the default preset, so the default is now server.");
                }
            } else {
                p.set("first", kept);
                report.add("The preset " + id + " no longer puts " + String.join(", ", gone)
                        + " first, because those categories don't exist now.");
            }
        }
        return report;
    }

    /** Reads a config file keeping its comments, so a later save writes them back. */
    static YamlConfiguration load(Path file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        yaml.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
        return yaml;
    }

    /** Writes a config file through a temporary file and a rename, so the file is never left half written. */
    static void save(YamlConfiguration yaml, Path file) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, yaml.saveToString(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
