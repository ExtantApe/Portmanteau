package com.extantape.portmanteau.sorting.claims;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Asks a protection plugin whether a player may take items from a container at a place. */
public interface ClaimCheck {
    ClaimCheck NONE = (player, where) -> true;

    boolean maySort(Player player, Location where);
}
