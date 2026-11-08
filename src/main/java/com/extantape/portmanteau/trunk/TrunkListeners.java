package com.extantape.portmanteau.trunk;

import com.extantape.portmanteau.text.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/** Opening trunks, and saving them at every moment a player could leave or change inventories. */
final class TrunkListeners implements Listener {
    private final TrunkFeature feature;
    private final Messages messages;
    private final Logger log;
    private final Set<String> reported = new HashSet<>();

    TrunkListeners(TrunkFeature feature, Messages messages, Logger log) {
        this.feature = feature;
        this.messages = messages;
        this.log = log;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null || !feature.blocks().isTrunk(b)) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        // Sneaking with a block in hand places it against the trunk, as with any container.
        if (p.isSneaking() && !hand.isEmpty() && hand.getType().isBlock()) return;
        // A protection plugin that refused the interaction keeps it shut. Otherwise the lodestone does nothing of its own.
        boolean denied = e.useInteractedBlock() == Event.Result.DENY;
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
        if (denied) return;
        open(p, b);
    }

    void open(Player p, Block b) {
        if (!p.hasPermission("portmanteau.trunk.use")) {
            messages.actionBar(p, "trunk.no-permission");
            return;
        }
        if (!feature.config().gameModes().contains(p.getGameMode())) {
            messages.actionBar(p, "trunk.wrong-mode");
            return;
        }
        Scopes.Resolution r = feature.scopes().resolve(b.getWorld());
        reportLinkErrors(r.linkErrors());
        if (r.scope() == null) {
            messages.send(p, "trunk.shut");
            String why = "A multiversal trunk in " + b.getWorld().getName() + " stayed shut: " + r.problem() + ".";
            if (reported.add(why)) log.warning(why);
            return;
        }
        var title = messages.get(p, "trunk.title", Placeholder.unparsed("scope", r.scope().label()));
        TrunkSessions.Session s = feature.sessions().open(p, r.scope().id(), title);
        if (s == null) {
            messages.send(p, "trunk.unreadable");
            return;
        }
        p.openInventory(s.inventory);
        messages.actionBar(p, "trunk.linked", Placeholder.unparsed("scope", r.scope().label()));
        var look = feature.config().look();
        if (!look.sound().isBlank()) p.playSound(b.getLocation().add(0.5, 0.5, 0.5), look.sound(), 0.7f, 1.1f);
        NamespacedKey pk = NamespacedKey.fromString(look.particle());
        Particle particle = pk == null ? null : Registry.PARTICLE_TYPE.get(pk);
        if (particle != null && particle.getDataType() == Void.class) {
            b.getWorld().spawnParticle(particle, b.getLocation().add(0.5, 1.0, 0.5), 24, 0.3, 0.3, 0.3, 0.2);
        }
    }

    private void reportLinkErrors(List<String> errors) {
        if (errors.isEmpty()) return;
        String all = String.join(" ", errors);
        if (reported.add(all)) log.severe("Multiversal trunk links refused: " + all);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onReadOnlyClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof ReadOnlyHolder) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onReadOnlyDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof ReadOnlyHolder) e.setCancelled(true);
    }

    // Any click or drag while a trunk is open may change it, including shift-clicks from below, number keys, the off-hand
    // key and double-click collecting. Each one schedules a save for the next tick.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent e) {
        dirty(e.getView().getTopInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent e) {
        dirty(e.getView().getTopInventory());
    }

    private void dirty(Inventory top) {
        TrunkSessions.Session s = feature.sessions().find(top);
        if (s != null) feature.sessions().markDirty(s);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        TrunkSessions.Session s = feature.sessions().find(e.getInventory());
        if (s != null && e.getPlayer() instanceof Player p) feature.sessions().release(s, p);
    }

    // The view closes, and saves, before any of these can swap the player's inventory or move them elsewhere.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeleport(PlayerTeleportEvent e) {
        feature.sessions().closeViewOf(e.getPlayer(), InventoryCloseEvent.Reason.PLUGIN);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        feature.sessions().closeViewOf(e.getPlayer(), InventoryCloseEvent.Reason.PLUGIN);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        feature.sessions().closeViewOf(e.getPlayer(), InventoryCloseEvent.Reason.DEATH);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        feature.sessions().closeViewOf(e.getPlayer(), InventoryCloseEvent.Reason.DISCONNECT);
        feature.sessions().unloadPlayer(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldSave(WorldSaveEvent e) {
        feature.sessions().commitAll("world save");
    }
}
