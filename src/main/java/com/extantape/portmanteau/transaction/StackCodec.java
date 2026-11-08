package com.extantape.portmanteau.transaction;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Paper item stacks, identified by {@link ItemStack#serializeAsBytes()} at an amount of one, so every component counts.
 * Make one per transaction: it remembers each stack's identity so it serializes each one once.
 */
public final class StackCodec implements ItemCodec<ItemStack> {
    private final Map<ItemStack, ItemKey> keys = new IdentityHashMap<>();

    @Override
    public boolean isEmpty(ItemStack item) {
        return item == null || item.isEmpty();
    }

    @Override
    public int amount(ItemStack item) {
        return item.getAmount();
    }

    @Override
    public int maxStack(ItemStack item) {
        return item.getMaxStackSize();
    }

    @Override
    public ItemKey key(ItemStack item) {
        ItemKey key = keys.get(item);
        if (key == null) {
            key = identity(item);
            keys.put(item, key);
        }
        return key;
    }

    /** The identity of an item apart from its amount. */
    public static ItemKey identity(ItemStack item) {
        ItemStack one = item.clone();
        one.setAmount(1);
        return new ItemKey(one.serializeAsBytes());
    }

    @Override
    public ItemStack copy(ItemStack item) {
        ItemStack copy = item.clone();
        ItemKey known = keys.get(item);
        if (known != null) keys.put(copy, known);
        return copy;
    }

    @Override
    public ItemStack withAmount(ItemStack item, int amount) {
        ItemStack copy = copy(item);
        copy.setAmount(amount);
        return copy;
    }

    @Override
    public String describe(ItemStack item) {
        return item.getAmount() + " x " + item.getType().getKey() + " (base64 item bytes, restorable with "
                + "ItemStack.deserializeBytes: " + Base64.getEncoder().encodeToString(item.serializeAsBytes()) + ")";
    }
}
