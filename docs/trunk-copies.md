# Trunk admin copies

After every save of a multiversal trunk, Portmanteau writes a readable copy of it for admins and backups:

```
plugins/Portmanteau/trunk-copies/<player UUID>/<scope>.yml
```

The copy is never read back. The real contents live in the player's own data (their `.dat` file), saved in the same write as their inventory. Files are named by UUID, never by player name, and the scope's characters other than letters, digits and `. _ + -` become `_`, so `group:survival` is `group_survival.yml`.

Each file is written to a `.tmp` file first and then renamed over the old one, so a reader never sees half a file. If writing fails (a full disk, a read-only folder), the trunk still saves normally, the console warns, and online admins are told, at most once every ten minutes.

## Format 1

```yaml
# Portmanteau multiversal trunk, admin copy. Format 1, described in docs/trunk-copies.md.
# A backup for admins. Portmanteau never reads this file; the player's own data holds the real contents.
format: 1
server-id: "6f0c2b7e-…"          # from plugins/Portmanteau/server-id.txt, made once per server
owner: "0c1d…-uuid"               # the player's UUID
scope: "group:survival"           # which storage this is; see below
saved-at: "2026-11-08T02:07:41Z"  # UTC
items-base64: "…"                 # every slot at once, for an exact restore
slots:
  0:
    id: "minecraft:diamond_sword"
    count: 1
    components: "[minecraft:damage=12,minecraft:enchantments={…}]"
  7:
    id: "minecraft:bread"
    count: 12
```

- `slots` lists only filled slots, numbered 0 to 53. An empty trunk has `slots: {}`.
- `components` is Minecraft's own text form of the item's data, the same as in `/give`. It's left out when the item has none.
- `items-base64` is Paper's `ItemStack.serializeItemsAsBytes` of all 54 slots, base64 encoded. It records the Minecraft data version, so a newer server can upgrade it. To restore it exactly, decode it with `ItemStack.deserializeItemsFromBytes`.
- `server-id` tells copies from different servers apart, since trunks never span servers.

## Scopes

| Scope | Meaning |
| --- | --- |
| `group:<name>` | One inventory group's storage. |
| `linked:<a>+<b>` | Groups an admin linked, sharing one storage. Names in alphabetical order. |
| `world:<name>` | A world in no inventory group, with storage of its own. |

Linking or unlinking groups changes which scope a trunk opens. Items stay in the old scope's storage, untouched, and come back if the link is undone. Link groups before players start using trunks.

## Restoring

Version 0.1 has no restore command. An admin with server access can rebuild a player's trunk from `items-base64`, with the player online and the trunk closed. A safe restore command is planned.
