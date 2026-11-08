package com.extantape.portmanteau.trunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Which inventory groups share one trunk storage. A group always shares with itself. Admins may link groups, but a link
 * is only allowed when every group it joins, directly or through a chain of links, is labelled with the same audience,
 * kids or adults. A group with no label, or with both kids' and adults' worlds in it, can't be linked to anything. If
 * any link breaks the rule, every link is refused, so nothing is shared by mistake, and the errors say why.
 */
public final class LinkRules {
    private LinkRules() {}

    public enum Audience {
        KIDS, ADULTS, MIXED, UNLABELLED;

        public static Audience parse(String text) {
            if (text == null) return UNLABELLED;
            return switch (text.strip().toLowerCase(Locale.ROOT)) {
                case "kids", "kid", "children", "child" -> KIDS;
                case "adults", "adult", "grown-ups" -> ADULTS;
                case "mixed", "both" -> MIXED;
                default -> UNLABELLED;
            };
        }
    }

    public record Link(String a, String b) {}

    /**
     * @param scopes each known group's scope id; linked groups share one
     * @param errors why links were refused, in words; empty when every link is allowed
     */
    public record Result(Map<String, String> scopes, List<String> errors) {
        public String scopeOf(String group) {
            return scopes.getOrDefault(group, groupScope(group));
        }
    }

    public static String groupScope(String group) {
        return "group:" + group;
    }

    public static String worldScope(String world) {
        return "world:" + world;
    }

    /** A group's audience from its own label and its worlds' labels. Disagreement, or a mixed label, means mixed. */
    public static Audience audience(String group, Set<String> worlds, Map<String, Audience> groupLabels,
                                    Map<String, Audience> worldLabels) {
        Audience found = groupLabels.getOrDefault(group, Audience.UNLABELLED);
        if (found == Audience.MIXED) return Audience.MIXED;
        for (String w : worlds) {
            Audience a = worldLabels.getOrDefault(w, Audience.UNLABELLED);
            if (a == Audience.UNLABELLED) continue;
            if (a == Audience.MIXED) return Audience.MIXED;
            if (found == Audience.UNLABELLED) found = a;
            else if (found != a) return Audience.MIXED;
        }
        return found;
    }

    public static Result resolve(Map<String, Set<String>> groups, Map<String, Audience> groupLabels,
                                 Map<String, Audience> worldLabels, List<Link> links) {
        Map<String, String> scopes = new HashMap<>();
        for (String g : groups.keySet()) scopes.put(g, groupScope(g));
        if (links.isEmpty()) return new Result(scopes, List.of());

        List<String> errors = new ArrayList<>();
        Map<String, String> parent = new HashMap<>();
        for (String g : groups.keySet()) parent.put(g, g);
        for (Link l : links) {
            if (!groups.containsKey(l.a()) || !groups.containsKey(l.b())) {
                String missing = !groups.containsKey(l.a()) ? l.a() : l.b();
                errors.add("The link " + l.a() + " + " + l.b() + " names \"" + missing + "\", which isn't an inventory group.");
                continue;
            }
            parent.put(find(parent, l.a()), find(parent, l.b()));
        }
        Map<String, Set<String>> components = new HashMap<>();
        for (String g : groups.keySet()) components.computeIfAbsent(find(parent, g), k -> new TreeSet<>()).add(g);

        for (Set<String> members : components.values()) {
            if (members.size() < 2) continue;
            Audience shared = null;
            for (String g : members) {
                Audience a = audience(g, groups.get(g), groupLabels, worldLabels);
                switch (a) {
                    case UNLABELLED -> errors.add("Group " + g + " has no audience label, so it can't be linked to "
                            + others(members, g) + ". Label it kids or adults.");
                    case MIXED -> errors.add("Group " + g + " holds both kids' and adults' worlds, so it can never be linked to "
                            + others(members, g) + ".");
                    default -> {
                        if (shared == null) shared = a;
                        else if (shared != a) errors.add("Links join kids' and adults' groups (" + String.join(", ", members)
                                + "). Kids' and adults' groups can never share a trunk.");
                    }
                }
            }
        }
        if (!errors.isEmpty()) {
            errors.add("Because of this, no groups are linked: each group keeps its own trunk storage.");
            return new Result(scopes, List.copyOf(errors));
        }
        for (Set<String> members : components.values()) {
            if (members.size() < 2) continue;
            String id = "linked:" + String.join("+", members);
            for (String g : members) scopes.put(g, id);
        }
        return new Result(scopes, List.of());
    }

    private static String others(Set<String> members, String g) {
        List<String> rest = new ArrayList<>(members);
        rest.remove(g);
        return String.join(", ", rest);
    }

    private static String find(Map<String, String> parent, String g) {
        String p = parent.get(g);
        if (p.equals(g)) return g;
        String root = find(parent, p);
        parent.put(g, root);
        return root;
    }
}
