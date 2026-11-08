package com.extantape.portmanteau.transaction;

import java.util.Arrays;

/**
 * What an item is, apart from how many there are: the full serialized item at an amount of one, so every component
 * counts. Two stacks with the same key are the same item. Keys order by their bytes, which gives sorting a stable,
 * repeatable tie-break.
 */
public final class ItemKey implements Comparable<ItemKey> {
    private final byte[] bytes;
    private final int hash;

    public ItemKey(byte[] bytes) {
        this.bytes = bytes.clone();
        this.hash = Arrays.hashCode(this.bytes);
    }

    public int length() {
        return bytes.length;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ItemKey k && hash == k.hash && Arrays.equals(bytes, k.bytes);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public int compareTo(ItemKey o) {
        return Arrays.compareUnsigned(bytes, o.bytes);
    }
}
