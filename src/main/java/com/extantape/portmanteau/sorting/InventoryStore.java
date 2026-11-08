package com.extantape.portmanteau.sorting;

import com.extantape.portmanteau.transaction.SlotStore;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** An inventory as transaction slots. A player's inventory is its 36 storage slots; armour and the off hand never move. */
final class InventoryStore implements SlotStore<ItemStack> {
    private final Inventory inventory;
    private final boolean storageOnly;
    private final String description;

    InventoryStore(Inventory inventory, boolean storageOnly, String description) {
        this.inventory = inventory;
        this.storageOnly = storageOnly;
        this.description = description;
    }

    @Override
    public ItemStack[] read() {
        return storageOnly ? inventory.getStorageContents() : inventory.getContents();
    }

    @Override
    public void write(ItemStack[] contents) {
        if (com.extantape.portmanteau.transaction.TestFaults.take("portmanteau.test.break-writes")) {
            contents = contents.clone();
            for (int i = contents.length - 1; i >= 0; i--) {
                if (contents[i] != null && !contents[i].isEmpty()) { contents[i] = null; break; }
            }
        }
        if (storageOnly) inventory.setStorageContents(contents);
        else inventory.setContents(contents);
    }

    @Override
    public int slotLimit() {
        return inventory.getMaxStackSize();
    }

    @Override
    public String describe() {
        return description;
    }
}
