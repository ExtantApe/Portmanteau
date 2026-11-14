# Live tests

These run Portmanteau on real Paper servers and drive them with bots. The unit tests in `src/test` cover the transaction, the sort order and the link rules with stand-in items; these cover everything that needs a server.

Each suite builds one throwaway server in a new folder in the temp directory, listening only on 127.0.0.1 port 25620, with a flat made-up world. It's deleted afterwards, including when a test fails or the run is interrupted. Only one server runs at a time.

## What's real and what isn't

- **Real:** Paper, Portmanteau, Multiverse-Core and Multiverse-Inventories, the items and their data, players' data files, and the server being killed with `kill -9`.
- **Bots, not the game:** the players are [mineflayer](https://github.com/PrismarineJS/mineflayer) bots speaking the 26.1 protocol. On 26.2 and 26.3 they join through ViaVersion and ViaBackwards. Nothing is drawn, so nothing here says how anything looks.
- **Through ViaBackwards (26.2, 26.3):** the bots' right-clicks on blocks don't reach the server, and their movement packets come out as moves the server rejects. So there the bots send no movement, the server opens containers for them, and the helper fires the same interact event Paper fires for a right-click on a trunk. Clicks inside windows are real on every version. On 26.1.2 the bots right-click for real.
- **Plugin loading:** ViaBackwards can't load its mappings beside Multiverse under Paper's newer plugin loader, so test servers that need both use `-Dpaper.useLegacyPluginLoading=true`. This is a clash between those test plugins, not part of Portmanteau.
- **Stand-ins:** other plugins' menus, graves, previews and a shulker box opened from an item are made by the helper plugin, not by Magic, Citizens, BetonQuest, Lantern Graves or any real plugin.
- **Not covered:** the `/sort` dialog's layout and buttons (a bot can't click them), and whether a copper golem ever tries to use a trunk (the test only shows nothing moved in 30 seconds).

## Setting up

You need Java 25 and Node 22 or newer.

```bash
./gradlew build
cd test/live && npm ci
```

The first run downloads Paper from PaperMC's API and the other plugins from Modrinth into `work/cache`, which git ignores, and checks each against the checksum its publisher lists. A server means accepting the [Minecraft EULA](https://aka.ms/MinecraftEULA): the harness writes `eula=true` into each throwaway server it makes. Don't run it unless you accept it.

## Running

```bash
node run.mjs 26.3 sorting trunk crash   # some suites on one version
./all.sh                                 # every suite on 26.3, 26.2 and 26.1.2
```

| Suite | What it checks |
| --- | --- |
| `sorting` | Every container type, places that must never sort, the four hotkeys and the cooldown, a hopper, two viewers, the cursor, a half-finished drag, a spoiled write, quick transfer, `/isort`, the config modes, every item in the registry, a 3,000-round fuzz, and 20 bots for a minute. |
| `sorting-solo` | The same with the trunk switched off. |
| `trunk` | Recipes and per-group overrides, linking, kids and adults, per-player storage, breaking, hoppers, comparators, a hopper minecart, a dropper and a copper golem, a trunk item in a bundle, shelf, fox and grave, teleport, death and disconnect with a trunk open, damaged data, the admin copy, a read-only admin view, a spoiled save, and a 2,000-round fuzz. |
| `trunk-solo` | The same with sorting switched off. |
| `crash` | `kill -9` at several moments after moving items into a trunk, with players saved every tick, and a stop mid-move; then every item counted. Also a crash after sorting and a Multiverse-Inventories teleport with a chest open. |
| `claims` | GriefPrevention container trust: the owner and trusted players sort a claimed chest; a stranger and an admin in `/ignoreclaims` don't. Skipped where Modrinth has no GriefPrevention build for that version. |
| `prism` | A quick transfer shows in Prism as item-insert and item-remove by the player, with the counts, and nothing else. |
| `coreprotect` | A sort, then CoreProtect's rollback and restore: counts match every time. Skipped where Modrinth has no CoreProtect build for that version. |
| `import` | `/portmanteau import chestsort` from a made-up ChestSort folder with 12 numbered category files, a read-me and a config: only numbered files are copied, presets are fitted to the new categories with no warnings, and `config.yml` keeps every comment. |
| `switches` | Each feature off leaves nothing running; the trunk off and on again keeps storage. |

Each suite prints a line per check and writes `work/results-<suite>-<version>.json`. Server logs go to `work/logs/`.

## The helper plugin

`helper/` is a test-only Paper plugin with one console command, `pmt`. It counts items by their full serialized bytes, fills containers with rich random items, opens stand-in screens, runs the fuzz on real items, damages stored data on purpose, and switches the test faults on. Never install it on a real server.

The test faults are two JVM system properties, `portmanteau.test.break-writes` and `portmanteau.test.break-trunk-saves`, that make the next write come out wrong, to prove it's put back. Only the helper sets them.
