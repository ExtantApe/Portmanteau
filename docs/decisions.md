# Decisions

Owner decisions for Portmanteau, newest first. Later work follows these without asking again.

| Date | Decision | Why |
| --- | --- | --- |
| 2026-11-07 | When two players have the same container open, it sorts when the last one closes it. | A chest never rearranges under someone who's still using it. |
| 2026-11-07 | Commands are `/sort` and `/isort`, owned by the plugin, with `/portmanteau` for admin tasks. | They're the names players already know. |
| 2026-11-07 | Admins set how much players may customise with `sorting.player-choices`, from 0 to 100: nothing, on/off switches, one order preset, a preset for each section, then everything (top categories for each section and hotkeys). | Lets a server go from "admin decides everything" to "players choose a lot" without separate switches for each option. |
| 2026-11-07 | Players set up sorting in a short Paper dialog, opened by `/sort`. Everything else is a command. | Quick choices that are easy to read, with keyboard navigation, without a chest menu. |
| 2026-11-07 | Players choose the order for chests, their inventory and their hotbar separately, from easy presets first, with fine-tuning of the top categories at the highest `player-choices` level. The hotbar is its own section that players can include in inventory sorting or leave out. | Quick choices, but a lot of room to configure. Replaces the brief's "hotbar never, or only with `/isort all`" question. |
| 2026-11-07 | Quick transfer by clicking outside the window is in version 1, off by default. | Admins can turn it on; it never surprises players on servers that don't. |
| 2026-11-07 | Sorting is forced on on our servers. The published default is on, with a player toggle (`default-on`). | Matches today's always-on setup here, while other servers get the friendlier default. |
| 2026-11-07 | Modrinth and Hangar only later, each after the owner's own code review, with no claims of review or testing the owner hasn't made. | Standing rule for every Minecraft plugin. |
| 2026-11-07 | One plugin, Portmanteau, with a switch per feature (sorting, multiversal trunk). The two features share only the transaction code. | The safety code can't drift apart, and splitting into two plugins later is a move, not a rewrite. |
| 2026-11-07 | Multiversal trunk: crafted only, from a Multiversal Key (recovery compass and eye of ender) plus an ender chest and a chest. 54 slots. No hoppers or other automation. Breaking it never drops stored items. Storage lives in the player's own data, per link scope, with a readable admin copy beside it. Kids' and adults' groups can never be linked, enforced in code. | From the build brief. |
| 2026-11-07 | The key recipe is the same for everyone by default. Recipes can be overridden per group, chosen from presets (`standard`, `easy`, `endgame`) or written in full. | Specialty servers, such as one with a kids' group, can make the key easier without changing everyone's. |
