package com.extantape.portmanteau.sorting;

import com.extantape.portmanteau.sorting.ContainerGate.Kind;
import com.extantape.portmanteau.sorting.ContainerGate.Target;
import com.extantape.portmanteau.sorting.PlayerChoices.Section;
import com.extantape.portmanteau.sorting.api.PortmanteauSortEvent;
import com.extantape.portmanteau.sorting.api.PortmanteauSortedEvent;
import com.extantape.portmanteau.sorting.api.SortReason;
import com.extantape.portmanteau.sorting.claims.ClaimCheck;
import com.extantape.portmanteau.sorting.order.OrderSpec;
import com.extantape.portmanteau.sorting.order.SectionPlanner;
import com.extantape.portmanteau.sorting.order.SortEntry;
import com.extantape.portmanteau.transaction.PlanRefused;
import com.extantape.portmanteau.transaction.StackCodec;
import com.extantape.portmanteau.transaction.Transaction;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Sorts one inventory for one player, through the transaction, after every check. */
public final class Sorter {
    /** What part of a player's inventory to sort. Ignored for containers. */
    public enum Part { MAIN, HOTBAR, BOTH }

    public enum Status {
        SORTED, ALREADY_SORTED,
        /** Left alone because something didn't add up. Items are as they were. */
        LEFT_SAFE,
        /** The write and the restore both failed; logged in full. */
        FAILED,
        /** Not done here: a disabled world, no claim trust, or another plugin cancelled it. */
        NOT_ALLOWED
    }

    public record Outcome(Status status, long items, String reason) {}

    private final Plugin plugin;
    private volatile FactsCache facts;
    private volatile SortingConfig config;
    private final ClaimCheck claims;
    private long sorts, nanos, worstNanos;

    public Sorter(Plugin plugin, FactsCache facts, SortingConfig config, ClaimCheck claims) {
        this.plugin = plugin;
        this.facts = facts;
        this.config = config;
        this.claims = claims;
    }

    public void update(FactsCache facts, SortingConfig config) {
        this.facts = facts;
        this.config = config;
    }

    /** Whether claims let the player take items from this container. */
    public boolean claimsAllow(Player player, Target target) {
        return !(target.kind().placed() && config.claims() && target.where() != null && !claims.maySort(player, target.where()));
    }

    public Outcome sort(Player player, Target target, SortReason reason, Part part) {
        if (player != null && config.disabledWorlds().contains(player.getWorld().getName())) {
            return new Outcome(Status.NOT_ALLOWED, 0, "sorting is off in this world");
        }
        if (player != null && !claimsAllow(player, target)) {
            return new Outcome(Status.NOT_ALLOWED, 0, "no container trust here");
        }
        PortmanteauSortEvent event = new PortmanteauSortEvent(player, target.inventory(), reason);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return new Outcome(Status.NOT_ALLOWED, 0, "another plugin cancelled it");

        PlayerChoices choices = player == null ? new PlayerChoices() : PlayerChoices.load(plugin, player);
        FactsCache f = facts;
        SortingConfig c = config;
        boolean isPlayer = target.kind() == Kind.PLAYER;
        int size = isPlayer ? 36 : target.inventory().getSize();

        Set<Integer> fixed = new HashSet<>(target.fixed());
        fixed.addAll(event.getLockedSlots());
        List<int[]> sections = new ArrayList<>();
        List<OrderSpec> orders = new ArrayList<>();
        if (isPlayer) {
            if (part != Part.HOTBAR) {
                sections.add(range(9, 36, fixed));
                orders.add(Effective.order(c, f.categories(), choices, Section.INVENTORY));
            } else {
                for (int i = 9; i < 36; i++) fixed.add(i);
            }
            if (part != Part.MAIN) {
                sections.add(range(0, 9, fixed));
                orders.add(Effective.order(c, f.categories(), choices, Section.HOTBAR));
            } else {
                for (int i = 0; i < 9; i++) fixed.add(i);
            }
        } else {
            sections.add(range(0, size, fixed));
            orders.add(Effective.order(c, f.categories(), choices, Section.CHESTS));
        }

        StackCodec codec = new StackCodec();
        Function<ItemStack, SortEntry> describe = f.describer(codec::key);
        int limit = target.inventory().getMaxStackSize();
        boolean shulker = target.kind() == Kind.SHULKER_BOX;
        String where = describe(target, player);

        long start = System.nanoTime();
        Transaction.Result result = Transaction.apply(new InventoryStore(target.inventory(), isPlayer, where), codec, snapshot -> {
            if (shulker) {
                for (ItemStack item : snapshot) {
                    if (item != null && Tag.SHULKER_BOXES.isTagged(item.getType())) {
                        throw new PlanRefused("a shulker box holds another shulker box");
                    }
                }
            }
            for (int s = 0; s < sections.size(); s++) {
                var order = SortEntry.comparator(orders.get(s), f.categories()::sticky);
                SectionPlanner.plan(snapshot, sections.get(s), order, codec, limit, describe);
            }
            return snapshot;
        }, fixed, plugin.getLogger());
        record(System.nanoTime() - start);

        Status status = switch (result.outcome()) {
            case CHANGED -> Status.SORTED;
            case UNCHANGED -> Status.ALREADY_SORTED;
            case REFUSED, RESTORED -> Status.LEFT_SAFE;
            case FAILED -> Status.FAILED;
        };
        if (status == Status.SORTED) {
            Bukkit.getPluginManager().callEvent(new PortmanteauSortedEvent(player, target.inventory(), reason));
        }
        return new Outcome(status, result.items(), result.reason());
    }

    private static int[] range(int from, int to, Set<Integer> fixed) {
        return java.util.stream.IntStream.range(from, to).filter(i -> !fixed.contains(i)).toArray();
    }

    private static String describe(Target t, Player p) {
        String kind = t.kind().name().toLowerCase().replace('_', ' ');
        String who = p == null ? "" : " for " + p.getName();
        Location l = t.where();
        if (l == null || l.getWorld() == null) return kind + who;
        return kind + " at " + l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + who;
    }

    private synchronized void record(long took) {
        sorts++;
        nanos += took;
        worstNanos = Math.max(worstNanos, took);
    }

    public synchronized String timing() {
        if (sorts == 0) return "No sorts yet since the server started.";
        return String.format("%d sorts since the server started, %.1f µs on average, %.1f µs at worst.",
                sorts, nanos / 1000.0 / sorts, worstNanos / 1000.0);
    }
}
