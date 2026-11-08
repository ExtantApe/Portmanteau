package com.extantape.portmanteau.trunk;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Groups written in Portmanteau's own config, for servers without Multiverse-Inventories. */
final class ConfigGroups implements GroupSource {
    private final Map<String, Set<String>> groups;

    ConfigGroups(Map<String, Set<String>> groups) {
        this.groups = Map.copyOf(groups);
    }

    @Override
    public Map<String, Set<String>> groups() {
        return groups;
    }

    @Override
    public List<String> groupsFor(String world) {
        return groups.entrySet().stream().filter(e -> e.getValue().contains(world)).map(Map.Entry::getKey).sorted().toList();
    }

    @Override
    public String describe() {
        return "the groups in Portmanteau's config (" + groups.size() + ")";
    }
}
