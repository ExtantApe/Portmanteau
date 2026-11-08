# Configuration

Everything is in `plugins/Portmanteau/config.yml`. Run `/portmanteau reload` after editing. Switching a feature on or off takes a restart; everything else reloads. A value Portmanteau can't read falls back to a safe default, and the console and the reload command say what and why.

## features

| Key | Default | |
| --- | --- | --- |
| `features.sorting` | `true` | Sorting. Off means no sorting listeners, commands or category files at all. |
| `features.trunk` | `true` | The multiversal trunk. Off means no trunk listeners, recipes, admin commands or files. Players' stored contents stay in their data and come back when it's switched on again. While it's off, placed trunks keep their look but open nothing, and breaking one drops a plain lodestone. |

## sorting

| Key | Default | |
| --- | --- | --- |
| `containers` | `default-on` | When containers sort themselves as the player closes them: `forced`, `default-on`, `default-off` or `off`. |
| `inventory` | `default-on` | The same for the player's own inventory, when they close it or a container. |
| `hotbar-by-default` | `false` | Whether the hotbar sorts with the rest of the inventory. Players may change it at `player-choices` 75 and up. |
| `player-choices` | `100` | How much players may choose in `/sort`, 0 to 100. See below. |
| `method` | `{category},{itemsFirst},{name},{color},{customName}` | The server's order, in ChestSort's format. |
| `default-preset` | `server` | The preset everyone starts with. |
| `presets` | four examples | Orders players can pick. |
| `hotkeys.*` | all on, 200 ms | See below. |
| `quick-transfer` | `false` | Click outside the window to move matching items. |
| `disabled-worlds` | `[]` | Worlds where nothing sorts. |
| `allowed-holders` | `[]` | Class names of other plugins' inventory holders that may be sorted. |
| `claims` | `true` | Check GriefPrevention container trust before sorting a placed container. |

### The modes

- `forced`: always, and players can't turn it off. `/sort` doesn't show a switch for it.
- `default-on`, `default-off`: players choose in `/sort`, starting on or off. A player who never chooses follows the server, so changing the default later reaches them.
- `off`: never by itself. Hotkeys and `/isort` still work.

YAML reads a bare `off` as false and `on` as true. Portmanteau takes those as `off` and `default-on`.

### player-choices

Each level includes the ones below it. Values in between round down, so 60 means 50.

| Level | Players may |
| --- | --- |
| 0 | Nothing. `/sort` says the server sets sorting up. |
| 25 | Turn chest and inventory sorting on or off, where the mode isn't `forced` or `off`. |
| 50 | Pick one order preset for everything. |
| 75 | Pick a preset for chests, inventory and hotbar separately, and whether the hotbar sorts. |
| 100 | Also put up to three categories first in each, and turn each hotkey on or off. |

### The sorting method

Parts are compared in turn. Unknown parts are skipped, with a warning.

| Part | Sorts by |
| --- | --- |
| `{category}` | The category's position, with any categories the player put first at the front. |
| `{keepCategoryOrder}` | The line the item matched inside its category file. |
| `{itemsFirst}`, `{blocksFirst}` | Items before blocks, or the other way round. |
| `{tier}` | Best first: netherite, diamond, iron, gold, copper, stone or chainmail, wood or leather. |
| `{name}` | The item's name without its colour, so all wool sits together. |
| `{color}` | Dye order, as in the creative menu. |
| `{customName}` | Renamed items after unnamed ones, then by name. |
| `{lore}` | Items with lore after those without, then by the text. |

Ties always break on the item's full data, so the same chest always sorts the same way, and sorting a sorted chest writes nothing.

### Categories

`plugins/Portmanteau/categories/` holds one text file per category, in ChestSort's format, so ChestSort's files work unchanged. Files sort by the number at the start of their name. The name after the number, such as `tools` in `010-tools.txt`, is the category's id for presets.

```
# A comment.
display=Tools
sticky=true
DIAMOND_PICKAXE
*_pickaxe
#minecraft:axes
```

- One item per line, in any case, with or without `minecraft:`.
- `*` at the start or end of a line is a wildcard.
- A line that is only an item tag, such as `#minecraft:logs`, matches every item in the tag. Any other line starting with `#` is a comment.
- `sticky=true` keeps the file's own order inside the category.
- `display=` names the category in the player setup.

An item that no file matches goes to "Everything else", last. The first file that matches wins. `/portmanteau categories` lists the categories with how many items each holds, and warns about tags the server doesn't have.

### Hotkeys

