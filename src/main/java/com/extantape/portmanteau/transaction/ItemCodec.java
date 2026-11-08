package com.extantape.portmanteau.transaction;

/**
 * How the transaction code reads one kind of item. Paper's item stacks in the plugin, plain records in the tests.
 * A null item is an empty slot.
 */
public interface ItemCodec<T> {
    boolean isEmpty(T item);

    int amount(T item);

    /** The most this item may hold in one stack, from its own data. */
    int maxStack(T item);

    /** The item's identity apart from its amount. Equal keys mean the stacks may merge. */
    ItemKey key(T item);

    /** A copy that shares nothing with the original. */
    T copy(T item);

    /** The same item at another amount. */
    T withAmount(T item, int amount);

    /** Everything about the item, for the console when an admin may need to restore it by hand. */
    String describe(T item);
}
