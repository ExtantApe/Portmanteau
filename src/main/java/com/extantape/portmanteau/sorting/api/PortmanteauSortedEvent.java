package com.extantape.portmanteau.sorting.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Fired on the main thread after Portmanteau rearranged an inventory and checked that every item is still there. */
public final class PortmanteauSortedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Inventory inventory;
    private final SortReason reason;

    public PortmanteauSortedEvent(@Nullable Player player, @NotNull Inventory inventory, @NotNull SortReason reason) {
        this.player = player;
        this.inventory = inventory;
        this.reason = reason;
    }

    public @Nullable Player getPlayer() { return player; }

    public @NotNull Inventory getInventory() { return inventory; }

    public @NotNull SortReason getReason() { return reason; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
