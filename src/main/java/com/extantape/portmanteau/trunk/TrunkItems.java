package com.extantape.portmanteau.trunk;

import com.extantape.portmanteau.text.Messages;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * The Multiversal Key and the multiversal trunk item. Each is recognised by a persistent data value, never by its name,
 * so a renamed look-alike can't stand in for it, and later features can recognise the key. The trunk item holds no items
 * and no owner: it's only a way in, so copying one copies nothing.
 */
final class TrunkItems {
    static final String KEY = "multiversal_key", TRUNK = "multiversal_trunk";
    static final Material KEY_MATERIAL = Material.RECOVERY_COMPASS;
    static final Material TRUNK_MATERIAL = Material.LODESTONE;

    private final NamespacedKey itemKey;
    private final Messages messages;
    private final TrunkConfig.Look look;

    TrunkItems(Plugin plugin, Messages messages, TrunkConfig.Look look) {
        this.itemKey = new NamespacedKey(plugin, "item");
        this.messages = messages;
        this.look = look;
    }

    ItemStack key() {
        return make(KEY_MATERIAL, KEY, "trunk.key-name", "trunk.key-lore", look.keyModel());
    }

    ItemStack trunk() {
        return make(TRUNK_MATERIAL, TRUNK, "trunk.trunk-name", "trunk.trunk-lore", look.trunkModel());
    }

    /** What the placed block shows through its display, or null with no resource pack model set. */
    ItemStack blockLook() {
        if (look.blockModel() == null || look.blockModel().isBlank()) return null;
        ItemStack item = new ItemStack(TRUNK_MATERIAL);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key(look.blockModel()));
        return item;
    }

    private ItemStack make(Material material, String id, String nameKey, String loreKey, String model) {
        ItemStack item = new ItemStack(material);
        Audience server = Audience.empty();
        item.setData(DataComponentTypes.ITEM_NAME, messages.get(server, nameKey));
        List<Component> lore = new ArrayList<>();
        for (String line : messages.raw(server, loreKey).split("\n")) {
            lore.add(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(line)
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        item.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        if (model != null && !model.isBlank()) item.setData(DataComponentTypes.ITEM_MODEL, Key.key(model));
        item.editPersistentDataContainer(pdc -> pdc.set(itemKey, PersistentDataType.STRING, id));
        return item;
    }

    String kind(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        return item.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    boolean isKey(ItemStack item) {
        return KEY.equals(kind(item));
    }

    boolean isTrunk(ItemStack item) {
        return TRUNK.equals(kind(item));
    }

    boolean isOurs(ItemStack item) {
        return kind(item) != null;
    }
}
