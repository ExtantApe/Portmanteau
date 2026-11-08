package com.extantape.portmanteau.sorting.order;

import com.extantape.portmanteau.transaction.ItemCodec;
import com.extantape.portmanteau.transaction.ItemKey;
import com.extantape.portmanteau.transaction.PlanRefused;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Plans one run of slots: merges matching stacks, orders them, and packs them from the first slot. */
public final class SectionPlanner {
    private SectionPlanner() {}

    /**
     * Rearranges {@code slots[from, to)} in place.
     *
     * @param describe gives the order's view of a stack; called once per kind of item
     */
    public static <T> void plan(T[] slots, int from, int to, Comparator<SortEntry> order, ItemCodec<T> codec,
                                int slotLimit, Function<T, SortEntry> describe) throws PlanRefused {
        final class Group {
            final T sample;
            final SortEntry entry;
            long total;

            Group(T sample, SortEntry entry) {
                this.sample = sample;
                this.entry = entry;
            }
        }
        Map<ItemKey, Group> groups = new LinkedHashMap<>();
        for (int i = from; i < to; i++) {
            T item = slots[i];
            if (item == null || codec.isEmpty(item)) continue;
            ItemKey key = codec.key(item);
            Group g = groups.get(key);
            if (g == null) {
                g = new Group(item, describe.apply(item));
                groups.put(key, g);
            }
            g.total += codec.amount(item);
        }
        List<Group> ordered = new ArrayList<>(groups.values());
        ordered.sort((a, b) -> order.compare(a.entry, b.entry));

        int slot = from;
        for (Group g : ordered) {
            int per = Math.min(codec.maxStack(g.sample), slotLimit);
            if (per < 1) throw new PlanRefused("an item that can't be stacked at all");
            long left = g.total;
            while (left > 0) {
                if (slot >= to) throw new PlanRefused("the items wouldn't fit back after merging");
                int n = (int) Math.min(left, per);
                slots[slot++] = codec.withAmount(g.sample, n);
                left -= n;
            }
        }
        while (slot < to) slots[slot++] = null;
    }
}
