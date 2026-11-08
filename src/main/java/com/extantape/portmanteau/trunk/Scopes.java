package com.extantape.portmanteau.trunk;

import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Works out which storage a trunk opens, from the world the trunk is in, never from whatever inventory the player holds
 * at that moment. Each inventory group is one scope; linked groups share one; a world in no group is its own scope. When
 * the groups can't be read, or a world is in more than one group, there's no scope and the trunk stays shut.
 */
final class Scopes {
    /** A storage scope. {@code group} is the inventory group, or null for a world on its own. */
    record Scope(String id, String label, String group) {}

    record Resolution(Scope scope, String problem, List<String> linkErrors) {}

    private final GroupSource source;
    private final TrunkConfig config;

    Scopes(GroupSource source, TrunkConfig config) {
        this.source = source;
        this.config = config;
    }

    GroupSource source() {
        return source;
    }

    /** The link rules, checked against the groups as they are now. */
    LinkRules.Result links() {
        Map<String, Set<String>> groups = source == null ? null : source.groups();
        if (groups == null) return new LinkRules.Result(Map.of(), List.of("The inventory groups can't be read."));
        return LinkRules.resolve(groups, config.groupAudiences(), config.worldAudiences(), config.links());
    }

    Resolution resolve(World world) {
        if (source == null) return new Resolution(null, "Multiverse-Inventories isn't running, and the config says to use it", List.of());
        List<String> groups = source.groupsFor(world.getName());
        if (groups == null) return new Resolution(null, "the inventory groups can't be read", List.of());
        if (groups.size() > 1) {
            return new Resolution(null, "world " + world.getName() + " is in more than one inventory group (" + String.join(", ", groups) + ")", List.of());
        }
        if (groups.isEmpty()) {
            String w = world.getName();
            return new Resolution(new Scope(LinkRules.worldScope(w), config.names().getOrDefault(w, pretty(w)), null), null, List.of());
        }
        String group = groups.getFirst();
        LinkRules.Result links = links();
        String id = links.scopeOf(group);
        return new Resolution(new Scope(id, label(id, group), group), null, links.errors());
    }

    /** The group a world is in, for per-group recipes. Null for none or more than one. */
    String groupOf(World world) {
        if (source == null) return null;
        List<String> groups = source.groupsFor(world.getName());
        return groups != null && groups.size() == 1 ? groups.getFirst() : null;
    }

    private String label(String id, String group) {
        if (!id.startsWith("linked:")) return config.names().getOrDefault(group, pretty(group));
        List<String> names = new ArrayList<>();
        for (String g : new TreeSet<>(List.of(id.substring("linked:".length()).split("\\+")))) {
            names.add(config.names().getOrDefault(g, pretty(g)));
        }
        return String.join(" & ", names);
    }

    static String pretty(String name) {
        String words = name.replaceAll("[-_]+", " ").strip();
        return words.isEmpty() ? name : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
