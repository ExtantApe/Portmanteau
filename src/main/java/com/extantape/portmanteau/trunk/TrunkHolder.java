package com.extantape.portmanteau.trunk;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * The holder of a player's live trunk inventory for one scope. Sorting recognises this class by name. It's keyed by owner
 * and scope, never by viewer.
 */
public final class TrunkHolder implements InventoryHolder {
    private final UUID owner;
    private final String scope;
    private Inventory inventory;

    TrunkHolder(UUID owner, String scope) {
        this.owner = owner;
        this.scope = scope;
    }

    void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    public UUID owner() {
        return owner;
    }

    public String scope() {
        return scope;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
