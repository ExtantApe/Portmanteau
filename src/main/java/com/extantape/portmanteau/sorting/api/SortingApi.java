package com.extantape.portmanteau.sorting.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * For other plugins: sort an inventory you own, such as your own storage menu, through Portmanteau's checks. Every item
 * is kept or nothing changes. Call on the main thread. Portmanteau's events fire as for any other sort.
 */
public final class SortingApi {
    /** What happened. */
    public enum Result {
        SORTED, ALREADY_SORTED,
        /** Left unchanged because something didn't add up; the console says why. */
        LEFT_UNCHANGED,
        /** The write failed and so did putting it back; the console lists every item. */
        FAILED,
        /** Cancelled by a listener, or not allowed. */
        NOT_ALLOWED,
        /** Sorting is switched off on this server. */
        NOT_RUNNING
    }

    /** Implemented by the sorting feature while it's running. */
    public interface Backend {
        Result sort(@NotNull Inventory inventory, @Nullable Player player);
    }

    private static volatile Backend backend;

    private SortingApi() {}

    /** Sorts every slot of the inventory with the server's order, or the player's chest order if a player is given. */
    public static @NotNull Result sort(@NotNull Inventory inventory, @Nullable Player player) {
        Backend b = backend;
        return b == null ? Result.NOT_RUNNING : b.sort(inventory, player);
    }

    public static boolean running() {
        return backend != null;
    }

    /** For Portmanteau itself. */
    public static void install(@Nullable Backend b) {
        backend = b;
    }
}
