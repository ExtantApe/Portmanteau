package com.extantape.portmanteau.transaction;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {
    private static final Logger LOG = Logger.getLogger("test");
    private final FakeItem.Codec codec = new FakeItem.Codec();

    private static FakeItem item(String type, int amount) {
        return new FakeItem(type, "", amount, 64);
    }

    private static FakeItem[] reversed(FakeItem[] s) {
        FakeItem[] out = new FakeItem[s.length];
        for (int i = 0; i < s.length; i++) out[i] = s[s.length - 1 - i];
        return out;
    }

    @Test
    void writesAGoodPlanOnceAndChecksIt() {
        var store = new FakeItem.Store(new FakeItem[]{item("a", 1), null, item("b", 2)});
        var r = Transaction.apply(store, codec, TransactionTest::reversed, Set.of(), LOG);
        assertEquals(Transaction.Outcome.CHANGED, r.outcome());
        assertEquals(1, store.writes);
        assertEquals("b", store.slots[0].type());
    }

    @Test
    void writesNothingWhenThePlanChangesNothing() {
        var store = new FakeItem.Store(new FakeItem[]{item("a", 1), item("b", 2)});
        var r = Transaction.apply(store, codec, s -> s, Set.of(), LOG);
        assertEquals(Transaction.Outcome.UNCHANGED, r.outcome());
        assertEquals(0, store.writes);
    }

    @Test
    void refusesAPlanThatLosesAnItem() {
        var store = new FakeItem.Store(new FakeItem[]{item("a", 3), item("b", 2)});
        var r = Transaction.apply(store, codec, s -> new FakeItem[]{item("a", 3), null}, Set.of(), LOG);
        assertEquals(Transaction.Outcome.REFUSED, r.outcome());
        assertEquals(0, store.writes);
    }

    @Test
    void refusesAPlanThatSwapsDataBetweenStacksOfOneType() {
        // Same type and amounts, but the enchantment moved to the other stack: counting by type alone would miss it.
        var store = new FakeItem.Store(new FakeItem[]{new FakeItem("sword", "sharpness", 1, 1), new FakeItem("sword", "", 1, 1)});
        var r = Transaction.apply(store, codec,
                s -> new FakeItem[]{new FakeItem("sword", "", 1, 1), new FakeItem("sword", "", 1, 1)}, Set.of(), LOG);
        assertEquals(Transaction.Outcome.REFUSED, r.outcome());
    }

    @Test
    void refusesOverStackedItemsWithoutPlanning() {
        var store = new FakeItem.Store(new FakeItem[]{new FakeItem("spawner", "", 3, 1), null});
        var r = Transaction.apply(store, codec, s -> { fail("planned"); return s; }, Set.of(), LOG);
        assertEquals(Transaction.Outcome.REFUSED, r.outcome());
    }

    @Test
    void refusesStacksOverTheInventorysOwnLimit() {
        var store = new FakeItem.Store(new FakeItem[]{item("a", 20), null});
        store.limit = 16;
        var r = Transaction.apply(store, codec, TransactionTest::reversed, Set.of(), LOG);
        assertEquals(Transaction.Outcome.REFUSED, r.outcome());
        assertEquals(0, store.writes);
    }

    @Test
    void refusesAPlanThatMovesAFixedSlot() {
        var store = new FakeItem.Store(new FakeItem[]{item("saddle", 1), item("a", 1), item("b", 1)});
        var r = Transaction.apply(store, codec, TransactionTest::reversed, Set.of(0), LOG);
        assertEquals(Transaction.Outcome.REFUSED, r.outcome());
    }

    @Test
    void stopsWhenTheContentsChangeBeforeTheWrite() {
        var store = new FakeItem.Store(new FakeItem[]{item("a", 1), item("b", 1)}) {
            int reads;
            @Override public FakeItem[] read() {
                // A hopper takes an item after the snapshot.
                if (++reads == 2) slots[0] = null;
                return super.read();
            }
        };
        var r = Transaction.apply(store, codec, TransactionTest::reversed, Set.of(), LOG);
        assertEquals(Transaction.Outcome.REFUSED, r.outcome());
        assertEquals(0, store.writes);
    }

    @Test
    void putsTheOriginalBackWhenTheWriteComesOutWrong() {
        FakeItem[] start = {item("a", 1), item("b", 1)};
        var store = new FakeItem.Store(start) {
            @Override public void write(FakeItem[] contents) {
                writes++;
                if (writes == 1) { slots = new FakeItem[]{contents[0], null}; return; } // Corrupts the first write.
                super.write(contents);
            }
        };
        var r = Transaction.apply(store, codec, TransactionTest::reversed, Set.of(), LOG);
        assertEquals(Transaction.Outcome.RESTORED, r.outcome());
        assertTrue(Transaction.sameSlots(store.slots, start, codec));
    }

    @Test
    void reportsFailureWhenEvenTheRestoreFails() {
        var store = new FakeItem.Store(new FakeItem[]{item("a", 1), item("b", 1)}) {
            @Override public void write(FakeItem[] contents) { writes++; slots = new FakeItem[contents.length]; }
        };
        var r = Transaction.apply(store, codec, TransactionTest::reversed, Set.of(), LOG);
        assertEquals(Transaction.Outcome.FAILED, r.outcome());
        assertFalse(r.safe());
    }

    @Test
    void aThrowingPlannerChangesNothing() {
        var store = new FakeItem.Store(new FakeItem[]{item("a", 1)});
        var r = Transaction.apply(store, codec, s -> { throw new IllegalStateException("boom"); }, Set.of(), LOG);
        assertEquals(Transaction.Outcome.REFUSED, r.outcome());
        assertEquals(0, store.writes);
    }

    @Test
    void verifiedBytesPutsThePreviousValueBackOnABadWrite() {
        byte[][] cell = {{1, 2, 3}};
        var c = new VerifiedBytes.Cell() {
            int sets;
            @Override public byte[] get() { return cell[0]; }
            @Override public void set(byte[] b) { sets++; cell[0] = sets == 1 ? new byte[]{9} : b; }
        };
        var r = VerifiedBytes.replace(c, new byte[]{4, 5}, b -> true);
        assertEquals(VerifiedBytes.Outcome.RESTORED, r.outcome());
        assertArrayEquals(new byte[]{1, 2, 3}, cell[0]);
    }

    @Test
    void verifiedBytesRefusesBytesThatDontDecodeBack() {
        byte[][] cell = {{1}};
        var c = new VerifiedBytes.Cell() {
            @Override public byte[] get() { return cell[0]; }
            @Override public void set(byte[] b) { cell[0] = b; }
        };
        var r = VerifiedBytes.replace(c, new byte[]{2}, b -> false);
        assertEquals(VerifiedBytes.Outcome.REFUSED, r.outcome());
        assertArrayEquals(new byte[]{1}, cell[0]);
    }
}
