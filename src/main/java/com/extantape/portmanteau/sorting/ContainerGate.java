package com.extantape.portmanteau.sorting;

import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.PlayerInventory;

import java.util.Set;

/**
 * Decides whether an inventory is one Portmanteau may sort. Only real vanilla containers pass, plus the player's own
 * inventory and the multiversal trunk, and any holder an admin allow-lists by class name. Another plugin's menu, grave,
 * backpack, preview or a shulker box opened from an item has a plugin holder or none, so it never passes, and no plugin
 * has to opt out.
 */
public final class ContainerGate {
    /** The trunk's holder, named rather than imported, so the two features share nothing but the transaction code. */
    static final String TRUNK_HOLDER = "com.extantape.portmanteau.trunk.TrunkHolder";

    public enum Kind {
        CHEST, DOUBLE_CHEST, BARREL, SHULKER_BOX, ENDER_CHEST, ANIMAL, CHEST_BOAT, STORAGE_MINECART, TRUNK, ALLOWED, PLAYER;

        /** Containers that exist somewhere in a world, so claims apply. */
        boolean placed() {
            return this != ENDER_CHEST && this != TRUNK && this != PLAYER && this != ALLOWED;
        }
    }

    /**
     * A sortable inventory.
     *
     * @param fixed slots that hold equipment, such as an animal's saddle, and never move
     */
    public record Target(Kind kind, Inventory inventory, Set<Integer> fixed, Location where) {}

    private final Set<String> allowed;
    private final boolean trunkAllowed;

    public ContainerGate(Set<String> allowedHolders, boolean trunkAllowed) {
        this.allowed = allowedHolders;
        this.trunkAllowed = trunkAllowed;
    }

    /** The player's own inventory, or null if it isn't theirs. */
    public Target playerInventory(Player player, Inventory inv) {
        if (inv instanceof PlayerInventory pi && player.equals(pi.getHolder())) {
            return new Target(Kind.PLAYER, inv, Set.of(), null);
        }
        return null;
    }

    /**
     * The container a player has open as the top of their screen, or null if it isn't one Portmanteau may sort.
     * {@code inv} must be that top inventory.
     */
    public Target container(Player player, Inventory inv) {
        if (inv == null || !inv.equals(player.getOpenInventory().getTopInventory())) return null;
        InventoryHolder holder = inv.getHolder(false);
        InventoryType type = inv.getType();

        if (holder == null) return null;
        String holderClass = holder.getClass().getName();
        if (holderClass.equals(TRUNK_HOLDER)) {
            return trunkAllowed && type == InventoryType.CHEST ? new Target(Kind.TRUNK, inv, Set.of(), null) : null;
        }
        if (allowed.contains(holderClass)) return new Target(Kind.ALLOWED, inv, Set.of(), null);

        if (type == InventoryType.ENDER_CHEST) {
            return inv.equals(player.getEnderChest()) ? new Target(Kind.ENDER_CHEST, inv, Set.of(), null) : null;
        }
        if (holder instanceof DoubleChest dc && type == InventoryType.CHEST) {
            if (!(dc.getLeftSide(false) instanceof Chest left) || !(dc.getRightSide(false) instanceof Chest right)) return null;
            if (!realChest(left) || !realChest(right) || !inv.equals(dc.getInventory())) return null;
            return new Target(Kind.DOUBLE_CHEST, inv, Set.of(), left.getLocation());
        }
        if (holder instanceof Chest chest && type == InventoryType.CHEST) {
            if (!realChest(chest) || !(inv.equals(chest.getBlockInventory()) || inv.equals(chest.getInventory()))) return null;
            return new Target(Kind.CHEST, inv, Set.of(), chest.getLocation());
        }
        if (holder instanceof Barrel barrel && type == InventoryType.BARREL) {
            Block b = barrel.getBlock();
            if (b.getType() != org.bukkit.Material.BARREL || !inv.equals(barrel.getInventory())) return null;
            return new Target(Kind.BARREL, inv, Set.of(), barrel.getLocation());
        }
        if (holder instanceof ShulkerBox box && type == InventoryType.SHULKER_BOX) {
            // A placed shulker box only. One opened from an item by another plugin has that plugin's holder.
            if (!Tag.SHULKER_BOXES.isTagged(box.getBlock().getType()) || !inv.equals(box.getInventory())) return null;
            return new Target(Kind.SHULKER_BOX, inv, Set.of(), box.getLocation());
        }
        if (holder instanceof ChestedHorse animal) {
            // Slot 0 is the saddle and slot 1 the armour or carpet. The chest's slots follow.
            if (!animal.isCarryingChest() || animal.isDead() || !inv.equals(animal.getInventory())) return null;
            return new Target(Kind.ANIMAL, inv, Set.of(0, 1), animal.getLocation());
        }
        if (holder instanceof ChestBoat boat && type == InventoryType.CHEST) {
            if (boat.isDead() || !inv.equals(boat.getInventory())) return null;
            return new Target(Kind.CHEST_BOAT, inv, Set.of(), boat.getLocation());
        }
        if (holder instanceof StorageMinecart cart && type == InventoryType.CHEST) {
            if (cart.isDead() || !inv.equals(cart.getInventory())) return null;
            return new Target(Kind.STORAGE_MINECART, inv, Set.of(), cart.getLocation());
        }
        return null;
    }

    /** The holder's block is still a chest of the same kind, so a stale or made-up holder doesn't pass. */
    private static boolean realChest(Chest chest) {
        Block b = chest.getBlock();
        return b.getType() == chest.getType() && (b.getType() == org.bukkit.Material.CHEST || b.getType() == org.bukkit.Material.TRAPPED_CHEST);
    }
}
