package com.extantape.portmanteau.trunk;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** An admin's read-only copy of someone's trunk. Every click in it is cancelled, and nothing is saved from it. */
final class ReadOnlyHolder implements InventoryHolder {
    private Inventory inventory;

    void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
