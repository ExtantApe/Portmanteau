package com.extantape.portmanteau.transaction;

import java.nio.charset.StandardCharsets;

/** A stand-in item for tests: a type, some data such as enchantments or a name, an amount and a stack limit. */
public record FakeItem(String type, String data, int amount, int max) {

    public static final class Codec implements ItemCodec<FakeItem> {
        @Override public boolean isEmpty(FakeItem item) { return item == null; }
        @Override public int amount(FakeItem item) { return item.amount; }
        @Override public int maxStack(FakeItem item) { return item.max; }
        @Override public ItemKey key(FakeItem item) {
            return new ItemKey((item.type + "|" + item.data + "|" + item.max).getBytes(StandardCharsets.UTF_8));
        }
        @Override public FakeItem copy(FakeItem item) { return new FakeItem(item.type, item.data, item.amount, item.max); }
        @Override public FakeItem withAmount(FakeItem item, int amount) { return new FakeItem(item.type, item.data, amount, item.max); }
        @Override public String describe(FakeItem item) { return item.toString(); }
    }

    /** Slots in memory, with hooks to misbehave. */
    public static class Store implements SlotStore<FakeItem> {
        public FakeItem[] slots;
        public int limit = 64;
        public int writes;

        public Store(FakeItem[] slots) { this.slots = slots.clone(); }
        @Override public FakeItem[] read() { return slots.clone(); }
        @Override public void write(FakeItem[] contents) { writes++; slots = contents.clone(); }
        @Override public int slotLimit() { return limit; }
        @Override public String describe() { return "test store"; }
    }
}
