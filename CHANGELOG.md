# Changelog

## 0.1.2

- **Quick transfers are recorded in Prism** (4.4 and up) when it's installed, as item-insert and item-remove by the player at the container. Each kind of item gets one record with the count moved, and only once the transfer has stuck. A sort moves nothing in or out, so it isn't recorded. Portmanteau finds Prism by itself, doesn't need it, and says once if it can't reach Prism's API. Prism replaced CoreProtect on the owner's servers (D168).
- Tested with bots on throwaway Paper 26.1.2 and 26.3 servers with Prism 4.5: both directions were recorded with the right counts and the player's name, and nothing else at the chest.

## 0.1.1

Fixes to `/portmanteau import chestsort`, found by trying it on a copy of a real ChestSort setup.

- Only files whose name starts with a number are copied as categories. ChestSort's read-me used to become an empty 13th category, with warnings on every reload. Anything left out is named in one line of the report. The category folder skips such files too, with one line in the console.
- After an import, the presets are fitted to the new categories: names that no longer exist are taken out of each preset, a preset left with nothing to do is removed, and if it was the default, the default goes back to `server`. The report lists each change. Before, every reload warned about the missing names.
- The import saves `config.yml` through a temporary file and a rename, so it's never left half written, and tests now check that every comment survives the import. (A report that the import dropped comments turned out to be a mistake in the test that made it: comments were always kept. Saving does still rewrite short lists like `[tools, weapons]` one item per line, and quote the method in single quotes. That's only how the file looks.)
- Owner decisions confirmed on 2026-11-08: all seven of 0.1.0's proposed design choices, including Portmanteau's own 15 default categories, and sorting staying forced on the owner's servers. See [docs/decisions.md](docs/decisions.md).

## 0.1.0

First version.

- Sorting: containers sort when the player closes them (only when they close it themselves, and only when the last viewer closes it), four hotkeys, `/isort`, quick transfer (off by default), and the player's inventory. Only real vanilla containers and the multiversal trunk are ever sorted.
- Every sort is checked: the same items, by full data, in the same amounts, written in one step and read back, or the container is left exactly as it was.
- Sorting modes per server (`forced`, `default-on`, `default-off`, `off`) and `player-choices` from 0 to 100 for what players may change in the `/sort` setup dialog: on/off, order presets for chests, inventory and hotbar, the top categories, and hotkeys.
- ChestSort's category files and sorting method strings work unchanged, plus item tags and a catch-all. `/portmanteau import chestsort` copies a server's ChestSort categories.
- GriefPrevention container trust is checked when GriefPrevention is installed.
- Cancellable `PortmanteauSortEvent`, `PortmanteauSortedEvent`, and `SortingApi` for other plugins.
- The multiversal trunk: a Multiversal Key and a trunk to craft, 54 slots of the player's own storage per inventory group, linked groups, kids' and adults' groups never linked, storage in the player's own data with a readable admin copy, no automation in or out, and breaking it never drops what's stored.


The design choices this version shipped with as proposals (the trunk being a lodestone underneath, the scope shown when it opens, the key being used up, trunks opening only in survival and adventure, no restore command, and links changing which storage opens) were confirmed by the owner on 2026-11-08. See [docs/decisions.md](docs/decisions.md).
