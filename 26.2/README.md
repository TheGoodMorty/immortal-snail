# Immortal Snail — README

> **Status:** ported to Minecraft **26.2** (Mojang official mappings, new Fabric Loom) and **compiling cleanly** — `gradle build` produces a working jar. See `DESIGN.md` for the full plan and "Build status" below.

A server-side Fabric mod that turns Minecraft Java Edition **26.2** (released April 7, 2026) into the **Immortal Snail** thought experiment.

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
| Minecraft Java Edition | **26.2** (released Apr 7 2026) |
| Mod loader | **Fabric Loader** `0.19.x` |
| Mappings | **Mojang official** (unobfuscated — no Yarn) |
| Modding API | **Fabric API** `0.158.0+26.2` |
| Config library | **Cloth Config** `26.2.155` |
| Optional UI | **JEI** (Just Enough Items) — soft dependency |
| Java | **25** or newer |
| Gradle | **8.5** or newer (provided by Loom) |

The mod is **server-side only** (`environment: "server"` in `fabric.mod.json`). Clients do not need it installed.

## Note on the 26.x version scheme

Java Edition 1.21.11 was the **final release using the old `1.x.y` versioning scheme** and the **final obfuscated release**. Going forward Minecraft uses the year-based scheme: **26.1, 26.2, 26.3, …** (released in 2026).

**This mod is now ported to 26.2.** The port was *not* a one-line gradle bump — 26.1+ dropped Yarn mappings entirely (Mojang official mappings are mandatory), replaced the old `fabric-loom` plugin with `net.fabricmc.fabric-loom` (which no longer remaps), raised the minimum Java to 25, and renamed a large surface of Fabric API and vanilla classes. The full Yarn→Mojang migration plus the 26.1/26.2 API renames are applied across every source file. See `DESIGN.md` §2 for the locked toolchain.

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

**Ported to 26.2 and compiling cleanly.** `gradle build` succeeds end-to-end in this environment and produces `build/libs/immortalsnail-0.1.0.jar` (there is no `remapJar` anymore — 26.1+ Minecraft is unobfuscated, so the plain `jar` output is the final mod jar). Both source sets (`main` + `client`) compile against the real `com.mojang:minecraft:26.2` artifacts, and the JEI `compileOnly` jar (`libs/jei-api-intermediate.jar`, extracted from `jei-26.2-fabric-30.24.0.176.jar`) resolves correctly.

The toolchain used:

- Java **25** (26.1+ requires Java 25 for the Gradle JVM)
- Gradle 9.7.1
- Fabric Loom **1.17.20** (`net.fabricmc.fabric-loom`)
- Fabric API **0.158.0+26.2**
- Cloth Config **26.2.155**
- Mojang official mappings (no Yarn)

Key 26.x API changes handled in this port (all verified against the real 26.2 jar via `javap`): `ResourceLocation` → `Identifier`, `ScreenRectangle` → `Rect2i`, `Spider` → `net.minecraft.world.entity.monster.spider.Spider`, `MobSpawnType` → `EntitySpawnReason`, `Registry#getKey` → `getResourceKey`, `RegistryAccess#getOrThrow` → `lookupOrThrow`, `ResourceKey#location` → `identifier`, NBT `CompoundTag` getters now return `Optional` (use the `*Or`/`*OrEmpty` helpers), `ChunkPos` is a record (`x()`/`z()`), `GameProfile` is a record (`id()`) and ban/op lists are keyed by `NameAndId`, `Commands.LEVEL_*` are now `PermissionCheck` objects (`LEVEL_GAMEMASTERS.check(op.permissions())`), `Direction#getNormal` → `getUnitVec3`, `Inventory#getItems` → `getNonEquipmentItems`, GUI `drawString`/`renderItem`/`renderTooltip` → `text`/`item`/`tooltip` on `GuiGraphicsExtractor`, `Screen#render` → `extractRenderState`/`extractBackground`, `MenuScreens` moved to `net.minecraft.client.gui.screens`, `EntityModel#setAngles` → `setupAnim`, and `ClientboundLevelParticlesPacket` gained a second `boolean` (`alwaysShow`).

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