package com.extantape.portmanteau.trunk;

import org.mvplugins.multiverse.inventories.MultiverseInventoriesApi;
import org.mvplugins.multiverse.inventories.profile.group.WorldGroup;
import org.mvplugins.multiverse.inventories.share.Sharables;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Multiverse-Inventories 5 groups that share the player's inventory, read each time they're needed, so a change to the
 * groups takes effect without a reload. Only loaded when that plugin is enabled.
 */
final class MultiverseGroups implements GroupSource {
    @Override
    public Map<String, Set<String>> groups() {
        try {
            Map<String, Set<String>> out = new HashMap<>();
            for (WorldGroup g : MultiverseInventoriesApi.get().getWorldGroupManager().getGroups()) {
                if (sharesInventory(g)) out.put(g.getName(), new HashSet<>(g.getApplicableWorlds()));
            }
            return out;
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    @Override
    public List<String> groupsFor(String world) {
        try {
            return MultiverseInventoriesApi.get().getWorldGroupManager().getGroupsForWorld(world).stream()
                    .filter(MultiverseGroups::sharesInventory).map(WorldGroup::getName).sorted().toList();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static boolean sharesInventory(WorldGroup g) {
        return g.getApplicableShares().contains(Sharables.INVENTORY);
    }

    @Override
    public String describe() {
        return "Multiverse-Inventories groups that share the inventory";
    }
}
