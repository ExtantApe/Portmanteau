package com.extantape.portmanteau.sorting.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Fired on the main thread just before Portmanteau rearranges an inventory. Cancel it to leave the inventory alone, or
 * add slot numbers to {@link #getLockedSlots()} to keep those slots exactly as they are.
 */
public final class PortmanteauSortEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Inventory inventory;
    private final SortReason reason;
    private final Set<Integer> lockedSlots = new HashSet<>();
    private boolean cancelled;

    public PortmanteauSortEvent(@Nullable Player player, @NotNull Inventory inventory, @NotNull SortReason reason) {
        this.player = player;
        this.inventory = inventory;
        this.reason = reason;
    }

    /** The player the sort is for, or null when another plugin asked without one. */
    public @Nullable Player getPlayer() { return player; }

    public @NotNull Inventory getInventory() { return inventory; }

    public @NotNull SortReason getReason() { return reason; }

    /** Slot numbers, in the inventory's own numbering, that the sort must leave exactly as they are. */
    public @NotNull Set<Integer> getLockedSlots() { return lockedSlots; }

    @Override public boolean isCancelled() { return cancelled; }

    @Override public void setCancelled(boolean cancel) { cancelled = cancel; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
