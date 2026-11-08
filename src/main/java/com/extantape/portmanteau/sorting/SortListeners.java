package com.extantape.portmanteau.sorting;

import com.extantape.portmanteau.sorting.ContainerGate.Target;
import com.extantape.portmanteau.sorting.PlayerChoices.Hotkey;
import com.extantape.portmanteau.sorting.Sorter.Outcome;
import com.extantape.portmanteau.sorting.Sorter.Part;
import com.extantape.portmanteau.sorting.api.SortReason;
import com.extantape.portmanteau.text.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sorting on close, hotkeys, and quick transfer. */
final class SortListeners implements Listener {
    private final Plugin plugin;
    private final SortingFeature feature;
    private final Messages messages;
    private final Map<UUID, Long> lastHotkey = new HashMap<>();

    SortListeners(Plugin plugin, SortingFeature feature, Messages messages) {
        this.plugin = plugin;
        this.feature = feature;
        this.messages = messages;
    }

    // Sorting on close. Only when the player closed the screen themselves: never on a teleport, world change, death or
    // disconnect, so an inventory plugin swapping inventories between worlds is never written into.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (e.getReason() != InventoryCloseEvent.Reason.PLAYER || !(e.getPlayer() instanceof Player p)) return;
        SortingConfig c = feature.config();
        PlayerChoices choices = PlayerChoices.load(plugin, p);
        Inventory top = e.getInventory();
        boolean ownInventory = top.getType() == InventoryType.CRAFTING;
        boolean recognised = ownInventory;

        if (!ownInventory) {
            Target target = feature.gate().container(p, top);
            recognised = target != null;
            boolean othersStillLooking = top.getViewers().stream().anyMatch(v -> !v.equals(p));
            if (target != null && !othersStillLooking && p.hasPermission("portmanteau.sort")
                    && Effective.autoChests(c, choices)) {
                Outcome o = feature.sorter().sort(p, target, SortReason.CLOSE, Part.BOTH);
                report(p, o, false);
                if (o.status() == Sorter.Status.SORTED) firstHint(p, choices);
            }
        }
        // The player's inventory sorts a tick later, once the server has put back anything on the cursor, and only if
        // they haven't opened something else in the meantime.
        if (recognised && p.hasPermission("portmanteau.inventory") && Effective.autoInventory(c, choices)) {
            var world = p.getWorld();
            p.getScheduler().run(plugin, task -> {
                if (!p.isOnline() || p.isDead() || p.getWorld() != world) return;
                if (p.getOpenInventory().getTopInventory().getType() != InventoryType.CRAFTING) return;
                if (!p.getItemOnCursor().isEmpty()) return;
                Target self = feature.gate().playerInventory(p, p.getInventory());
                if (self == null) return;
                Part part = Effective.hotbar(c, PlayerChoices.load(plugin, p)) ? Part.BOTH : Part.MAIN;
                report(p, feature.sorter().sort(p, self, SortReason.CLOSE, part), false);
            }, null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null) {
            quickTransfer(e, p);
            return;
        }
        Hotkey hotkey = hotkeyFor(e, p);
        if (hotkey == null) return;
        SortingConfig c = feature.config();
        if (!p.hasPermission("portmanteau.hotkeys")) return;
        if (!Effective.hotkey(c, PlayerChoices.load(plugin, p), hotkey)) return;

        Inventory clicked = e.getClickedInventory();
        Inventory top = e.getView().getTopInventory();
        boolean bottom = clicked instanceof PlayerInventory;
        // Only real containers, and the player's inventory only below one.
        Target container = feature.gate().container(p, top);
        if (top.getType() != InventoryType.CRAFTING && container == null) return;
        if (!bottom && container == null) return;

        e.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = lastHotkey.get(p.getUniqueId());
        if (last != null && now - last < c.hotkeys().cooldownMillis()) return;
        lastHotkey.put(p.getUniqueId(), now);

        Part part = bottom && e.getSlot() < 9 ? Part.HOTBAR : Part.MAIN;
        p.getScheduler().run(plugin, task -> {
            if (!p.isOnline() || !p.getItemOnCursor().isEmpty()) return;
            Inventory nowTop = p.getOpenInventory().getTopInventory();
            if (!nowTop.equals(top)) return;
            Target target = bottom ? feature.gate().playerInventory(p, p.getInventory()) : feature.gate().container(p, top);
            if (target == null) return;
            if (bottom && nowTop.getType() != InventoryType.CRAFTING && feature.gate().container(p, nowTop) == null) return;
            if (bottom && !p.hasPermission("portmanteau.inventory")) return;
            report(p, feature.sorter().sort(p, target, SortReason.HOTKEY, part), true);
        }, null);
    }

