# Test audit

Kept by this repo's owning session (Minecraft: Plugins Lead), per `~/Code/CLAUDE.md`, "Test self-audits". Newest first.

## 2026-11-10: first full audit (at 0.1.1)

What Portmanteau promises (README, `docs/decisions.md`, `docs/trunk-copies.md`), and what proves it. "Unit" is `src/test`; the live suites are in `test/live` (see its README for what's real and what's a stand-in), run on throwaway Paper 26.1.2, 26.2 and 26.3 servers.

### Covered

**Never losing an item**
- **A sort that would lose, add or swap anything changes nothing:** unit `TransactionTest`, 13 tests, including swapped enchantments between two swords, over-stacked items, a container that changes before the write, a write that comes out wrong, and a failed restore; live `sorting`, a spoiled write put back.
- **Every item in the registry, a 3,000-round fuzz on real items, 20 bots for a minute:** live `sorting`. Unit `fuzzEverySortKeepsEveryItemOrChangesNothing`.
- **A sorted chest writes nothing, and sorts the same way every time:** unit `sortingASortedContainerWritesNothing`, `ordersByCategoryThenNameThenColour`.
- **Crashes:** live `crash`, `kill -9` at several moments while items move into a trunk, after a sort, and during a Multiverse-Inventories teleport, then every item counted.
- **CoreProtect rollbacks after a sort match:** live `coreprotect` (26.1.2 and 26.2; no build for 26.3).

**Only real containers are sorted**
- **Other plugins' menus, graves, previews and a shulker box opened from an item are never touched:** live `sorting`, with stand-ins (not the real plugins).
- **Claims:** live `claims`, with GriefPrevention container trust: owner and trusted sort, a stranger and an admin in `/ignoreclaims` don't.

**The trunk**
- **Everyone sees only their own storage; kids' and adults' groups never share, whatever the config says:** unit `LinkRulesTest`, 7 tests, including a chain and a mixed group; live `trunk`, "kids and adults".
- **Breaking it, by hand or explosion, drops only the trunk:** live `trunk`.
- **Hoppers, droppers, comparators, a hopper minecart and a copper golem can't reach in or read it:** live `trunk`.
- **Unreadable stored data keeps the trunk shut and the data untouched:** live `trunk`, "damaged data".
- **A spoiled save keeps the previous contents; a failing admin copy never blocks the real save:** live `trunk`.
- **The admin view is read-only and keyed to the owner:** live `trunk`.
- **New in this audit: without `portmanteau.admin`, `/portmanteau trunk view` and `trunk give` are refused.** Live `trunk`, "admin view is read-only", run as a non-op first. Viewing someone else's storage is an access rule, and only the op side was tested. The trunk suite passed 37/37 on Paper 26.1.2 with it.

**Settings and import**
- **Each feature off leaves nothing running; the trunk off and on keeps storage:** live `switches`.
- **ChestSort import copies only numbered files, fits presets, keeps comments:** unit `ChestSortImportTest`; live `import`.

### Skipped, with reasons

- **The `/sort` dialog's layout and buttons, how messages read, middle-click in survival, the trunk's look:** a bot can't click a dialog; real-client list.
- **The real plugins behind the stand-ins** (Magic, Citizens, BetonQuest, LanternGraves): the stand-ins open the same kinds of inventory. A run with the real LanternGraves is worth doing when both are on the test server together.
- **Player-only commands (`/sort`, `/isort`) refused for players without their permission:** they're on for everyone by default and only change the player's own inventory, so a refusal protects nothing.
