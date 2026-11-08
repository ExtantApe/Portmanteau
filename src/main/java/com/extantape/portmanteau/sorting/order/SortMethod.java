package com.extantape.portmanteau.sorting.order;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A sorting method string in ChestSort's format, such as {@code {category},{itemsFirst},{name},{color},{customName}}.
 * Items are compared by each part in turn.
 */
public record SortMethod(List<Criterion> criteria, List<String> unknown) {
    public enum Criterion {
        /** The category's position in the category files. */
        CATEGORY("category"),
        /** The line the item matched inside its category, for the category file's own order. */
        KEEP_CATEGORY_ORDER("keepcategoryorder"),
        /** Items before blocks. */
        ITEMS_FIRST("itemsfirst"),
        /** Blocks before items. */
        BLOCKS_FIRST("blocksfirst"),
        /** Material tier, best first: netherite, diamond, iron, gold, copper, stone or chainmail, wood or leather. */
        TIER("tier"),
        /** The item's name without its colour, so every wool sits together. */
        NAME("name"),
        /** Colour, in the order of the dyes in the creative menu. */
        COLOR("color"),
        /** The custom name, if any. Unnamed items first. */
        CUSTOM_NAME("customname"),
        /** The lore text, if any. Items without lore first. */
        LORE("lore");

        private final String token;

        Criterion(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    public static final String DEFAULT = "{category},{itemsFirst},{name},{color},{customName}";

    public static SortMethod parse(String text) {
        List<Criterion> criteria = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        if (text == null) text = DEFAULT;
        for (String part : text.split(",")) {
            String token = part.strip().replace("{", "").replace("}", "").toLowerCase(Locale.ROOT);
            if (token.isEmpty()) continue;
            Criterion found = null;
            for (Criterion c : Criterion.values()) {
                if (c.token.equals(token)) found = c;
            }
            if (found == null) unknown.add(part.strip());
            else if (!criteria.contains(found)) criteria.add(found);
        }
        return new SortMethod(List.copyOf(criteria), List.copyOf(unknown));
    }

    public String text() {
        StringBuilder sb = new StringBuilder();
        for (Criterion c : criteria) {
            if (!sb.isEmpty()) sb.append(',');
            sb.append('{').append(switch (c) {
                case KEEP_CATEGORY_ORDER -> "keepCategoryOrder";
                case ITEMS_FIRST -> "itemsFirst";
                case BLOCKS_FIRST -> "blocksFirst";
                case CUSTOM_NAME -> "customName";
                default -> c.token;
            }).append('}');
        }
        return sb.toString();
    }
}
