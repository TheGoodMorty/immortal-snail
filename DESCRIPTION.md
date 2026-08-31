# Immortal Snail

This mod brings the classic immortality snail thought experiment to Minecraft servers. Upon joining, players receive near-infinite wealth and invulnerability to normal damage, but an unkillable snail spawns far away and slowly tracks down the nearest target at roughly 1 block per minute. If it reaches you, your account is permanently banned from the server.

Requires Fabric for **Minecraft 1.21.11** and **Minecraft 26.2**. This mod must be installed on both the server **and** each client to render the custom bargain interface.

## The bargain

The first time a player joins the world, two actions trigger:

1. You receive **starter gear**: a shulker box holding 12 max-enchanted gear items (full netherite armor set, sword, pickaxe, axe, shovel, bow with Power V/Infinity/Flame/Punch II, crossbow, trident, mace, and elytra), a Totem of Undying, 4 enchanted golden apples, 16 ender pearls, 64 golden carrots, and 10 stacks of building materials (stone, logs, torches, gold/diamond/emerald/iron blocks, obsidian, food, and redstone). An additional shulker box containing 128 firework rockets is provided for flight. Overflow items drop to the ground.
2. Ten ticks after joining, **The Bargain** menu appears. It presents two empty 27-slot shulker boxes alongside a scrollable item picker displaying every registered item in the game. Left-click an item in the picker to put a max stack into the next available slot; right-click an item to assign it to the currently selected slot (marked with a yellow highlight); right-click a slot to clear it. Select **Clear** to empty both boxes, or **Confirm** to lock in your choices. When JEI is installed, you can drag items directly from the sidebar into slots, view standard tooltips, and let JEI automatically arrange its sidebar around the interface.

Confirming hands over the two filled shulker boxes as physical items and permanently writes the selection to the world save. The menu cannot be declined; logging off merely postpones it, and logging back in re-delivers the starter kit and reopens the prompt until completed.

A small set of technical items cannot be picked during the bargain: all command block variants (including the minecart variant), structure blocks, structure voids, jigsaw blocks, barriers, light blocks, and spawners. This restriction is hardcoded to prevent server corruption.

## Immortality rules

Players in a modded world gain functional invulnerability: **all damage from any source other than the snail is negated**, including mobs, lava, falling, void damage, suffocation, and starvation (food and saturation levels remain locked at maximum). This applies to all players, including singleplayer hosts.

The only lethal threat is **the snail's touch**. The snail deals 1,000,000 damage upon contact, bypassing standard protection. Totems of Undying function against this attack by default. If the server administrator sets `totemsWorkAgainstSnail = false`, any Totems in your inventory are automatically cleared the moment the snail strikes, preventing survival.

## The snail

A single snail spawns when the first player joins, generating between 5,000 and 50,000 blocks away from the world origin in the Overworld. It continuously pathfinds toward the nearest non-spectator player.

* **Speed:** Moves at 1 block per minute by default. This starting distance provides a significant grace period. Run `/snail locate` to check its current ETA.
* **Terrain:** Climbs walls and ceilings like a spider at 3× ground speed, remaining attached over ledges until the target drops below it. It falls with standard gravity and resumes climbing.
* **Obstacles:** If stuck without moving for 60 seconds, it begins eating through obstruction blocks at a rate of 1 block per minute, creating visible break particles. By default, bedrock, obsidian, and end portal frames cannot be destroyed. Mining speed, whitelists, and blacklists can be modified in the configuration.
* **Persistence:** Invulnerable, immune to knockback, and never despawns. Its chunk stays force-loaded so it advances even when no players are nearby. If the entity is accidentally removed, the mod restores it at its last saved position. Snail data persists across server restarts in `<world>/immortalsnail/spawned.dat`.
* **Ban mechanics:** Contact triggers 1,000,000 damage. The killed player is added to the **vanilla ban list** under source "The Snail" with a custom message (`banMessage`, defaults to "The snail caught you.") and kicked from the server.

The snail constantly displays the nameplate "The Snail" and emits block-break particles while moving or digging, allowing players to spot it when close.

## Admin commands

Requires operator status (level 2+). Non-ops can be given access via the `commandAllowedPlayers` configuration option (accepts names or UUIDs, case-insensitive).

| Command | Effect |
| --- | --- |
| `/snail status` | Displays position, origin distance, set speed, target player, and block-breaking status. |
| `/snail locate` | Displays position, current target player, distance, and estimated arrival time in minutes. |
| `/snail remove` | Removes the snail entity from the world until respawned. |
| `/snail respawn here` | Respawns the snail directly at your position. |
| `/snail respawn nearby` | Respawns the snail 5 blocks in front of your direction. |
| `/snail respawn random` | Generates a new spawn location within the configured distance range. |
| `/snail respawn <x y z>` | Respawns the snail at specific coordinates (supports `~` relative positioning). |
| `/snail reload` | Reloads `config/immortalsnail-common.toml` without restarting the server. |

## Configuration

Configuration file located at `config/immortalsnail-common.toml` (generated on first boot, reloadable via `/snail reload`):

| Section / Key | Default | Description |
| --- | --- | --- |
| `snail`: `minDistance` / `maxDistance` | 5000 / 50000 | Spawn distance range from world origin. |
| `snail`: `speedBlocksPerMinute` | 1.0 | Base ground movement speed. |
| `snail`: `canClimbWalls` / `climbSpeedMultiplier` | true / 3.0 | Wall/ceiling climbing toggle and speed multiplier relative to ground speed. |
| `snail`: `canBreakBlocks` | true | Toggles whether the snail chews through terrain when blocked. |
| `snail`: `breakSpeedBlocksPerSecond` | 0.0167 | Block destruction rate (defaults to one block per minute). |
| `snail`: `breakBlocksWhitelist` / `breakBlocksBlacklist` | [] / [bedrock, obsidian, end_portal_frame] | Allowed and blocked terrain destruction lists. If a whitelist is set, only listed blocks can be broken; the blacklist overrides whitelist settings. |
| `snail`: `stuckBreakAfterTicks` | 1200 | Idle duration (in ticks) before block destruction begins. |
| `snail`: `chunkForceRadius` | 1 | Radius of loaded chunks maintained around the snail. |
| `starter`: `giveStarterShulker`, `includeFoodInStarter`, `includeBasicMaterials` | true | Toggles for individual components of the starter kit. |
| `starter`: `bargainShulkerCount` / `bargainShulkerSize` | 2 / 27 | Number of custom reward shulker boxes and their inventory slot size. |
| `death`: `banOnSnailKill` | true | Determines if dying to the snail results in a server ban. |
| `death`: `banMessage` | "The snail caught you." | The kick/ban screen message displayed to banned players. |
| `death`: `totemsWorkAgainstSnail` | true | Controls whether Totems of Undying protect players against snail damage. |
| `commandAllowedPlayers` | [] | List of non-operator players permitted to run `/snail` commands. |

## Compatibility and requirements

This mod adds one entity, one custom screen, and three network packets without modifying world generation or base game logic outside of the initial bargain prompt.

* **Dependencies:** Fabric Loader (≥ 0.19.0 on 26.2), Fabric API, and Cloth Config.
* **JEI Integration:** Optional. The item selection window works without JEI, but installing JEI allows drag-and-drop item selection directly into bargain slots.
* **Installation:** Required on both the server and all connecting clients to render the custom menu.