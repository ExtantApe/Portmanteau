package com.extantape.portmanteau.sorting;

import com.extantape.portmanteau.sorting.PlayerChoices.Hotkey;
import com.extantape.portmanteau.sorting.PlayerChoices.Order;
import com.extantape.portmanteau.sorting.PlayerChoices.Section;
import com.extantape.portmanteau.sorting.category.Categories;
import com.extantape.portmanteau.sorting.order.OrderSpec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static com.extantape.portmanteau.sorting.SortingConfig.*;

/**
 * What actually applies to a player: the server's rules first, then the player's choices where the server's
 * {@code player-choices} level lets them choose.
 */
public final class Effective {
    private Effective() {}

    public static boolean autoChests(SortingConfig c, PlayerChoices p) {
        return auto(c.containers(), c.playerChoices(), p.autoChests);
    }

    public static boolean autoInventory(SortingConfig c, PlayerChoices p) {
        return auto(c.inventory(), c.playerChoices(), p.autoInventory);
    }

    private static boolean auto(Mode mode, int choices, Boolean chosen) {
        if (mode == Mode.FORCED) return true;
        if (mode == Mode.OFF) return false;
        return choices >= CHOICES_SWITCHES && chosen != null ? chosen : mode.defaultOn();
    }

    /** Whether the hotbar sorts along with the rest of the inventory when it sorts by itself or on {@code /isort}. */
    public static boolean hotbar(SortingConfig c, PlayerChoices p) {
        return c.playerChoices() >= CHOICES_SECTIONS && p.hotbar != null ? p.hotbar : c.hotbarByDefault();
    }

    public static boolean hotkey(SortingConfig c, PlayerChoices p, Hotkey h) {
        boolean server = switch (h) {
            case MIDDLE_CLICK -> c.hotkeys().middleClick();
            case SHIFT_CLICK -> c.hotkeys().shiftClick();
            case DOUBLE_CLICK -> c.hotkeys().doubleClick();
            case SHIFT_RIGHT_CLICK -> c.hotkeys().shiftRightClick();
        };
        if (!server) return false;
        return c.playerChoices() < CHOICES_ALL || p.hotkeys.getOrDefault(h, true);
    }

    /** The order the player has for a section, as far as the server lets them choose it. */
    public static Order chosenOrder(SortingConfig c, PlayerChoices p, Section s) {
        Order fallback = new Order(c.defaultPreset(), List.of());
        if (c.playerChoices() < CHOICES_PRESET) return fallback;
        Order o = c.playerChoices() < CHOICES_SECTIONS ? p.orders.get(Section.CHESTS) : p.orders.get(s);
        if (o == null) return fallback;
        String preset = o.preset() != null && c.presets().containsKey(o.preset()) ? o.preset() : c.defaultPreset();
        return new Order(preset, c.playerChoices() >= CHOICES_ALL ? o.first() : List.of());
    }

    public static OrderSpec order(SortingConfig c, Categories cats, PlayerChoices p, Section s) {
        Order o = chosenOrder(c, p, s);
        Preset preset = c.preset(o.preset());
        LinkedHashSet<String> ids = new LinkedHashSet<>(o.first());
        ids.addAll(preset.first());
        List<Integer> priorities = new ArrayList<>();
        for (String id : ids) {
            int i = cats.indexOf(id);
            if (i >= 0 && !priorities.contains(i)) priorities.add(i);
        }
        return new OrderSpec(preset.method(), priorities);
    }
}
