package com.extantape.portmanteau.sorting.claims;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * GriefPrevention's container trust. An admin using {@code /ignoreclaims} can open anyone's chest, but it only sorts if
 * they'd have container trust without the bypass, so looking through a chest never rearranges it. Only loaded when
 * GriefPrevention is enabled.
 */
public final class GriefPreventionCheck implements ClaimCheck {
    @Override
    public boolean maySort(Player player, Location where) {
        GriefPrevention gp = GriefPrevention.instance;
        if (gp == null || gp.dataStore == null) return false;
        Claim claim = gp.dataStore.getClaimAt(where, false, null);
        if (claim == null) return true;
        if (player.getUniqueId().equals(claim.getOwnerID())) return true;
        PlayerData data = gp.dataStore.getPlayerData(player.getUniqueId());
        if (data != null && data.ignoreClaims) return claim.hasExplicitPermission(player.getUniqueId(), ClaimPermission.Inventory);
        return claim.checkPermission(player, ClaimPermission.Inventory, null) == null;
    }
}
