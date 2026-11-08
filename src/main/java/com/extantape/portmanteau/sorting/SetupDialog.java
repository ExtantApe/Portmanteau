package com.extantape.portmanteau.sorting;

import com.extantape.portmanteau.sorting.PlayerChoices.Hotkey;
import com.extantape.portmanteau.sorting.PlayerChoices.Order;
import com.extantape.portmanteau.sorting.PlayerChoices.Section;
import com.extantape.portmanteau.text.Messages;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static com.extantape.portmanteau.sorting.SortingConfig.*;

/**
 * The player setup, opened by {@code /sort}: a short screen of quick choices, with more on it the more the server's
 * {@code player-choices} level allows. Paper dialogs work with the keyboard and read every label as text.
 */
final class SetupDialog {
    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1).lifetime(Duration.ofMinutes(15)).build();
    private static final String NONE = "none";

    private final Plugin plugin;
    private final SortingFeature feature;
    private final Messages messages;

    SetupDialog(Plugin plugin, SortingFeature feature, Messages messages) {
        this.plugin = plugin;
        this.feature = feature;
        this.messages = messages;
    }

    void open(Player p) {
        SortingConfig c = feature.config();
        if (!feature.anythingToChoose()) {
            p.showDialog(notice(p, c));
            return;
        }
        p.showDialog(main(p, c, PlayerChoices.load(plugin, p)));
    }

    private Component t(Player p, String key) {
        return messages.get(p, key);
    }

    private Dialog notice(Player p, SortingConfig c) {
        List<DialogBody> body = List.of(DialogBody.plainMessage(t(p, "setup.server-decides")), DialogBody.plainMessage(summary(p, c)));
        return Dialog.create(b -> b.empty()
                .base(DialogBase.builder(t(p, "setup.title")).body(body).build())
                .type(DialogType.notice(ActionButton.builder(t(p, "setup.ok")).build())));
    }

    private Component summary(Player p, SortingConfig c) {
        PlayerChoices ch = PlayerChoices.load(plugin, p);
        return messages.get(p, "setup.summary",
                Placeholder.component("chests", t(p, Effective.autoChests(c, ch) ? "setup.on" : "setup.off")),
                Placeholder.component("inventory", t(p, Effective.autoInventory(c, ch) ? "setup.on" : "setup.off")));
    }

    private Dialog main(Player p, SortingConfig c, PlayerChoices ch) {
        int level = c.playerChoices();
        List<DialogInput> inputs = new ArrayList<>();
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(t(p, "setup.intro"), 300));

        if (level >= CHOICES_SWITCHES && c.containers().playerMayChange()) {
            inputs.add(DialogInput.bool("auto_chests", t(p, "setup.auto-chests")).initial(Effective.autoChests(c, ch)).build());
        } else if (c.containers() == Mode.FORCED) {
            body.add(DialogBody.plainMessage(t(p, "setup.chests-forced"), 300));
        }
        if (level >= CHOICES_SWITCHES && c.inventory().playerMayChange()) {
            inputs.add(DialogInput.bool("auto_inventory", t(p, "setup.auto-inventory")).initial(Effective.autoInventory(c, ch)).build());
        } else if (c.inventory() == Mode.FORCED) {
            body.add(DialogBody.plainMessage(t(p, "setup.inventory-forced"), 300));
        }
        if (level >= CHOICES_SECTIONS && c.inventory() != Mode.OFF) {
            inputs.add(DialogInput.bool("hotbar", t(p, "setup.hotbar")).initial(Effective.hotbar(c, ch)).build());
        }
        if (level >= CHOICES_PRESET && level < CHOICES_SECTIONS) {
            inputs.add(presetInput(p, c, "order_all", t(p, "setup.order-all"), Effective.chosenOrder(c, ch, Section.CHESTS).preset()));
        }
        if (level >= CHOICES_SECTIONS) {
            for (Section s : Section.values()) {
                inputs.add(presetInput(p, c, "order_" + s.name().toLowerCase(), t(p, "setup.order-" + s.name().toLowerCase()),
                        Effective.chosenOrder(c, ch, s).preset()));
            }
        }
        if (level >= CHOICES_ALL) {
            for (Hotkey h : Hotkey.values()) {
                if (!serverHotkey(c, h)) continue;
                inputs.add(DialogInput.bool("hotkey_" + h.name().toLowerCase(), t(p, "setup.hotkey-" + h.name().toLowerCase().replace('_', '-')))
                        .initial(ch.hotkeys.getOrDefault(h, true)).build());
            }
        }

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(p, "setup.save", "setup.save-tip", (view, who) -> {
            saveMain(who, view);
            messages.send(who, "setup.saved");
        }));
        if (level >= CHOICES_ALL) {
            for (Section s : Section.values()) {
                buttons.add(button(p, "setup.fine-tune-" + s.name().toLowerCase(), "setup.fine-tune-tip", (view, who) -> {
                    saveMain(who, view);
                    who.showDialog(fineTune(who, feature.config(), s));
                }));
            }
        }
        buttons.add(button(p, "setup.reset", "setup.reset-tip", (view, who) -> {
            PlayerChoices fresh = PlayerChoices.load(plugin, who);
            fresh.reset();
            fresh.save(plugin, who);
            messages.send(who, "setup.reset-done");
        }));
        ActionButton cancel = ActionButton.builder(t(p, "setup.cancel")).build();
        return Dialog.create(b -> b.empty()
                .base(DialogBase.builder(t(p, "setup.title")).body(body).inputs(inputs).build())
                .type(DialogType.multiAction(buttons).exitAction(cancel).columns(2).build()));
    }

    private static boolean serverHotkey(SortingConfig c, Hotkey h) {
        return switch (h) {
            case MIDDLE_CLICK -> c.hotkeys().middleClick();
            case SHIFT_CLICK -> c.hotkeys().shiftClick();
            case DOUBLE_CLICK -> c.hotkeys().doubleClick();
            case SHIFT_RIGHT_CLICK -> c.hotkeys().shiftRightClick();
        };
    }

    private SingleOptionDialogInput presetInput(Player p, SortingConfig c, String key, Component label, String current) {
        List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
        // The server's own order first, then the rest in config order.
        List<Preset> presets = new ArrayList<>(c.presets().values());
        presets.sort((a, b) -> a.id().equals(c.defaultPreset()) ? -1 : b.id().equals(c.defaultPreset()) ? 1 : 0);
        for (Preset preset : presets) {
            entries.add(SingleOptionDialogInput.OptionEntry.create(preset.id(), Component.text(preset.name()), preset.id().equals(current)));
        }
        return DialogInput.singleOption(key, label, entries).width(300).build();
    }

    private void saveMain(Player p, DialogResponseView view) {
        SortingConfig c = feature.config();
        PlayerChoices ch = PlayerChoices.load(plugin, p);
        Boolean b;
        if ((b = view.getBoolean("auto_chests")) != null) ch.autoChests = b;
        if ((b = view.getBoolean("auto_inventory")) != null) ch.autoInventory = b;
        if ((b = view.getBoolean("hotbar")) != null) ch.hotbar = b;
        String all = view.getText("order_all");
        if (all != null && c.presets().containsKey(all)) {
            for (Section s : Section.values()) ch.orders.put(s, new Order(all, keepFirst(ch, s)));
        }
        for (Section s : Section.values()) {
            String chosen = view.getText("order_" + s.name().toLowerCase());
            if (chosen != null && c.presets().containsKey(chosen)) ch.orders.put(s, new Order(chosen, keepFirst(ch, s)));
        }
        for (Hotkey h : Hotkey.values()) {
            if ((b = view.getBoolean("hotkey_" + h.name().toLowerCase())) != null) ch.hotkeys.put(h, b);
        }
        ch.save(plugin, p);
    }

    private static List<String> keepFirst(PlayerChoices ch, Section s) {
        Order o = ch.orders.get(s);
        return o == null ? List.of() : o.first();
    }

    /** Pick up to three categories to come first in one section. */
    private Dialog fineTune(Player p, SortingConfig c, Section s) {
        PlayerChoices ch = PlayerChoices.load(plugin, p);
        Order current = Effective.chosenOrder(c, ch, s);
        var cats = feature.facts().categories();
        List<DialogInput> inputs = new ArrayList<>();
        String[] keys = {"first", "second", "third"};
        for (int i = 0; i < keys.length; i++) {
            String now = i < current.first().size() ? current.first().get(i) : NONE;
            List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
            entries.add(SingleOptionDialogInput.OptionEntry.create(NONE, t(p, "setup.no-change"), now.equals(NONE)));
            for (int cat = 0; cat < cats.size(); cat++) {
                String id = cats.id(cat);
                entries.add(SingleOptionDialogInput.OptionEntry.create(id, Component.text(cats.display(cat)), id.equals(now)));
            }
            inputs.add(DialogInput.singleOption(keys[i], t(p, "setup.pick-" + keys[i]), entries).width(300).build());
        }
        List<DialogBody> body = List.of(DialogBody.plainMessage(messages.get(p, "setup.fine-tune-intro",
                Placeholder.component("section", t(p, "setup.section-" + s.name().toLowerCase()))), 300));
        ActionButton save = button(p, "setup.save", "setup.save-tip", (view, who) -> {
            PlayerChoices fresh = PlayerChoices.load(plugin, who);
            List<String> first = new ArrayList<>();
            for (String k : keys) {
                String id = view.getText(k);
                if (id != null && !id.equals(NONE) && cats.indexOf(id) >= 0 && !first.contains(id)) first.add(id);
            }
            Order was = Effective.chosenOrder(feature.config(), fresh, s);
            fresh.orders.put(s, new Order(was.preset(), first));
            fresh.save(plugin, who);
            messages.send(who, "setup.saved");
            who.showDialog(main(who, feature.config(), fresh));
        });
        ActionButton back = button(p, "setup.back", "setup.back-tip",
                (view, who) -> who.showDialog(main(who, feature.config(), PlayerChoices.load(plugin, who))));
        return Dialog.create(b -> b.empty()
                .base(DialogBase.builder(t(p, "setup.fine-tune-title")).body(body).inputs(inputs).build())
                .type(DialogType.confirmation(save, back)));
    }

    /** A button whose action runs on the player's own thread, once. */
    private ActionButton button(Player p, String label, String tip, BiConsumer<DialogResponseView, Player> action) {
        return ActionButton.builder(t(p, label)).tooltip(t(p, tip)).width(150)
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player who) || !who.isOnline()) return;
                    who.getScheduler().run(plugin, task -> action.accept(view, who), null);
                }, ONCE)).build();
    }
}
