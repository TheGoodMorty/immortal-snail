# Immortal Snail — README

> **Status:** structural code complete (~21 Java files + Gradle scaffolding + texture PNGs). See `DESIGN.md` for the full plan. Not yet compiled in this environment — see "Build status" below.

A server-side Fabric mod that turns Minecraft Java Edition 1.21.11 ("Mounts of Mayhem", released December 9, 2025) into the **Immortal Snail** thought experiment.

## The thought experiment, in one paragraph

You're offered a bargain: live the rest of your life, immortal, with anything you want — but a snail that knows where you are and slowly, slowly crawls toward you. If it ever touches you, you die.

## What this mod does (the user-facing summary)

- **Players are unkillable.** No fall damage, no drowning, no void, no `/kill`, no starvation. The only thing that can end a player is the Snail.
- **On first login** the player receives a shulker box stuffed with max-enchanted Netherite gear, food, basic materials, and a one-time GUI to fill **two more shulkers with anything they want** (the "money for the bargain").
- **Once per world**, the very first time any player joins, the Snail spawns somewhere between `minDistance` and `maxDistance` blocks from world origin `(0, 0)`. From then on, it inexorably moves toward the closest online player.
- **Default speed: 0.25 blocks per minute.** Configurable. The Snail also has chunkloading so it can move and break blocks off-grid.
- **Default behavior: the Snail breaks blocks in its path** at a rate of **1 block per minute**, freezing in place while breaking. Admins can configure a whitelist of breakable blocks — anything not whitelisted becomes a permanent cage. The default is "breaks everything except bedrock/obsidian/end portal frames."
- **If the Snail ever touches a player**, that player is **permanently banned** from the server with the message `"The snail caught you."`.
- **Server operators (`op`)** are completely exempt — they're unkillable, the snail never targets them, and they can never be banned by the mod. Useful for hosting and debugging.
- **Server ops get a `/snail` command tree** for locating, inspecting, respawning, and reloading the snail.

## Why this README exists

This is the friendly "front page" of the project. The full design — every class, every config key, every caveat — is in [`DESIGN.md`](./DESIGN.md).

## Requirements (for when we do build it)

| Thing | Version |
|---|---|
| Minecraft Java Edition | **1.21.11** (Mounts of Mayhem, Dec 9 2025) |
| Mod loader | **Fabric Loader** `0.16.x` |
| Mappings | **Yarn** `1.21.11+build.6` |
| Modding API | **Fabric API** `0.140.0+1.21.11` |
| Config library | **Cloth Config** `14.x` |
| Optional UI | **JEI** (Just Enough Items) — soft dependency |
| Java | **21** or newer |
| Gradle | **8.5** or newer (provided by Loom) |

The mod is **server-side only** (`environment: "server"` in `fabric.mod.json`). Clients do not need it installed.

## Note on the 26.x version scheme

Java Edition 1.21.11 was the **final release using the old `1.x.y` versioning scheme** and the **final obfuscated release**. Going forward Minecraft uses the year-based scheme: **26.1, 26.2, 26.3, …** (released in 2026). Yarn mappings and Fabric API artifacts for 1.21.11 are final and will keep working; they're not invalidated by the 26.x releases.

If you ever want to bump this mod to a 26.x release:
- Change `minecraft_version` and `yarn_mappings` in `gradle.properties` to the new version (e.g. `26.1` and `26.1+build.1`)
- Change `fabric_version` to the matching `0.X.Y+26.1`
- Java 21+ stays the minimum — that requirement carries through the 26.x line.

The mod's logic doesn't depend on any 1.21.11-specific feature beyond what was available in 1.21.x, so porting forward should be a one-line change to the gradle properties.

## Quick start (intended, once built)

1. Drop the compiled `immortalsnail-*.jar` into your server's `mods/` folder.
2. Drop **Fabric API**, **Cloth Config**, and (optionally) **JEI** into `mods/` as well.
3. Start the server once to generate `config/immortalsnail-common.toml`.
4. Tweak the config to taste (defaults are sane).
5. Either run `/snail respawn here` to spawn the Snail at your feet, or just wait for the first player to join — the Snail auto-spawns on the world's first ever join.
6. Connect with a non-op account, complete the bargain, and try to outrun the snail. You can't.

