# Changelog

## 0.1.0

First version.

- Sorting: containers sort when the player closes them (only when they close it themselves, and only when the last viewer closes it), four hotkeys, `/isort`, quick transfer (off by default), and the player's inventory. Only real vanilla containers and the multiversal trunk are ever sorted.
- Every sort is checked: the same items, by full data, in the same amounts, written in one step and read back, or the container is left exactly as it was.
- Sorting modes per server (`forced`, `default-on`, `default-off`, `off`) and `player-choices` from 0 to 100 for what players may change in the `/sort` setup dialog: on/off, order presets for chests, inventory and hotbar, the top categories, and hotkeys.
- ChestSort's category files and sorting method strings work unchanged, plus item tags and a catch-all. `/portmanteau import chestsort` copies a server's ChestSort categories.
- GriefPrevention container trust is checked when GriefPrevention is installed.
- Cancellable `PortmanteauSortEvent`, `PortmanteauSortedEvent`, and `SortingApi` for other plugins.
- The multiversal trunk: a Multiversal Key and a trunk to craft, 54 slots of the player's own storage per inventory group, linked groups, kids' and adults' groups never linked, storage in the player's own data with a readable admin copy, no automation in or out, and breaking it never drops what's stored.
