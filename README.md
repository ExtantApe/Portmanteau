# Portmanteau

Chest and inventory sorting for Paper that checks every item survives each sort, plus a multiversal trunk: a craftable block that opens 54 slots of your own storage, shared across linked worlds.

**Status: 0.1, early.** Tested on throwaway Paper 26.1.2, 26.2 and 26.3 servers with bots: 583 live checks passed for 0.1.0, and for 0.1.1 the ChestSort import (39 more checks) and both sorting suites passed again, including crash tests that `kill -9` the server while items move in and out of a trunk, with nothing lost or duplicated, and CoreProtect rollbacks after a sort (on 26.1.2 and 26.2; there's no CoreProtect build for 26.3 yet). It hasn't been seen in a real game client yet, so the `/sort` dialog's layout, how messages read, middle-click in survival, and the trunk's look are unchecked. Other plugins' screens were tested with stand-ins, not the real plugins. See [test/live](test/live/README.md) for what was real and what wasn't.

## What it does

### Sorting

- Chests, barrels, placed shulker boxes, your ender chest, donkeys, mules, llamas, chest boats and chest minecarts sort themselves when you close them. If someone else still has it open, it waits for the last person to close it.
- Your own inventory sorts when you close it or a container. The hotbar stays as it is unless you choose otherwise.
- Hotkeys, with nothing on the cursor: shift-click, double-click or shift-right-click an empty slot, or middle-click any slot outside creative.
- `/isort` sorts your inventory. `/isort hotbar` sorts just the hotbar, `/isort all` both.
- `/sort` opens a short setup screen. Depending on what the server allows, you can turn sorting on or off, pick an order for chests, your inventory and your hotbar, put up to three kinds of items first, and choose your hotkeys.
- Admins decide how much players may change, from nothing to everything (`player-choices`, 0 to 100), and whether sorting is `forced`, `default-on`, `default-off` or `off`, separately for containers and inventories.
- Quick transfer, off unless an admin turns it on: click outside the window to move items the container already holds into it, or right-click to take them out. The hotbar never moves.

### Never losing an item

Every sort is a transaction. Portmanteau copies the contents, plans the new layout, and checks that every item is still there in the same amount, compared by its full data, so two swords that swapped enchantments would count as a change. It re-reads the container in case a hopper or another player changed it, writes the new layout in one step, and reads it back. If anything doesn't add up, the container is left exactly as it was, the player is told kindly, and the console says why. All of it happens in one go on the server's main thread, so a crash can't leave half a sort behind.

Only real vanilla containers are sorted, plus the multiversal trunk. Other plugins' menus, graves, backpacks and previews, and a shulker box opened from an item, are never touched, without those plugins having to do anything.

Sorting a sorted chest writes nothing, and the same chest always sorts the same way.

### The multiversal trunk

- Craft a Multiversal Key from an eye of ender above a recovery compass. Then the key above an ender chest above a chest makes a multiversal trunk. Both recipes can be changed, per group too, and there are `easy` and `endgame` presets.
- Place the trunk and open it: 54 slots of your own storage. Everyone sees only their own, like an ender chest.
- Every trunk in the same inventory group opens the same storage, from any world in that group. Admins can link groups.
- Kids' and adults' groups can never share storage, whatever the config says. A link that would join them is refused when the config loads, and nothing is shared.
- Breaking a trunk, by hand or by an explosion, drops only the trunk. What's stored stays yours.
- Hoppers, droppers and comparators can't reach in or read it.
- The contents live in each player's own data, saved together with their inventory, so a crash can't split the two. A readable copy is written for admins after every save ([format](docs/trunk-copies.md)).
- If a player's stored data can't be read, that trunk stays shut and the data is left untouched, rather than showing an empty trunk.

## Install

1. Paper 26.1 or newer, Java 25.
2. Put `Portmanteau-<version>.jar` in `plugins/` and restart.
3. Optional: Multiverse-Inventories for the trunk's groups, GriefPrevention for claim checks.
4. Edit `plugins/Portmanteau/config.yml`; see [docs/config.md](docs/config.md). `/portmanteau reload` applies changes.

Coming from ChestSort: `/portmanteau import chestsort` copies its category files (only the ones whose name starts with a number), its sorting method and its disabled worlds, and takes category names that no longer exist out of the presets. Every other ChestSort setting is listed for you to set by hand. Your current categories are moved aside first, and `config.yml` keeps its comments.

## Commands

| Command | Who | |
| --- | --- | --- |
| `/sort` | everyone | Open the sorting setup. |
| `/sort reset` | everyone | Go back to the server's settings. |
| `/isort`, `/isort hotbar`, `/isort all` | everyone | Sort your inventory. |
| `/portmanteau status` | admins | Settings, open trunks, and sort timing. |
| `/portmanteau reload` | admins | Reload the config, categories and messages. |
| `/portmanteau categories` | admins | The categories in order, with how many items each holds. |
| `/portmanteau import chestsort` | admins | Copy ChestSort's categories and method. |
| `/portmanteau trunk give <player> key\|trunk [amount]` | admins | For testing or replacing a lost trunk. |
| `/portmanteau trunk view <player>` | admins | A read-only look at a player's trunk for the world they're in. |
| `/portmanteau trunk scopes` | admins | Which storage each world's trunks open, and any refused links. |

Permissions: `portmanteau.sort`, `portmanteau.inventory`, `portmanteau.hotkeys`, `portmanteau.quicktransfer` and `portmanteau.trunk.use` are on for everyone; `portmanteau.admin` is for ops.

## Limits

- Middle-click may never reach the server outside creative, depending on the client, and many mice and trackpads have no middle button. The other hotkeys are the main ones.
- With sorting forced on, a chest a client-side sorting mod rearranged is sorted again when it's closed. Nothing on the server can tell those clicks from a player's.
- Changing trunk links changes which storage a trunk opens. Nothing is lost, and the old storage comes back if the link is undone, but link groups before players start using trunks.
- Trunks never span servers.
- Version 0.1 has no command to restore a trunk from its admin copy.

## For developers

`PortmanteauSortEvent` fires before any sort and can be cancelled, or told to leave slots alone. `PortmanteauSortedEvent` fires after a sort that was checked and written. `SortingApi.sort(inventory, player)` sorts your own plain-storage inventory through the same checks.

Build with `./gradlew build`. The live tests in [test/live](test/live/README.md) run real Paper servers with bots.

## License

MIT. See [LICENSE](LICENSE).
