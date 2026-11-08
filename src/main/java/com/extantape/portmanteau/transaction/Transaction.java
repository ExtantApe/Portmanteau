package com.extantape.portmanteau.transaction;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Changes the items in some slots without ever losing or adding one.
 *
 * <ol>
 *   <li>Copy the contents. Plan the new layout from the copy.</li>
 *   <li>Check the plan: the same items in the same amounts, compared by full identity; no stack empty or over its
 *       limit; every slot that mustn't move unchanged.</li>
 *   <li>If the plan changes nothing, write nothing.</li>
 *   <li>Re-read the live contents. If anything changed since the copy (a hopper, another viewer), stop.</li>
 *   <li>Write the plan in one call, re-read, and compare. If it differs, put the copy back. If even that fails, log
 *       every item in full so an admin can restore it.</li>
 * </ol>
 *
 * All of it runs in one call on the server's main thread, so nothing else can touch the slots between the steps, and a
 * crash can't leave a half-written result.
 */
public final class Transaction {
    private Transaction() {}

    public enum Outcome {
        /** The new layout was written and checked. */
        CHANGED,
        /** The contents were already in that layout. Nothing was written. */
        UNCHANGED,
        /** Nothing was written, because something didn't add up. The items are as they were. */
        REFUSED,
        /** The write didn't come out as planned, and the original contents were put back and checked. */
        RESTORED,
        /** The write didn't come out as planned, and putting the original back didn't work either. Logged in full. */
        FAILED
    }

    public record Result(Outcome outcome, String reason, long items) {
        public boolean wrote() {
            return outcome == Outcome.CHANGED;
        }

        public boolean safe() {
            return outcome != Outcome.FAILED;
        }
    }

    public static <T> Result apply(SlotStore<T> store, ItemCodec<T> codec, Planner<T> planner, Set<Integer> fixedSlots,
                                   Logger log) {
        T[] snapshot = copyAll(store.read(), codec);
        Ledger before = Ledger.of(snapshot, codec);

        String overLimit = firstBadStack(snapshot, codec, store.slotLimit());
        if (overLimit != null) return refused(log, store, "it holds " + overLimit, before.total());

        T[] plan;
        try {
            plan = planner.plan(copyAll(snapshot, codec));
        } catch (PlanRefused e) {
            return refused(log, store, e.getMessage(), before.total());
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Left " + store.describe() + " unchanged: planning failed", e);
            return new Result(Outcome.REFUSED, "planning failed: " + e, before.total());
        }

        String problem = checkPlan(snapshot, plan, before, codec, fixedSlots, store.slotLimit());
        if (problem != null) return refused(log, store, problem, before.total());
        if (sameSlots(snapshot, plan, codec)) return new Result(Outcome.UNCHANGED, null, before.total());

        if (!sameSlots(store.read(), snapshot, codec)) {
            return new Result(Outcome.REFUSED, "the contents changed while sorting", before.total());
        }

        T[] toWrite = copyAll(plan, codec);
        try {
            store.write(toWrite);
            if (sameSlots(store.read(), plan, codec)) return new Result(Outcome.CHANGED, null, before.total());
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Writing " + store.describe() + " failed", e);
        }
        return restore(store, codec, snapshot, before, log);
    }

    private static <T> Result restore(SlotStore<T> store, ItemCodec<T> codec, T[] snapshot, Ledger before, Logger log) {
        try {
            store.write(copyAll(snapshot, codec));
            if (sameSlots(store.read(), snapshot, codec)) {
                log.warning("The new layout of " + store.describe() + " didn't read back as written, so the original "
                        + "contents were put back. Nothing was lost.");
                return new Result(Outcome.RESTORED, "the write didn't read back as planned", before.total());
            }
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Putting back " + store.describe() + " failed", e);
        }
        StringBuilder dump = new StringBuilder("Could not write or restore ").append(store.describe())
                .append(". Its contents before the change were, slot by slot:");
        for (int i = 0; i < snapshot.length; i++) {
            if (!codec.isEmpty(snapshot[i])) dump.append("\n  slot ").append(i).append(": ").append(codec.describe(snapshot[i]));
        }
        log.severe(dump.toString());
        return new Result(Outcome.FAILED, "the write and the restore both failed", before.total());
    }

    private static Result refused(Logger log, SlotStore<?> store, String reason, long items) {
        log.info("Left " + store.describe() + " unsorted to keep its items safe: " + reason + ".");
        return new Result(Outcome.REFUSED, reason, items);
    }

    /** Null when the plan keeps every item and every rule, otherwise what's wrong. */
    static <T> String checkPlan(T[] snapshot, T[] plan, Ledger before, ItemCodec<T> codec, Set<Integer> fixedSlots,
                                int slotLimit) {
        if (plan == null || plan.length != snapshot.length) return "the plan has the wrong number of slots";
        String bad = firstBadStack(plan, codec, slotLimit);
        if (bad != null) return "the plan holds " + bad;
        for (int slot : fixedSlots) {
            if (slot < 0 || slot >= snapshot.length) continue;
            if (!sameItem(snapshot[slot], plan[slot], codec)) return "the plan moves slot " + slot + ", which must stay put";
        }
        Ledger after = Ledger.of(plan, codec);
        if (!before.equals(after)) return before.difference(after);
        return null;
    }

    private static <T> String firstBadStack(T[] slots, ItemCodec<T> codec, int slotLimit) {
        for (int i = 0; i < slots.length; i++) {
            T item = slots[i];
            if (item == null || codec.isEmpty(item)) continue;
            int amount = codec.amount(item);
            int max = Math.min(codec.maxStack(item), slotLimit);
            if (amount < 1) return "an empty stack in slot " + i;
            if (amount > max) return "a stack of " + amount + " in slot " + i + ", over its limit of " + max;
        }
        return null;
    }

    public static <T> boolean sameSlots(T[] a, T[] b, ItemCodec<T> codec) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (!sameItem(a[i], b[i], codec)) return false;
        }
        return true;
    }

    public static <T> boolean sameItem(T a, T b, ItemCodec<T> codec) {
        boolean ea = a == null || codec.isEmpty(a), eb = b == null || codec.isEmpty(b);
        if (ea || eb) return ea == eb;
        return codec.amount(a) == codec.amount(b) && codec.key(a).equals(codec.key(b));
    }

    private static <T> T[] copyAll(T[] slots, ItemCodec<T> codec) {
        T[] out = slots.clone();
        for (int i = 0; i < out.length; i++) {
            out[i] = out[i] == null || codec.isEmpty(out[i]) ? null : codec.copy(out[i]);
        }
        return out;
    }
}
