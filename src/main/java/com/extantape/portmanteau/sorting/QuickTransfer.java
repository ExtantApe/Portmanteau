package com.extantape.portmanteau.sorting;

import com.extantape.portmanteau.sorting.ContainerGate.Target;
import com.extantape.portmanteau.sorting.api.PortmanteauSortEvent;
import com.extantape.portmanteau.sorting.api.SortReason;
import com.extantape.portmanteau.transaction.SlotStore;
import com.extantape.portmanteau.transaction.StackCodec;
import com.extantape.portmanteau.transaction.Transaction;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;

/**
 * Moves items of kinds the destination already holds, between a container and the main part of the player's inventory.
 * Both inventories are one transaction, so the items across the two are checked as a whole, and the hotbar never moves.
 */
final class QuickTransfer {
    private QuickTransfer() {}

    static Sorter.Outcome move(Plugin plugin, Player p, Target container, boolean intoContainer, SortingConfig config, PrismItems prism) {
        if (config.disabledWorlds().contains(p.getWorld().getName())) return new Sorter.Outcome(Sorter.Status.NOT_ALLOWED, 0, "world");
        PortmanteauSortEvent event = new PortmanteauSortEvent(p, container.inventory(), SortReason.QUICK_TRANSFER);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return new Sorter.Outcome(Sorter.Status.NOT_ALLOWED, 0, "cancelled");

        Inventory chest = container.inventory();
        Inventory own = p.getInventory();
        int n = chest.getSize();
        Set<Integer> fixed = new HashSet<>();
        for (int i : container.fixed()) fixed.add(i);
        for (int i : event.getLockedSlots()) fixed.add(i);
        for (int i = 0; i < 9; i++) fixed.add(n + i); // the hotbar
        boolean shulker = container.kind() == ContainerGate.Kind.SHULKER_BOX;
        int limit = Math.min(chest.getMaxStackSize(), own.getMaxStackSize());

        SlotStore<ItemStack> both = new SlotStore<>() {
            @Override public ItemStack[] read() {
                ItemStack[] a = chest.getContents(), b = own.getStorageContents();
                ItemStack[] out = new ItemStack[a.length + b.length];
                System.arraycopy(a, 0, out, 0, a.length);
                System.arraycopy(b, 0, out, a.length, b.length);
                return out;
            }
            @Override public void write(ItemStack[] contents) {
                ItemStack[] a = new ItemStack[n], b = new ItemStack[contents.length - n];
                System.arraycopy(contents, 0, a, 0, n);
                System.arraycopy(contents, n, b, 0, b.length);
                chest.setContents(a);
                own.setStorageContents(b);
            }
            @Override public int slotLimit() { return limit; }
            @Override public String describe() { return "quick transfer for " + p.getName(); }
        };

        StackCodec codec = new StackCodec();
        ItemStack[] before = prism.active() ? chest.getContents() : null;
        long[] moved = {0};
        var result = Transaction.apply(both, codec, s -> {
            int fromStart = intoContainer ? n + 9 : 0, fromEnd = intoContainer ? n + 36 : n;
            int toStart = intoContainer ? 0 : n + 9, toEnd = intoContainer ? n : n + 36;
            Set<Material> kinds = new HashSet<>();
            for (int i = toStart; i < toEnd; i++) if (s[i] != null && !s[i].isEmpty()) kinds.add(s[i].getType());
            for (int i = fromStart; i < fromEnd; i++) {
                ItemStack item = s[i];
                if (item == null || item.isEmpty() || fixed.contains(i) || !kinds.contains(item.getType())) continue;
                if (intoContainer && shulker && Tag.SHULKER_BOXES.isTagged(item.getType())) continue;
                int left = item.getAmount();
                int per = Math.min(item.getMaxStackSize(), limit);
                // Top up matching stacks first, then use empty slots.
                for (int pass = 0; pass < 2 && left > 0; pass++) {
                    for (int j = toStart; j < toEnd && left > 0; j++) {
                        if (fixed.contains(j)) continue;
                        ItemStack there = s[j];
                        if (pass == 0 && there != null && !there.isEmpty() && codec.key(there).equals(codec.key(item))) {
                            int room = per - there.getAmount();
                            if (room <= 0) continue;
                            int take = Math.min(room, left);
                            s[j] = codec.withAmount(there, there.getAmount() + take);
                            left -= take;
                        } else if (pass == 1 && (there == null || there.isEmpty())) {
                            int take = Math.min(per, left);
                            s[j] = codec.withAmount(item, take);
                            left -= take;
                        }
                    }
                }
                moved[0] += item.getAmount() - left;
                s[i] = left == 0 ? null : codec.withAmount(item, left);
            }
            return s;
        }, fixed, plugin.getLogger());
        // Recorded only once the transaction has stuck, so Prism never shows a move that was rolled back.
        if (before != null && result.outcome() == Transaction.Outcome.CHANGED)
            prism.record(p, container.where(), PrismItems.changes(before, chest.getContents()));
        return switch (result.outcome()) {
            case CHANGED -> new Sorter.Outcome(Sorter.Status.SORTED, moved[0], null);
            case UNCHANGED -> new Sorter.Outcome(Sorter.Status.ALREADY_SORTED, 0, null);
            case REFUSED, RESTORED -> new Sorter.Outcome(Sorter.Status.LEFT_SAFE, 0, result.reason());
            case FAILED -> new Sorter.Outcome(Sorter.Status.FAILED, 0, result.reason());
        };
    }

}
