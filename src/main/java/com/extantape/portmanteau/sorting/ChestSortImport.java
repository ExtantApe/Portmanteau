package com.extantape.portmanteau.sorting;

import com.extantape.portmanteau.sorting.category.CategoryFile;
import com.extantape.portmanteau.sorting.order.SortMethod;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a ChestSort folder on this server and carries over what it can be sure of: the category files (only those whose
 * name starts with a number, as category files' names do), plus a sorting method string and a list of disabled worlds,
 * recognised by their shape. Every other setting is listed for the admin to set by hand, rather than guessed at. The
 * current categories are backed up first. Presets that name categories the import removed are fitted to the new ones,
 * and config.yml keeps its comments.
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
        File[] all = theirs.listFiles((d, n) -> n.toLowerCase().endsWith(".txt"));
        List<File> files = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (File f : all == null ? new File[0] : all) {
            if (CategoryFile.isCategoryFileName(f.getName())) files.add(f);
            else skipped.add(f.getName());
        }
        files.sort(java.util.Comparator.comparing(File::getName));
        Set<String> newIds = null;
        if (!skipped.isEmpty()) {
            report.add("Left out " + String.join(", ", skipped) + ": not a category file, because the name doesn't start with a number.");
        }
        if (!files.isEmpty()) {
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
                report.add("Copied " + files.size() + " category files from ChestSort.");
                newIds = new LinkedHashSet<>();
                for (File f : files) newIds.add(CategoryFile.parse(f.getName(), List.of()).id());
            } catch (IOException e) {
                report.add("Couldn't copy the category files: " + e.getMessage() + ". Nothing else was changed.");
                return report;
            }
        } else {
            report.add("ChestSort has no category files, so the current categories stay.");
        }

        File configFile = new File(source, "config.yml");
        Path ourConfig = new File(plugin.getDataFolder(), "config.yml").toPath();
        YamlConfiguration ours;
        try {
            ours = ImportEdits.load(ourConfig);
        } catch (IOException | InvalidConfigurationException e) {
            report.add("Couldn't read Portmanteau's config.yml (" + e.getMessage() + "), so the settings and presets weren't changed.");
            report.addAll(feature.reload());
            return report;
        }
        boolean changed = false;
        List<String> notCarried = new ArrayList<>();
        if (configFile.isFile()) {
            YamlConfiguration cs = YamlConfiguration.loadConfiguration(configFile);
            for (Map.Entry<String, Object> e : cs.getValues(true).entrySet()) {
                if (e.getValue() instanceof org.bukkit.configuration.ConfigurationSection) continue;
                String key = e.getKey();
                Object value = e.getValue();
                if (value instanceof String s && s.contains("{") && key.toLowerCase().contains("method")) {
                    SortMethod m = SortMethod.parse(s);
                    ours.set("sorting.method", m.text());
                    changed = true;
                    report.add("Sorting method: " + s + (m.unknown().isEmpty() ? "" : " (these parts aren't supported and were left out: " + m.unknown() + ")"));
                } else if (value instanceof List<?> list && key.toLowerCase().contains("world") && key.toLowerCase().contains("disabled")) {
                    ours.set("sorting.disabled-worlds", list.stream().map(String::valueOf).toList());
                    changed = true;
                    report.add("Disabled worlds: " + list.size() + ".");
                } else {
                    notCarried.add(key + ": " + value);
                }
            }
        } else {
            report.add("ChestSort has no config.yml, so only categories were imported.");
        }
        ConfigurationSection sorting = ours.getConfigurationSection("sorting");
        if (newIds != null && sorting != null) {
            List<String> presetChanges = ImportEdits.fitPresets(sorting, newIds);
            if (!presetChanges.isEmpty()) changed = true;
            report.addAll(presetChanges);
        }
        if (changed) {
            try {
                ImportEdits.save(ours, ourConfig);
                plugin.reloadConfig();
            } catch (IOException e) {
                report.add("Couldn't save Portmanteau's config.yml (" + e.getMessage() + "). The categories were copied; set the method and presets by hand.");
            }
        }
        if (!notCarried.isEmpty()) {
            report.add("Not carried over. Check each one and set its match in Portmanteau's config.yml by hand:");
            for (String line : notCarried) report.add("  " + line);
        }
        report.addAll(feature.reload());
        report.add("Done. Check the order with /portmanteau categories.");
        return report;
    }
}