| Key | Works |
| --- | --- |
| `shift-click` | Shift-click an empty slot. |
| `double-click` | Double-click an empty slot. |
| `shift-right-click` | Shift-right-click an empty slot. |
| `middle-click` | Middle-click any slot, outside creative mode. |

Only with nothing on the cursor. Clicking a slot in the container sorts the container; clicking in the player's inventory sorts it, and a hotbar slot sorts the hotbar. `cooldown-ms` is the shortest time between two hotkey sorts.

Middle-click is a bonus. Many mice and trackpads don't have a middle button, and the game may not send it outside creative at all. That hasn't been checked with a real client yet.

### What can be sorted

Chests, double and trapped chests, barrels, placed shulker boxes, the player's own ender chest, donkeys, mules and llamas with chests, chest boats and chest minecarts, the multiversal trunk, and the player's inventory. Only the slots that hold storage move: an animal's saddle and armour slots never do.

Anything else is never sorted. That covers other plugins' menus, graves, backpacks and previews, and a shulker box opened from an item. To allow one, add its inventory holder's class name to `allowed-holders`. The player's inventory only sorts under their own screen or a container on that list.

## trunk

| Key | Default | |
| --- | --- | --- |
| `groups-from` | `auto` | `auto` (Multiverse-Inventories if installed, otherwise `groups`), `multiverse-inventories` or `config`. |
| `groups` | `{}` | Groups of worlds, when not using Multiverse-Inventories. |
| `audiences.groups`, `audiences.worlds` | `{}` | `kids`, `adults` or `mixed`, per group or per world. |
| `links` | `[]` | Pairs of groups that share one storage. |
| `names` | `{}` | Names players see for groups or worlds. |
| `game-modes` | `[survival, adventure]` | Trunks open only in these. |
| `recipes` | `standard` | See below. |
| `look` | empty models | Item models from your resource pack, the sound and the particle. |
| `admin-copies` | `true` | Write the readable copies described in `trunk-copies.md`. |

### Scopes and links

A trunk opens the storage of the world it stands in, never of whatever inventory the player happens to hold. Only Multiverse-Inventories groups that share the inventory count. A world in no group has storage of its own. A world in more than one inventory group keeps its trunks shut rather than guess, and so does every trunk when Multiverse-Inventories is required but not running.

Kids' and adults' groups can never share. A link is used only if every group it joins, directly or through a chain of links, has the same audience label, `kids` or `adults`. A group with no label can't be linked. A group with both kids' and adults' worlds, or labelled `mixed`, can never be linked. If any link breaks a rule, no links are used at all, and the console says why. `/portmanteau trunk scopes` shows each world's scope and any refusals.

```yaml
trunk:
  audiences:
    groups: {survival: adults, creative: adults, kids_survival: kids}
  links:
    - [survival, creative]
  names: {survival: "Survival", creative: "Creative"}
```

Changing links changes which storage a trunk opens. See `trunk-copies.md`.

### Recipes

The key is a Multiversal Key, and the trunk recipe uses it up.

| Preset | Key | Trunk |
| --- | --- | --- |
| `standard` | An eye of ender above a recovery compass | The key above an ender chest above a chest |
| `easy` | An ender pearl above a compass | The key above a chest |
| `endgame` | A nether star above an eye of ender, recovery compass and eye of ender | The key above an ender chest above a shulker box |

Custom recipes go under `recipes.custom`. Use `portmanteau:multiversal_key` for the key.

```yaml
trunk:
  recipes:
    key: standard
    trunk: my-trunk
    custom:
      my-trunk:
        shape: ["K", "B"]
        ingredients: {K: portmanteau:multiversal_key, B: minecraft:barrel}
    groups:
      kids_survival: {key: easy}
```

`groups` overrides a recipe in one inventory group only: in the example, the easy key works in `kids_survival` and nowhere else, and the standard key doesn't work there. A key is recognised by its own data, never by its name, so a renamed recovery compass can't stand in for one. Crafters can't make either recipe, because there's no player to tell which group it's in.

### Look

The trunk is a lodestone underneath. With your resource pack's models set in `look`, an item display drawn just outside the block gives it its own look, and the items use their own models. Without a pack, leave the models empty: the trunk and key then show as glinting lodestone and recovery compass, with their own names and tooltips.

## Messages

`plugins/Portmanteau/lang/en_us.yml` holds every message players see, in MiniMessage. To translate, copy it as `lang/<locale>.yml`, such as `de_de.yml`. Players get their own language's file when there is one.
