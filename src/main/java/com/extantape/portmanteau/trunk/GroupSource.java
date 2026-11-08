package com.extantape.portmanteau.trunk;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Where inventory groups come from: groups of worlds that share one player inventory. */
interface GroupSource {
    /** Every group and its worlds, or null if the groups can't be read right now. */
    Map<String, Set<String>> groups();

    /** The groups that hold this world and share its inventory, or null if they can't be read. */
    List<String> groupsFor(String world);

    String describe();
}
