# Immortal Snail

A server-side take on the classic thought experiment: take infinite wealth and immortality, and one snail is dispatched to finish you off. It moves ~1 block per minute. It cannot be stopped, damaged, distracted, or reasoned with. The moment it touches you, your account is banned from the world — permanently.

Works on Fabric for **Minecraft 1.21.11** and **Minecraft 26.2**. Install on the server **and** on each client (the bargain screen is a custom menu that needs the client mod to render).

## The bargain

The first time you ever join, two things happen immediately:

1. You receive **starter gear**: a shulker box containing 12 max-enchanted pieces of gear — the full netherite set, sword, pickaxe, axe, shovel, bow (Power V/Infinity/Flame/Punch II), crossbow, trident, mace, and elytra — plus a Totem of Undying, 4 enchanted golden apples, 16 ender pearls, 64 golden carrots, and ten stacks of building materials (stone, logs, torches, gold/diamond/emerald/iron blocks, obsidian, food, redstone). A separate shulker with 128 firework rockets is included for elytra travel. Anything that doesn't fit in your inventory drops at your feet.
2. Ten ticks later, **The Bargain** screen opens. Two empty shulker boxes (27 slots each) and a scrollable item picker are shown; the picker contains every registered item. Left-click a picker item to place a full max stack in the next empty slot; right-click to place it in the slot you've selected (yellow highlight); right-click a slot to empty it. **Clear** resets both boxes; **Confirm** submits. If you have JEI installed you can also drag ingredients straight from JEI into the slots, and hovering shows normal tooltips; JEI positions its sidebar around the screen automatically.

On confirm you're handed the two filled shulkers as real items and the bargain is recorded permanently in the world save. There is no decline — you can log out to postpone it, but the screen reopens (and the starter kit is re-delivered) on every subsequent login until you submit.

A small list of items can never be placed into the bargain shulkers: command blocks (all variants plus the minecart), structure block and structure void, jigsaw, barrier, light, and spawner. This list is fixed in the mod as a safeguard against trivially breaking the world.

## Immortality, and what it costs

Once you've joined a world running this mod, you are functionally unkillable: **all damage from any source other than the snail is cancelled** — mobs, lava, falling, the void, suffocation, and hunger (your food and saturation are pinned at full every tick, so you never starve). This applies to everyone, including the singleplayer host.

Exactly one thing kills you: **the snail's touch**. The snail deals 1,000,000 damage on contact — enough to bypass everything. Totems of Undying work against it by default (vanilla behavior); if the server owner sets \`totemsWorkAgainstSnail = false\`, any totems in your inventory are silently removed the moment a lethal snail blow lands, so nothing can save you.

## The snail

One snail spawns the first time anyone joins, 5,000–50,000 blocks from world origin in the overworld. It has one target rule: the closest player on the server who isn't in spectator mode. It walks straight toward them:

- **Speed:** 1 block per minute by default. At spawn distance that gives you a day-to-weeks head start; use \`/snail locate\` to see the live ETA.
- **Terrain:** it climbs walls and ceilings spider-style at 3× its ground speed and sticks to surfaces over ledges, releasing only when the target is below it. It falls with normal gravity and re-climbs.
- **Obstacles:** if it has made zero progress for 60 seconds, it starts chewing through whatever block is in its path — block particles visible to everyone nearby — at 1 block per minute. Bedrock, obsidian, and end portal frames are on its no-eat list; everything else is food. Both the whitelist/blacklist and the eat-rate are configurable.
- **Persistence:** it never despawns, is fully invulnerable, ignores knockback, and its chunk is force-loaded so it keeps crawling even with nobody nearby. If its entity is somehow lost, the mod rebuilds it at the last saved position. State is stored in \`<world>/immortalsnail/spawned.dat\` and survives restarts.
- **The catch:** contact triggers 1,000,000 damage. The player is added to the **vanilla ban list** — permanently, source "The Snail", reason configurable (\`banMessage\`, default "The snail caught you.") — and disconnected with that message.

While the snail walks and chews it emits block-break particles, and its name plate (just "The Snail") is always visible, so being close to it is something you'll notice.

## Admin commands

All are op level 2+; non-ops can be granted access through the \`commandAllowedPlayers\` list (names or UUIDs, case-insensitive).

| Command | Effect |
|---------|--------|
| \`/snail status\` | Position, distance from world origin, configured speed, current nearest player, and whether block breaking is on. |
| \`/snail locate\` | Position, the player it's chasing, distance, and an arrival ETA in minutes. |
| \`/snail remove\` | Removes the snail entirely (until the next respawn command). |
| \`/snail respawn here\` | Respawns it at your feet. |
| \`/snail respawn nearby\` | Respawns it 5 blocks ahead of where you're facing. |
| \`/snail respawn random\` | Re-rolls a random spawn point within the configured distance range. |
| \`/snail respawn <x y z>\` | Respawns it at a coordinate; supports \`~\` relative coordinates. |
| \`/snail reload\` | Reloads \`config/immortalsnail-common.toml\` without a restart. |

## Configuration

\`config/immortalsnail-common.toml\`, created on first run, live-reloadable with \`/snail reload\`:

| Section — Key | Default | What it does |
|---------------|---------|--------------|
| \`snail\` — \`minDistance\` / \`maxDistance\` | 5000 / 50000 | Spawn distance range from world origin. |
| \`snail\` — \`speedBlocksPerMinute\` | 1.0 | Ground speed. |
| \`snail\` — \`canClimbWalls\` / \`climbSpeedMultiplier\` | true / 3.0 | Wall and ceiling climbing, and its speed relative to ground movement. |
| \`snail\` — \`canBreakBlocks\` | true | Whether it chews through obstacles when stuck. |
| \`snail\` — \`breakSpeedBlocksPerSecond\` | 0.0167 | Block-eat rate (default = one block per minute). |
| \`snail\` — \`breakBlocksWhitelist\` / \`breakBlocksBlacklist\` | [] / [bedrock, obsidian, end_portal_frame] | If a whitelist is set, only those blocks are eaten; the blacklist always wins. |
| \`snail\` — \`stuckBreakAfterTicks\` | 1200 | How long (in ticks) it must make no progress before it starts chewing. |
| \`snail\` — \`chunkForceRadius\` | 1 | Radius of chunks kept loaded around it. |
| \`starter\` — \`giveStarterShulker\`, \`includeFoodInStarter\`, \`includeBasicMaterials\` | true | Toggles for the starter kit's parts. |
| \`starter\` — \`bargainShulkerCount\` / \`bargainShulkerSize\` | 2 / 27 | How many choice shulkers, and their slot count. |
| \`death\` — \`banOnSnailKill\` | true | Whether a catch bans the player. |
| \`death\` — \`banMessage\` | "The snail caught you." | The ban reason and disconnect message. |
| \`death\` — \`totemsWorkAgainstSnail\` | true | Whether Totems of Undying can save the target from the snail. |
| \`commandAllowedPlayers\` | [] | Non-ops allowed to use \`/snail\` commands. |

## Compatibility and requirements

The mod adds one entity, one menu, and three packets; it doesn't modify worldgen or vanilla behaviors beyond the bargain itself. **Dependencies:** Fabric Loader (≥ 0.19.0 on 26.2), Fabric API, and Cloth Config. **JEI is optional** — without it, the picker still shows every item; with it, you additionally get ghost-ingredient drag-and-drop. Both the client and the server need the mod installed so the bargain screen can render.
