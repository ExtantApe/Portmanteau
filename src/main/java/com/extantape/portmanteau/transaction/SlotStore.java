package com.extantape.portmanteau.transaction;

/** Slots that are read and written as a whole, such as a chest's contents. */
public interface SlotStore<T> {
    /** The current contents. May return live objects; the transaction copies what it keeps. */
    T[] read();

    /** Replaces every slot in one call. */
    void write(T[] contents);

    /** The most any slot accepts, whatever the item allows. */
    int slotLimit();

    /** For messages and logs, such as "chest at world 10 64 -3". */
    String describe();
}