## Default config (preview)

```toml
[snail]
minDistance = 5000
maxDistance = 50000
speedBlocksPerMinute = 0.25
canBreakBlocks = true
breakSpeedBlocksPerSecond = 0.01667       # 1 block per 60 seconds
breakBlocksWhitelist = []                 # populate to restrict; empty = breaks anything (subject to blacklist)
breakBlocksBlacklist = ["minecraft:bedrock", "minecraft:obsidian", "minecraft:end_portal_frame"]
breakProximityThreshold = 0.1             # snail must be within this many blocks to count as "touching"

[starter]
giveStarterShulker = true
bargainShulkerCount = 2
bargainShulkerSize = 27
includeFoodInStarter = true
includeBasicMaterials = true

[death]
banOnSnailKill = true
banMessage = "The snail caught you."
```

## Build status

**Working toolchain found:**
- Java 22
- Gradle 8.14 (installed at `C:\Users\mason\gradle-install\gradle-8.14`)
- Fabric Loom 1.13.1
- Fabric API 0.140.0+1.21.11
- Cloth Config 21.11.153
- Yarn mappings 1.21.11+build.6-v2 (mojmap namespace)

**Build is reaching Java compilation** but failing because the source code uses legacy Yarn names (pre-1.16.4-style), while Yarn 1.21.11 uses Mojang's official `mojmap` namespace for many classes. Example renames:

| Legacy Yarn | Yarn 1.21.11 |
|---|---|
| `net.minecraft.world.entity.player.Player` | `net.minecraft.entity.player.PlayerEntity` |
| `net.minecraft.network.FriendlyByteBuf` | `net.minecraft.network.PacketByteBuf` |
| `net.minecraft.network.codec.StreamCodec` | `net.minecraft.network.codec.PacketCodec` |
| `net.minecraft.world.inventory.AbstractContainerMenu` | `net.minecraft.screen.ScreenHandler` |
| `net.minecraft.world.inventory.MenuType` | `net.minecraft.screen.ScreenHandlerType` |
| `net.minecraft.server.level.ServerLevel` | `net.minecraft.server.world.ServerWorld` |
| `net.minecraft.resources.ResourceLocation` | `net.minecraft.util.Identifier` |
| `net.minecraft.core.Registry` | `net.minecraft.registry.Registry` |

These mechanical renames are being applied across all 21 source files. Once complete, a second `gradle build` pass should surface only the *real* API mismatches (if any).

## What we agreed on (locked decisions)

- **Mod loader:** Fabric
- **Minecraft version:** 1.21.11
- **Item picker UI:** JEI-backed (with built-in fallback picker if JEI is absent)
- **Blacklist:** conservative (command blocks, structure blocks, jigsaw, barrier, light, spawner)
- **Operator behavior:** ops are completely exempt from the mod
- **No-players-online behavior:** Snail freezes
- **Death punishment:** permanent ban via vanilla ban list
- **Bargain trigger:** per-player first-ever login
- **Snail trigger:** world's first-ever player login
- **Config style:** server-side TOML (`config/immortalsnail-common.toml`)

## What is still open (for later rounds)

These are decisions I flagged but didn't need to lock before writing the plan. We'll resolve them when we get to each subsystem:

- Default starter gear contents (proposed list is in `DESIGN.md` §3.6) — tweak as you like.
- Whether ops should see the Snail's position in `/snail locate` output (default: yes).
- Whether to expose a `/snail speed <blocks/min>` for live tuning without a config edit.
- Whether to log Snail state periodically for debugging (default: every 5 minutes).

## Reading order

1. This file (you're here).
2. [`DESIGN.md`](./DESIGN.md) — the full plan, every class and method I intend to write.
3. (Future) source files and `DESIGN.md` updates as we implement.

## License

To be decided. Default suggestion: MIT, since this is a vanilla-feeling gameplay mod with no copied assets.
