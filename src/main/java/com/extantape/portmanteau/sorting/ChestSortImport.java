package com.extantape.portmanteau.sorting;

import com.extantape.portmanteau.sorting.order.SortMethod;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads a ChestSort folder on this server and carries over what it can be sure of: the category files, which are a
 * documented format, plus a sorting method string and a list of disabled worlds, recognised by their shape. Every other
 * setting is listed for the admin to set by hand, rather than guessed at. The current categories are backed up first.
 */
final class ChestSortImport {
    private ChestSortImport() {}

    static List<String> run(JavaPlugin plugin, SortingFeature feature) {
        List<String> report = new ArrayList<>();
        File source = new File(plugin.getDataFolder().getParentFile(), "ChestSort");
        if (!source.isDirectory()) {
            report.add("There's no plugins/ChestSort folder on this server, so there's nothing to import.");
            return report;
        }

        File theirs = new File(source, "categories");
        File[] files = theirs.listFiles((d, n) -> n.toLowerCase().endsWith(".txt"));
        if (files != null && files.length > 0) {
            File ours = feature.categoryFolder();
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            File backup = new File(plugin.getDataFolder(), "categories-before-import-" + stamp);
            try {
                if (ours.isDirectory()) {
                    Files.move(ours.toPath(), backup.toPath());
                    report.add("Moved the current categories to " + backup.getName() + ".");
                }
                if (!ours.mkdirs() && !ours.isDirectory()) throw new IOException("couldn't make " + ours);
                for (File f : files) Files.copy(f.toPath(), new File(ours, f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                report.add("Copied " + files.length + " category files from ChestSort.");
            } catch (IOException e) {
                report.add("Couldn't copy the category files: " + e.getMessage() + ". Nothing else was changed.");
                return report;
            }
        } else {
            report.add("ChestSort has no category files, so the current categories stay.");
        }

        File configFile = new File(source, "config.yml");
        if (configFile.isFile()) {
            YamlConfiguration cs = YamlConfiguration.loadConfiguration(configFile);
            var ours = plugin.getConfig();
            List<String> notCarried = new ArrayList<>();
            for (Map.Entry<String, Object> e : cs.getValues(true).entrySet()) {
                if (e.getValue() instanceof org.bukkit.configuration.ConfigurationSection) continue;
                String key = e.getKey();
                Object value = e.getValue();
                if (value instanceof String s && s.contains("{") && key.toLowerCase().contains("method")) {
                    SortMethod m = SortMethod.parse(s);
                    ours.set("sorting.method", m.text());
                    report.add("Sorting method: " + s + (m.unknown().isEmpty() ? "" : " (these parts aren't supported and were left out: " + m.unknown() + ")"));
                } else if (value instanceof List<?> list && key.toLowerCase().contains("world") && key.toLowerCase().contains("disabled")) {
                    ours.set("sorting.disabled-worlds", list.stream().map(String::valueOf).toList());
                    report.add("Disabled worlds: " + list.size() + ".");
                } else {
                    notCarried.add(key + ": " + value);
                }
            }
            plugin.saveConfig();
            if (!notCarried.isEmpty()) {
                report.add("Not carried over. Check each one and set its match in Portmanteau's config.yml by hand:");
                for (String line : notCarried) report.add("  " + line);
            }
        } else {
            report.add("ChestSort has no config.yml, so only categories were imported.");
        }
        report.addAll(feature.reload());
        report.add("Done. Check the order with /portmanteau categories.");
        return report;
    }
}
