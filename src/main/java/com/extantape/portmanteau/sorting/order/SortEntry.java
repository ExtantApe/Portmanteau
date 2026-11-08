package com.extantape.portmanteau.sorting.order;

import com.extantape.portmanteau.transaction.ItemKey;

import java.util.Comparator;

/** One kind of item being sorted, with what the order compares. */
public record SortEntry(ItemFacts facts, String customName, String lore, ItemKey key) {

    /**
     * Compares by each part of the method in turn, then by the item's full identity, so the order is the same every time
     * and sorting a sorted container changes nothing.
     */
    public static Comparator<SortEntry> comparator(OrderSpec spec, StickyCheck sticky) {
        return (a, b) -> {
            for (SortMethod.Criterion c : spec.method().criteria()) {
                int r = switch (c) {
                    case CATEGORY -> Integer.compare(spec.rank(a.facts.category()), spec.rank(b.facts.category()));
                    case KEEP_CATEGORY_ORDER -> Integer.compare(a.facts.line(), b.facts.line());
                    case ITEMS_FIRST -> Boolean.compare(a.facts.block(), b.facts.block());
                    case BLOCKS_FIRST -> Boolean.compare(!a.facts.block(), !b.facts.block());
                    case TIER -> Integer.compare(b.facts.tier(), a.facts.tier());
                    case NAME -> a.facts.baseName().compareTo(b.facts.baseName());
                    case COLOR -> Integer.compare(a.facts.color(), b.facts.color());
                    case CUSTOM_NAME -> compareText(a.customName, b.customName);
                    case LORE -> compareText(a.lore, b.lore);
                };
                if (r != 0) return r;
                // A sticky category keeps its file's own order, straight after the category itself.
                if (c == SortMethod.Criterion.CATEGORY && a.facts.category() == b.facts.category()
                        && sticky.sticky(a.facts.category())) {
                    int line = Integer.compare(a.facts.line(), b.facts.line());
                    if (line != 0) return line;
                }
            }
            int path = a.facts.path().compareTo(b.facts.path());
            if (path != 0) return path;
            return a.key.compareTo(b.key);
        };
    }

    private static int compareText(String a, String b) {
        if (a == null || b == null) return a == null ? (b == null ? 0 : -1) : 1;
        return String.CASE_INSENSITIVE_ORDER.compare(a, b);
    }

    @FunctionalInterface
    public interface StickyCheck {
        boolean sticky(int category);
    }
}