    /**
     * Middle-click works on any slot outside creative mode, where the game uses it to copy items. The others only on an
     * empty slot with nothing on the cursor, where the game does nothing with them. Anything else, including odd clicks
     * from remapped mouse buttons, is left to the game.
     */
    private static Hotkey hotkeyFor(InventoryClickEvent e, Player p) {
        ClickType click = e.getClick();
        ItemStack cursor = e.getCursor();
        if (!cursor.isEmpty()) return null;
        ItemStack current = e.getCurrentItem();
        boolean emptySlot = current == null || current.isEmpty();
        return switch (click) {
            case MIDDLE -> p.getGameMode() == GameMode.CREATIVE ? null : Hotkey.MIDDLE_CLICK;
            case SHIFT_LEFT -> emptySlot ? Hotkey.SHIFT_CLICK : null;
            case DOUBLE_CLICK -> emptySlot ? Hotkey.DOUBLE_CLICK : null;
            case SHIFT_RIGHT -> emptySlot ? Hotkey.SHIFT_RIGHT_CLICK : null;
            default -> null;
        };
    }

    /** Left-click outside the window: matching items go into the container. Right-click: they come out. */
    private void quickTransfer(InventoryClickEvent e, Player p) {
        SortingConfig c = feature.config();
        if (!c.quickTransfer() || e.getSlotType() != InventoryType.SlotType.OUTSIDE) return;
        if (!e.getCursor().isEmpty() || !p.hasPermission("portmanteau.quicktransfer")) return;
        if (e.getClick() != ClickType.LEFT && e.getClick() != ClickType.RIGHT) return;
        Target container = feature.gate().container(p, e.getView().getTopInventory());
        if (container == null) return;
        e.setCancelled(true);
        boolean intoContainer = e.getClick() == ClickType.LEFT;
        long now = System.currentTimeMillis();
        Long last = lastHotkey.get(p.getUniqueId());
        if (last != null && now - last < c.hotkeys().cooldownMillis()) return;
        lastHotkey.put(p.getUniqueId(), now);
        p.getScheduler().run(plugin, task -> {
            if (!p.isOnline() || !p.getItemOnCursor().isEmpty()) return;
            Target again = feature.gate().container(p, p.getOpenInventory().getTopInventory());
            if (again == null || !again.inventory().equals(container.inventory())) return;
            if (!feature.sorter().claimsAllow(p, again)) {
                messages.actionBar(p, "sort.not-here");
                return;
            }
            var o = QuickTransfer.move(plugin, p, again, intoContainer, feature.config());
            if (o.status() == Sorter.Status.SORTED) {
                messages.actionBar(p, intoContainer ? "quick-transfer.in" : "quick-transfer.out",
                        Placeholder.unparsed("count", Long.toString(o.items())));
            } else {
                report(p, o, true);
            }
        }, null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastHotkey.remove(e.getPlayer().getUniqueId());
    }

    /**
     * Hotkeys and commands say what happened in the action bar. Sorting on close is silent, because the player has
     * walked away, unless something was left alone to keep items safe, which is always said.
     */
    void report(Player p, Outcome o, boolean asked) {
        switch (o.status()) {
            case SORTED -> { if (asked) messages.actionBar(p, "sort.done", Placeholder.unparsed("count", Long.toString(o.items()))); }
            case ALREADY_SORTED -> { if (asked) messages.actionBar(p, "sort.already"); }
            case LEFT_SAFE -> messages.send(p, "sort.left-safe");
            case FAILED -> messages.send(p, "sort.failed");
            case NOT_ALLOWED -> { if (asked) messages.actionBar(p, "sort.not-here"); }
        }
    }

    private void firstHint(Player p, PlayerChoices choices) {
        if (choices.hinted || !feature.anythingToChoose()) return;
        choices.hinted = true;
        choices.save(plugin, p);
        messages.send(p, "sort.first-hint");
    }
}
