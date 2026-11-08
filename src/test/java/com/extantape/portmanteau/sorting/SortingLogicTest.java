package com.extantape.portmanteau.sorting;

import com.extantape.portmanteau.sorting.category.Categories;
import com.extantape.portmanteau.sorting.category.CategoryFile;
import com.extantape.portmanteau.sorting.order.ItemFacts;
import com.extantape.portmanteau.sorting.order.OrderSpec;
import com.extantape.portmanteau.sorting.order.SectionPlanner;
import com.extantape.portmanteau.sorting.order.SortEntry;
import com.extantape.portmanteau.sorting.order.SortMethod;
import com.extantape.portmanteau.transaction.FakeItem;
import com.extantape.portmanteau.transaction.Ledger;
import com.extantape.portmanteau.transaction.Transaction;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class SortingLogicTest {
    private static final Logger LOG = Logger.getLogger("test");
    static { LOG.setLevel(Level.OFF); }

    private final FakeItem.Codec codec = new FakeItem.Codec();

    private static final Categories CATEGORIES = new Categories(List.of(
            CategoryFile.parse("100-tools.txt", List.of("display=Tools", "*_pickaxe", "*_axe", "# a comment")),
            CategoryFile.parse("200-wood.txt", List.of("#minecraft:logs", "*_planks")),
            CategoryFile.parse("300-ordered.txt", List.of("sticky=true", "zebra_thing", "apple_thing"))));

    private SortEntry entry(FakeItem item) {
        var p = CATEGORIES.place(item.type(), item.type().endsWith("_log") ? Set.of("minecraft:logs") : Set.of());
        return new SortEntry(ItemFacts.of(item.type(), p.category(), p.line(), item.type().endsWith("_log")),
                item.data().isEmpty() ? null : item.data(), null, codec.key(item));
    }

    private Comparator<SortEntry> order(String method, Integer... priorities) {
        return SortEntry.comparator(new OrderSpec(SortMethod.parse(method), List.of(priorities)), CATEGORIES::sticky);
    }

    private FakeItem[] sort(FakeItem[] slots, Comparator<SortEntry> order) throws com.extantape.portmanteau.transaction.PlanRefused {
        FakeItem[] copy = slots.clone();
        SectionPlanner.plan(copy, 0, copy.length, order, codec, 64, this::entry);
        return copy;
    }

    @Test
    void readsChestSortCategoryFiles() {
        CategoryFile tools = CATEGORIES.files().get(0);
        assertEquals("tools", tools.id());
        assertEquals("Tools", tools.display());
        assertEquals(2, tools.rules().size());
        assertTrue(CATEGORIES.files().get(2).sticky());
        assertEquals(0, CATEGORIES.place("diamond_pickaxe", Set.of()).category());
        assertEquals(1, CATEGORIES.place("cherry_log", Set.of("minecraft:logs")).category());
        assertEquals(3, CATEGORIES.place("some_new_block", Set.of()).category(), "unknown items go to the catch-all");
    }

    @Test
    void readsUpperCaseNamespacedAndWildcardLines() {
        var f = CategoryFile.parse("010-misc.txt", List.of("DIAMOND_PICKAXE", "minecraft:Stone", "*WOOL*", "bad line!"));
        assertEquals(0, f.match("diamond_pickaxe", Set.of()));
        assertEquals(1, f.match("stone", Set.of()));
        assertEquals(2, f.match("red_wool_carpet", Set.of()));
        assertEquals(1, f.warnings().size());
    }

    @Test
    void ordersByCategoryThenNameThenColour() throws Exception {
        FakeItem[] slots = {
                new FakeItem("red_wool", "", 5, 64), new FakeItem("oak_log", "", 3, 64), null,
                new FakeItem("iron_pickaxe", "", 1, 1), new FakeItem("white_wool", "", 2, 64)};
        FakeItem[] out = sort(slots, order(SortMethod.DEFAULT));
        assertEquals(List.of("iron_pickaxe", "oak_log", "white_wool", "red_wool"),
                java.util.Arrays.stream(out).filter(i -> i != null).map(FakeItem::type).toList());
        assertNull(out[4]);
    }

    @Test
    void stickyCategoriesKeepTheirFileOrder() throws Exception {
        FakeItem[] out = sort(new FakeItem[]{new FakeItem("apple_thing", "", 1, 64), new FakeItem("zebra_thing", "", 1, 64)},
                order(SortMethod.DEFAULT));
        assertEquals("zebra_thing", out[0].type());
    }

    @Test
    void priorityCategoriesComeFirst() throws Exception {
        FakeItem[] out = sort(new FakeItem[]{new FakeItem("iron_pickaxe", "", 1, 1), new FakeItem("oak_log", "", 1, 64)},
                order(SortMethod.DEFAULT, 1));
        assertEquals("oak_log", out[0].type());
    }

    @Test
    void mergesPartialStacksButKeepsDifferentDataApart() throws Exception {
        FakeItem[] out = sort(new FakeItem[]{
                new FakeItem("stone", "", 40, 64), new FakeItem("stone", "named", 1, 64), new FakeItem("stone", "", 40, 64)},
                order(SortMethod.DEFAULT));
        assertEquals(64, out[0].amount());
        assertEquals(16, out[1].amount());
        assertEquals("named", out[2].data());
    }

    @Test
    void sortingASortedContainerWritesNothing() throws Exception {
        Random r = new Random(7);
        FakeItem[] slots = randomSlots(r, 54);
        var store = new FakeItem.Store(slots);
        var order = order(SortMethod.DEFAULT);
        Transaction.apply(store, codec, s -> sort(s, order), Set.of(), LOG);
        int writes = store.writes;
        var again = Transaction.apply(store, codec, s -> sort(s, order), Set.of(), LOG);
        assertEquals(Transaction.Outcome.UNCHANGED, again.outcome());
        assertEquals(writes, store.writes);
    }

    @Test
    void fuzzEverySortKeepsEveryItemOrChangesNothing() {
        Random r = new Random(20261107);
        List<String> methods = List.of(SortMethod.DEFAULT, "{name},{color}", "{blocksFirst},{tier},{lore},{customName}",
                "{keepCategoryOrder},{category}");
        int changed = 0, refused = 0;
        for (int round = 0; round < 5000; round++) {
            FakeItem[] slots = randomSlots(r, 1 + r.nextInt(54));
            var store = new FakeItem.Store(slots);
            if (r.nextInt(10) == 0) store.limit = 1 + r.nextInt(64);
            Ledger before = Ledger.of(slots, codec);
            var order = order(methods.get(r.nextInt(methods.size())));
            var result = Transaction.apply(store, codec, s -> sort(s, order), Set.of(), LOG);
            assertEquals(before, Ledger.of(store.slots, codec), "round " + round);
            switch (result.outcome()) {
                case CHANGED -> changed++;
                case REFUSED -> {
                    refused++;
                    assertTrue(Transaction.sameSlots(store.slots, slots, codec), "a refused sort changed something");
                }
                case UNCHANGED -> assertTrue(Transaction.sameSlots(store.slots, slots, codec));
                default -> fail("round " + round + ": " + result);
            }
        }
        assertTrue(changed > 1000, "most rounds should sort: " + changed);
        assertTrue(refused > 10, "some rounds hold over-stacked items and must be refused: " + refused);
    }

    private static final String[] TYPES = {"stone", "oak_log", "red_wool", "white_wool", "iron_pickaxe", "diamond_sword",
            "apple_thing", "zebra_thing", "potion", "written_book", "shulker_box", "bundle", "ender_pearl", "new_thing"};

    private static FakeItem[] randomSlots(Random r, int size) {
        FakeItem[] slots = new FakeItem[size];
        for (int i = 0; i < size; i++) {
            if (r.nextInt(3) == 0) continue;
            String type = TYPES[r.nextInt(TYPES.length)];
            int max = switch (type) {
                case "iron_pickaxe", "diamond_sword", "potion", "shulker_box", "bundle" -> 1;
                case "ender_pearl", "written_book" -> 16;
                default -> 64;
            };
            String data = r.nextInt(4) == 0 ? "data" + r.nextInt(3) : "";
            int amount = 1 + r.nextInt(max);
            if (r.nextInt(200) == 0) amount = max + 1 + r.nextInt(100); // An illegal over-stack from another plugin.
            slots[i] = new FakeItem(type, data, amount, max);
        }
        return slots;
    }
}
