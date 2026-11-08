package com.extantape.portmanteau.trunk;

import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.List;

/**
 * Placed trunks. The block is a vanilla lodestone, which isn't a container, so hoppers, droppers, hopper minecarts,
 * copper golems and comparators have nothing to reach, and claims, explosions and pistons already treat it as an ordinary
 * block. Which lodestones are trunks is kept in the chunk's own data. With a resource pack, an item display drawn just
 * outside the block gives it its own look.
 *
 * <p>Breaking a trunk, by hand or by an explosion, drops only the trunk item, never anything stored: the storage lives in
 * each player's data, not in the block. Pistons can't move it.
 */
final class TrunkBlocks implements Listener {
    private final NamespacedKey positions, displayTag;
    private final TrunkItems items;

    TrunkBlocks(Plugin plugin, TrunkItems items) {
        this.positions = new NamespacedKey(plugin, "trunk_blocks");
        this.displayTag = new NamespacedKey(plugin, "trunk_display");
        this.items = items;
    }

    static int pack(Block b) {
        return (b.getY() << 8) | ((b.getX() & 15) << 4) | (b.getZ() & 15);
    }

    private int[] stored(Chunk c) {
        int[] a = c.getPersistentDataContainer().get(positions, PersistentDataType.INTEGER_ARRAY);
        return a == null ? new int[0] : a;
    }

    boolean isTrunk(Block b) {
        if (b.getType() != TrunkItems.TRUNK_MATERIAL) return false;
        int p = pack(b);
        for (int x : stored(b.getChunk())) if (x == p) return true;
        return false;
    }

    private void mark(Block b, boolean on) {
        Chunk c = b.getChunk();
        int p = pack(b);
        int[] now = Arrays.stream(stored(c)).filter(x -> x != p).toArray();
        if (on) {
            now = Arrays.copyOf(now, now.length + 1);
            now[now.length - 1] = p;
        }
        PersistentDataContainer pdc = c.getPersistentDataContainer();
        if (now.length == 0) pdc.remove(positions);
        else pdc.set(positions, PersistentDataType.INTEGER_ARRAY, now);
    }

    // MONITOR, so a protection plugin that refuses the placement has already had its say.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!items.isTrunk(e.getItemInHand())) return;
        Block b = e.getBlockPlaced();
        mark(b, true);
        ItemStack look = items.blockLook();
        if (look == null) return;
        Location at = b.getLocation().add(0.5, 0.5, 0.5);
        float yaw = Math.round(e.getPlayer().getYaw() / 90f) * 90f + 180f;
        b.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(look);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            // A hair larger than the block, so the lodestone underneath never shows through.
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f((float) Math.toRadians(-yaw), 0, 1, 0),
                    new Vector3f(1.002f), new AxisAngle4f()));
            d.getPersistentDataContainer().set(displayTag, PersistentDataType.INTEGER, pack(b));
        });
    }

    private void removeDisplay(Block b) {
        int p = pack(b);
        for (Entity en : b.getWorld().getNearbyEntities(b.getLocation().add(0.5, 0.5, 0.5), 0.6, 0.6, 0.6)) {
            Integer tag = en.getPersistentDataContainer().get(displayTag, PersistentDataType.INTEGER);
            if (en instanceof ItemDisplay && tag != null && tag == p) en.remove();
        }
    }

    /** Takes a trunk out of the world, dropping only the trunk item. */
    private void unplace(Block b, boolean drop) {
        mark(b, false);
        removeDisplay(b);
        if (drop) b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), items.trunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!isTrunk(b)) return;
        e.setDropItems(false);
        unplace(b, e.getPlayer().getGameMode() != GameMode.CREATIVE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        explode(e.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        explode(e.blockList());
    }

    private void explode(List<Block> blocks) {
        blocks.removeIf(b -> {
            if (!isTrunk(b)) return false;
            b.setType(Material.AIR, false);
            unplace(b, true);
            return true;
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (e.getBlocks().stream().anyMatch(this::isTrunk)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (e.getBlocks().stream().anyMatch(this::isTrunk)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        if (isTrunk(e.getBlock())) e.setCancelled(true);
    }

    /** Forgets trunks whose block was replaced while the plugin wasn't watching, such as by a world edit. */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        Chunk c = e.getChunk();
        int[] all = stored(c);
        if (all.length == 0) return;
        int[] keep = Arrays.stream(all).filter(p -> {
            Block b = c.getBlock((p >> 4) & 15, p >> 8, p & 15);
            return b.getType() == TrunkItems.TRUNK_MATERIAL;
        }).toArray();
        if (keep.length == all.length) return;
        for (int p : all) {
            if (Arrays.stream(keep).noneMatch(k -> k == p)) removeDisplay(c.getBlock((p >> 4) & 15, p >> 8, p & 15));
        }
        if (keep.length == 0) c.getPersistentDataContainer().remove(positions);
        else c.getPersistentDataContainer().set(positions, PersistentDataType.INTEGER_ARRAY, keep);
    }

    /** Entities load separately from blocks, so a display whose trunk is gone is removed when it loads. */
    @EventHandler
    public void onEntitiesLoad(org.bukkit.event.world.EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            Integer tag = en.getPersistentDataContainer().get(displayTag, PersistentDataType.INTEGER);
            if (tag == null || !(en instanceof ItemDisplay)) continue;
            Block b = en.getLocation().getBlock();
            if (pack(b) != tag || !isTrunk(b)) en.remove();
        }
    }
}
