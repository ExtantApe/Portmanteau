package com.extantape.portmanteau.trunk;

import com.extantape.portmanteau.transaction.StackCodec;
import com.extantape.portmanteau.transaction.Transaction;
import com.extantape.portmanteau.transaction.VerifiedBytes;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Trunk contents in the player's own persistent data, one entry per scope, so the server saves them in the same write as
 * the player's inventory and a crash can't split the two. Items are stored with Paper's own item serialization, which
 * records the data version so newer Minecraft versions can upgrade them, plus a SHA-256 checksum to spot damage.
 */
public final class TrunkStore {
    public static final int SLOTS = 54;

    public sealed interface Loaded permits Contents, Corrupt {}

    public record Contents(ItemStack[] items, byte[] bytes) implements Loaded {}

    /** The stored data couldn't be read. It's left untouched, and the trunk stays shut. */
    public record Corrupt(String reason) implements Loaded {}

    private final NamespacedKey root, scopeKey, itemsKey, hashKey;
    private final Plugin plugin;

    public TrunkStore(Plugin plugin) {
        this.plugin = plugin;
        root = new NamespacedKey(plugin, "trunk");
        scopeKey = new NamespacedKey(plugin, "scope");
        itemsKey = new NamespacedKey(plugin, "items");
        hashKey = new NamespacedKey(plugin, "sha256");
    }

    private NamespacedKey entryKey(String scope) {
        return new NamespacedKey(plugin, "s_" + HexFormat.of().formatHex(sha256(scope.getBytes(StandardCharsets.UTF_8)), 0, 10));
    }

    public Loaded load(Player owner, String scope) {
        PersistentDataContainer all = owner.getPersistentDataContainer().get(root, PersistentDataType.TAG_CONTAINER);
        PersistentDataContainer entry = all == null ? null : all.get(entryKey(scope), PersistentDataType.TAG_CONTAINER);
        if (entry == null) {
            ItemStack[] empty = new ItemStack[SLOTS];
            return new Contents(empty, serialize(empty));
        }
        try {
            String storedScope = entry.get(scopeKey, PersistentDataType.STRING);
            byte[] bytes = entry.get(itemsKey, PersistentDataType.BYTE_ARRAY);
            byte[] hash = entry.get(hashKey, PersistentDataType.BYTE_ARRAY);
            if (!scope.equals(storedScope)) return new Corrupt("the stored entry belongs to scope " + storedScope);
            if (bytes == null || hash == null) return new Corrupt("the stored entry is missing its items or checksum");
            if (!Arrays.equals(hash, sha256(bytes))) return new Corrupt("the checksum doesn't match the stored items");
            ItemStack[] items = ItemStack.deserializeItemsFromBytes(bytes);
            if (items.length > SLOTS) return new Corrupt("the stored entry has " + items.length + " slots, more than " + SLOTS);
            ItemStack[] slots = Arrays.copyOf(items, SLOTS);
            for (int i = 0; i < slots.length; i++) if (slots[i] != null && slots[i].isEmpty()) slots[i] = null;
            return new Contents(slots, bytes);
        } catch (RuntimeException e) {
            return new Corrupt("the stored items couldn't be read: " + e);
        }
    }

    public static byte[] serialize(ItemStack[] contents) {
        ItemStack[] out = new ItemStack[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            ItemStack item = i < contents.length ? contents[i] : null;
            out[i] = item == null || item.isEmpty() ? ItemStack.empty() : item;
        }
        return ItemStack.serializeItemsAsBytes(out);
    }

    /** Saves the contents, checking that they decode back to the same items and that the write reads back. */
    public VerifiedBytes.Result save(Player owner, String scope, ItemStack[] contents) {
        byte[] next = serialize(contents);
        PersistentDataContainer pdc = owner.getPersistentDataContainer();
        NamespacedKey key = entryKey(scope);
        VerifiedBytes.Cell cell = new VerifiedBytes.Cell() {
            @Override public byte[] get() {
                PersistentDataContainer all = pdc.get(root, PersistentDataType.TAG_CONTAINER);
                PersistentDataContainer entry = all == null ? null : all.get(key, PersistentDataType.TAG_CONTAINER);
                return entry == null ? null : entry.get(itemsKey, PersistentDataType.BYTE_ARRAY);
            }

            @Override public void set(byte[] bytes) {
                PersistentDataContainer all = pdc.get(root, PersistentDataType.TAG_CONTAINER);
                if (all == null) all = pdc.getAdapterContext().newPersistentDataContainer();
                if (bytes == null) {
                    all.remove(key);
                } else {
                    PersistentDataContainer entry = all.getAdapterContext().newPersistentDataContainer();
                    entry.set(scopeKey, PersistentDataType.STRING, scope);
                    entry.set(itemsKey, PersistentDataType.BYTE_ARRAY, bytes);
                    entry.set(hashKey, PersistentDataType.BYTE_ARRAY, sha256(bytes));
                    all.set(key, PersistentDataType.TAG_CONTAINER, entry);
                }
                pdc.set(root, PersistentDataType.TAG_CONTAINER, all);
            }
        };
        return VerifiedBytes.replace(cell, next, bytes -> {
            ItemStack[] back = Arrays.copyOf(ItemStack.deserializeItemsFromBytes(bytes), SLOTS);
            ItemStack[] meant = Arrays.copyOf(contents, SLOTS);
            for (int i = 0; i < SLOTS; i++) {
                if (back[i] != null && back[i].isEmpty()) back[i] = null;
                if (meant[i] != null && meant[i].isEmpty()) meant[i] = null;
            }
            return Transaction.sameSlots(back, meant, new StackCodec());
        });
    }

    static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing from this Java", e);
        }
    }
}
