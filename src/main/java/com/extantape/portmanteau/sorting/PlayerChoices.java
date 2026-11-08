package com.extantape.portmanteau.sorting;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A player's own sorting choices, kept in their player data. Anything they haven't chosen is null and follows the
 * server's defaults, so a later change to the defaults still reaches them.
 */
public final class PlayerChoices {
    /** The parts of storage that can each have their own order. */
    public enum Section { CHESTS, INVENTORY, HOTBAR }

    /** A preset, and categories to put first. */
    public record Order(String preset, List<String> first) {
        public Order {
            first = List.copyOf(first);
        }

        String encode() {
            return (preset == null ? "" : preset) + ";" + String.join(",", first);
        }

        static Order decode(String text) {
            if (text == null) return null;
            int semi = text.indexOf(';');
            String preset = semi < 0 ? text : text.substring(0, semi);
            List<String> first = semi < 0 || semi == text.length() - 1 ? List.of()
                    : Arrays.stream(text.substring(semi + 1).split(",")).filter(s -> !s.isBlank()).toList();
            return new Order(preset.isEmpty() ? null : preset, first);
        }
    }

    public enum Hotkey { MIDDLE_CLICK, SHIFT_CLICK, DOUBLE_CLICK, SHIFT_RIGHT_CLICK }

    public Boolean autoChests;
    public Boolean autoInventory;
    public Boolean hotbar;
    public final Map<Section, Order> orders = new EnumMap<>(Section.class);
    public final Map<Hotkey, Boolean> hotkeys = new EnumMap<>(Hotkey.class);
    public boolean hinted;

    private static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, "sorting");
    }

    public static PlayerChoices load(Plugin plugin, Player player) {
        PlayerChoices c = new PlayerChoices();
        PersistentDataContainer root = player.getPersistentDataContainer().get(key(plugin), PersistentDataType.TAG_CONTAINER);
        if (root == null) return c;
        c.autoChests = bool(root, plugin, "auto_chests");
        c.autoInventory = bool(root, plugin, "auto_inventory");
        c.hotbar = bool(root, plugin, "hotbar");
        for (Section s : Section.values()) {
            Order o = Order.decode(root.get(new NamespacedKey(plugin, "order_" + s.name().toLowerCase()), PersistentDataType.STRING));
            if (o != null) c.orders.put(s, o);
        }
        for (Hotkey h : Hotkey.values()) {
            Boolean b = bool(root, plugin, "hotkey_" + h.name().toLowerCase());
            if (b != null) c.hotkeys.put(h, b);
        }
        c.hinted = Boolean.TRUE.equals(bool(root, plugin, "hinted"));
        return c;
    }

    public void save(Plugin plugin, Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        PersistentDataContainer root = pdc.getAdapterContext().newPersistentDataContainer();
        put(root, plugin, "auto_chests", autoChests);
        put(root, plugin, "auto_inventory", autoInventory);
        put(root, plugin, "hotbar", hotbar);
        for (var e : orders.entrySet()) {
            root.set(new NamespacedKey(plugin, "order_" + e.getKey().name().toLowerCase()), PersistentDataType.STRING, e.getValue().encode());
        }
        for (var e : hotkeys.entrySet()) put(root, plugin, "hotkey_" + e.getKey().name().toLowerCase(), e.getValue());
        if (hinted) put(root, plugin, "hinted", true);
        if (root.isEmpty()) pdc.remove(key(plugin));
        else pdc.set(key(plugin), PersistentDataType.TAG_CONTAINER, root);
    }

    /** Forgets every choice except having seen the hint. */
    public void reset() {
        autoChests = autoInventory = hotbar = null;
        orders.clear();
        hotkeys.clear();
    }

    public List<String> describe() {
        List<String> out = new ArrayList<>();
        if (autoChests != null) out.add("chests " + (autoChests ? "on" : "off"));
        if (autoInventory != null) out.add("inventory " + (autoInventory ? "on" : "off"));
        if (hotbar != null) out.add("hotbar " + (hotbar ? "included" : "left alone"));
        orders.forEach((s, o) -> out.add(s.name().toLowerCase() + " order " + o.encode()));
        return out;
    }

    private static Boolean bool(PersistentDataContainer c, Plugin plugin, String name) {
        Byte b = c.get(new NamespacedKey(plugin, name), PersistentDataType.BYTE);
        return b == null ? null : b != 0;
    }

    private static void put(PersistentDataContainer c, Plugin plugin, String name, Boolean value) {
        if (value != null) c.set(new NamespacedKey(plugin, name), PersistentDataType.BYTE, (byte) (value ? 1 : 0));
    }
}
