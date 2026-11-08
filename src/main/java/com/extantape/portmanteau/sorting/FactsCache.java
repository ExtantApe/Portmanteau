package com.extantape.portmanteau.sorting;

import com.extantape.portmanteau.sorting.category.Categories;
import com.extantape.portmanteau.sorting.category.CategoryFile;
import com.extantape.portmanteau.sorting.order.ItemFacts;
import com.extantape.portmanteau.sorting.order.SortEntry;
import com.extantape.portmanteau.transaction.ItemKey;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Every item's category and name parts, worked out once from the server's own item registry and tags, so it follows
 * whatever Minecraft version the server runs, with no list of materials in the code.
 */
public final class FactsCache {
    private final Categories categories;
    private final Map<Material, ItemFacts> facts = new EnumMap<>(Material.class);
    private final List<String> problems = new ArrayList<>();
    private final int items;

    public FactsCache(Categories categories) {
        this.categories = categories;
        Map<Material, Set<String>> tagsOf = new HashMap<>();
        Set<String> knownTags = new HashSet<>();
        for (Tag<Material> tag : Bukkit.getTags(Tag.REGISTRY_ITEMS, Material.class)) {
            String key = tag.getKey().toString();
            knownTags.add(key);
            for (Material m : tag.getValues()) tagsOf.computeIfAbsent(m, k -> new HashSet<>()).add(key);
        }
        int count = 0;
        for (Material m : Registry.MATERIAL) {
            if (!m.isItem() || m.isAir()) continue;
            facts.put(m, compute(m, tagsOf.getOrDefault(m, Set.of())));
            count++;
        }
        items = count;
        for (CategoryFile f : categories.files()) {
            problems.addAll(f.warnings());
            for (CategoryFile.Rule r : f.rules()) {
                if (r.kind() == CategoryFile.Kind.TAG && !knownTags.contains(r.text())) {
                    problems.add(f.fileName() + ": this server has no item tag #" + r.text() + ", so that line matches nothing.");
                }
            }
        }
    }

    private ItemFacts compute(Material m, Set<String> tags) {
        String path = m.getKey().getKey();
        var placement = categories.place(path, tags);
        return ItemFacts.of(path, placement.category(), placement.line(), m.isBlock());
    }

    public ItemFacts facts(Material m) {
        ItemFacts f = facts.get(m);
        return f != null ? f : compute(m, Set.of());
    }

    public Categories categories() {
        return categories;
    }

    public List<String> problems() {
        return problems;
    }

    public int items() {
        return items;
    }

    /** Counts the items in each category, for the status command and the every-item check. */
    public int[] census() {
        int[] counts = new int[categories.size()];
        for (ItemFacts f : facts.values()) counts[f.category()]++;
        return counts;
    }

    /** Describes stacks for the order, using the transaction's identity so nothing is serialized twice. */
    public Function<ItemStack, SortEntry> describer(Function<ItemStack, ItemKey> keys) {
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        return item -> {
            Component name = item.getData(DataComponentTypes.CUSTOM_NAME);
            var lore = item.getData(DataComponentTypes.LORE);
            String loreText = null;
            if (lore != null && !lore.lines().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Component line : lore.lines()) sb.append(plain.serialize(line)).append('\n');
                loreText = sb.toString();
            }
            return new SortEntry(facts(item.getType()), name == null ? null : plain.serialize(name), loreText, keys.apply(item));
        };
    }
}
