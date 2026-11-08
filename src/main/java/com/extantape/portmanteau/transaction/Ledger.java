package com.extantape.portmanteau.transaction;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** A count of every item in some slots, by full identity. Two ledgers are equal when nothing was lost or added. */
public final class Ledger {
    private final Map<ItemKey, Long> counts = new HashMap<>();

    public static <T> Ledger of(T[] slots, ItemCodec<T> codec) {
        Ledger ledger = new Ledger();
        for (T item : slots) {
            if (codec.isEmpty(item)) continue;
            ledger.counts.merge(codec.key(item), (long) codec.amount(item), Long::sum);
        }
        return ledger;
    }

    public long total() {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    public int kinds() {
        return counts.size();
    }

    /** A short, readable account of what differs, for the console. */
    public String difference(Ledger after) {
        int missing = 0, extra = 0;
        long missingItems = 0, extraItems = 0;
        for (var e : counts.entrySet()) {
            long now = after.counts.getOrDefault(e.getKey(), 0L);
            if (now < e.getValue()) { missing++; missingItems += e.getValue() - now; }
        }
        for (var e : after.counts.entrySet()) {
            long before = counts.getOrDefault(e.getKey(), 0L);
            if (e.getValue() > before) { extra++; extraItems += e.getValue() - before; }
        }
        return missingItems + " item(s) of " + missing + " kind(s) would go missing and " + extraItems + " item(s) of "
                + extra + " kind(s) would appear";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Ledger l && counts.equals(l.counts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(counts);
    }
}
