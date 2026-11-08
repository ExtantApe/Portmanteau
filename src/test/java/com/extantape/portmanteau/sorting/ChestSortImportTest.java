package com.extantape.portmanteau.sorting;

import com.extantape.portmanteau.sorting.category.CategoryFile;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The import's file filter, preset fitting and comment-keeping save, against the real default config.yml. */
class ChestSortImportTest {
    private static final Path DEFAULT_CONFIG = Path.of("src/main/resources/config.yml");
    // Made-up category names: three the default presets use, two they don't, and none of tools, armor,
    // stone-and-building or colorful-blocks.
    private static final Set<String> IMPORTED = Set.of("wood", "weapons", "food", "ores", "redstone");

    @Test
    void onlyNumberedFilesAreCategories() {
        assertTrue(CategoryFile.isCategoryFileName("010-tools.txt"));
        assertTrue(CategoryFile.isCategoryFileName("900-Weapons.TXT"));
        assertTrue(CategoryFile.isCategoryFileName("5.txt"));
        assertFalse(CategoryFile.isCategoryFileName("_ReadMe - Category files.txt"));
        assertFalse(CategoryFile.isCategoryFileName("notes.txt"));
        assertFalse(CategoryFile.isCategoryFileName("010-tools.txt.bak"));
    }

    @Test
    void presetsFitTheImportedCategories() throws Exception {
        YamlConfiguration yaml = ImportEdits.load(DEFAULT_CONFIG);
        var sorting = yaml.getConfigurationSection("sorting");
        sorting.set("default-preset", "tools-first");
        List<String> report = ImportEdits.fitPresets(sorting, IMPORTED);

        assertEquals(List.of("weapons"), sorting.getStringList("presets.tools-first.first"));
        assertEquals(List.of("wood"), sorting.getStringList("presets.blocks-first.first"));
        assertEquals(List.of("food"), sorting.getStringList("presets.food-first.first"));
        assertTrue(sorting.isConfigurationSection("presets.a-to-z"), "a preset with its own method stays");
        assertEquals("tools-first", sorting.getString("default-preset"));
        assertEquals(2, report.size(), String.join("\n", report));

        // Every category a preset still names exists, so a reload has nothing to warn about.
        for (String id : sorting.getConfigurationSection("presets").getKeys(false)) {
            for (String c : sorting.getStringList("presets." + id + ".first")) assertTrue(IMPORTED.contains(c), id + " names " + c);
        }
        // Fitting again changes nothing.
        assertEquals(List.of(), ImportEdits.fitPresets(sorting, IMPORTED));
    }

    @Test
    void aPresetWithNothingLeftIsRemovedAndTheDefaultFallsBack() throws Exception {
        YamlConfiguration yaml = ImportEdits.load(DEFAULT_CONFIG);
        var sorting = yaml.getConfigurationSection("sorting");
        sorting.set("default-preset", "tools-first");
        List<String> report = ImportEdits.fitPresets(sorting, Set.of("ores"));

        assertFalse(sorting.contains("presets.tools-first"));
        assertFalse(sorting.contains("presets.blocks-first"));
        assertFalse(sorting.contains("presets.food-first"));
        assertTrue(sorting.contains("presets.a-to-z"));
        assertEquals("server", sorting.getString("default-preset"));
        assertTrue(report.stream().anyMatch(l -> l.contains("default is now server")), String.join("\n", report));

        var config = SortingConfig.read(sorting);
        assertEquals("server", config.defaultPreset());
    }

    @Test
    void savingKeepsEveryComment(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.copy(DEFAULT_CONFIG, file);
        YamlConfiguration yaml = ImportEdits.load(file);
        yaml.set("sorting.method", "{category},{name}");
        yaml.set("sorting.disabled-worlds", List.of("some_world"));
        ImportEdits.fitPresets(yaml.getConfigurationSection("sorting"), IMPORTED);
        ImportEdits.save(yaml, file);

        List<String> before = comments(DEFAULT_CONFIG);
        List<String> after = comments(file);
        assertFalse(before.isEmpty());
        assertEquals(before, after);
        assertFalse(Files.exists(dir.resolve("config.yml.tmp")));
        YamlConfiguration reread = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals("{category},{name}", reread.getString("sorting.method"));
        assertEquals(List.of("some_world"), reread.getStringList("sorting.disabled-worlds"));
    }

    private static List<String> comments(Path file) throws Exception {
        return Files.readAllLines(file).stream().map(String::strip).filter(l -> l.startsWith("#")).toList();
    }
}
