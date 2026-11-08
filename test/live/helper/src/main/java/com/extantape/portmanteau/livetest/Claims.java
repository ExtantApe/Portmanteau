package com.extantape.portmanteau.livetest;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** GriefPrevention claims for the claims suite. Only loaded when GriefPrevention is running. */
final class Claims {
    private Claims() {}

    static String claim(Player owner, World w, int x1, int z1, int x2, int z2) {
        var r = GriefPrevention.instance.dataStore.createClaim(w, x1, x2, -64, 320, z1, z2, owner.getUniqueId(), null, null, null);
        return "{\"made\":" + r.succeeded + "}";
    }

    static String trust(World w, int x, int z, Player who) {
        Claim c = GriefPrevention.instance.dataStore.getClaimAt(new Location(w, x, -60, z), true, null);
        c.setPermission(who.getUniqueId().toString(), ClaimPermission.Inventory);
        GriefPrevention.instance.dataStore.saveClaim(c);
        return "{\"trusted\":true}";
    }

    static String ignore(Player p, boolean on) {
        GriefPrevention.instance.dataStore.getPlayerData(p.getUniqueId()).ignoreClaims = on;
        return "{\"ignoring\":" + on + "}";
    }
}
