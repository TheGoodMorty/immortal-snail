# Immortal Snail — Design Document

> Companion to `README.md`. This is the implementation plan: every class, every config key, every behavior, every known caveat. Source code is not written yet; this document is the contract.

## 1. Goal & scope

A server-side Fabric mod for **Minecraft Java 1.21.11** (Yarn `1.21.11+build.6`) that turns every non-operator player into the subject of the Immortal Snail thought experiment.

### In scope

- Player immortality (all damage sources blocked, except snail-attributed damage).
- Per-player first-join bargain: starter shulker + GUI to fill two more shulkers.
- World's first-ever player join: spawn the Snail once, persistently, with chunkloading.
- Snail AI: straight-line pursuit of the closest online player, server-tick driven.
- Death-on-snail-touch → permanent ban via vanilla ban list.
- Admin commands: `/snail locate | status | respawn | reload`.
- TOML config; reloadable without restart.

### Out of scope

- Bedrock players (Java only).
- Mod is **not installed on clients** — clients receive whatever the server sends.
- Replay/integration with other mods (no JEI plugin, no LuckPerms hook, etc.).

## 2. Versions & toolchain (locked)

| Component | Version | Notes |
|---|---|---|
| Minecraft | `1.21.11` (Mounts of Mayhem, released 2025-12-09) | Newest stable per the Fandom Minecraft Wiki page. |
| Yarn mappings | `1.21.11+build.6` | Confirmed present in Fabric's mapping selector. |
| Fabric Loader | `0.16.x` | Latest stable. |
| Fabric API | `0.140.0+1.21.11` | Required. |
| Cloth Config | `14.x` | Required (TOML config). |
| JEI | latest 1.21.11 build | Soft dependency. |
| Java | 21 | Required by Minecraft 1.21+. |
| Build tool | Gradle 8.5+ via Fabric Loom 1.7+ | Standard. |

## 3. Project layout

```
immortalsnail/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── fabric.mod.json
├── README.md
├── DESIGN.md
└── src/main/
    ├── resources/
    │   ├── assets/immortalsnail/
    │   │   ├── icon.png
    │   │   ├── textures/entity/                 // Loaded by Java renderer (ResourceLocation)
    │   │   │   ├── body_side.png                // 32×32 tan body texture
    │   │   │   ├── body_top.png                 // 32×32 darker brown rim texture
    │   │   │   ├── shell_side.png               // 32×32 yellow-tan with vertical ridges
    │   │   │   └── shell_top.png                // 32×32 spiral coil viewed from above
    │   │   └── lang/en_us.json                  // "entity.immortalsnail.snail"
    │   └── immortalsnail.mixins.json
    └── java/com/yourname/immortalsnail/
        ├── ImmortalSnail.java                     // ModInitializer + FAPI server entrypoints
        ├── config/
        │   └── SnailConfig.java                   // Cloth Config TOML wrapper
        ├── player/
        │   ├── ImmortalityEvents.java             // DamageEvent + tick cancel
        │   ├── PlayerFirstJoinHandler.java        // JOIN listener
        │   └── StarterGear.java                   // Max-enchanted gear builder
        ├── bargain/
        │   ├── BargainState.java                  // Per-player persistent state
        │   ├── BargainOpenPayload.java            // C2S: open GUI request
        │   ├── BargainSubmitPayload.java          // C2S: confirm + lock
        │   ├── BargainNetworking.java             // PayloadTypeRegistry + handlers
        │   └── BargainScreen.java                 // AbstractContainerScreen, 2x27 grid
        ├── itempicker/
        │   ├── ItemPickerMenu.java                // 54+36 slot container
        │   ├── ItemPickerScreen.java              // Fallback if JEI absent
        │   ├── ItemPickerNetworking.java          // S2C open + blacklist sync
        │   └── ItemBlacklist.java                 // Predicate over Item
        ├── entity/
        │   ├── ModEntities.java                   // EntityType registration
        │   └── SnailEntity.java                   // Custom mob, AI disabled
        ├── snail/
        │   ├── SnailManager.java                  // State, save/load, spawn, chunkforce
        │   ├── SnailTickHandler.java              // ServerTickEvents.END → movement
        │   └── SnailDeathBan.java                 // Ban on snail-attributed death
        └── command/
            └── SnailCommands.java                 // /snail ...; perm level 2
```

`com.yourname.immortalsnail` will be renamed to whatever package you want at scaffolding time.

## 4. Behavior spec

### 4.1 Immortality

**Trigger conditions:**
- `entity` is a `ServerPlayer` whose UUID is in `BargainState.completedBargain` (i.e. they've finished the bargain at least once).
- `entity` is **not** an operator (`ServerPlayer.connection.permissions >= 2`).
- `damageSource.getEntity()` and `damageSource.getDirectEntity()` are **both not** a `SnailEntity`.

**What gets blocked:**
- All `LivingEntityEvents.DAMAGE` (`fabric-api`) → set damage to 0.
- Hunger: zero out the player's `foodData.foodLevel` decrement each tick by hooking into the food tick or by replacing food-loss events.
- Suffocation: in `LivingEntity#aiStep` mixin, early-return when the entity is a tracked, non-op player.
- Void: handled by damage event already (it's just damage from `IN_VOID`).
- `/kill`, `/damage`, explosions, magic, fall — all routed through the same damage event.

**Edge cases:**
- Operator → never tracked, mod is fully inert for them.
- Player who has not completed the bargain → not tracked, can die normally.
- Player who disconnects before submitting → bargain state persists, next login reopens the picker GUI; they remain mortal until they finish.
- Death by `damageSource == SnailEntity` (either direct or via projectile) → not blocked, passes through to normal damage handling → `SnailDeathBan` catches the death and bans.

### 4.2 First-join bargain

`PlayerFirstJoinHandler` listens on `ServerPlayConnectionEvents.JOIN`:
1. Check `BargainState.hasCompletedBargain(uuid)`.
2. If **yes** → no-op (normal join).
3. If **no**:
   - Tick +10 (give the client time to finish loading).
   - Deliver `StarterGear` to the player.
   - Open `BargainScreen` (S2C packet).
   - Player fills 2×27 slots, hits "Confirm".
   - Server receives `BargainSubmitPayload`, validates every slot against `ItemBlacklist`, persists `BargainState` to `<world>/immortalsnail/bargains/<uuid>.dat`.
   - On the next tick, the two shulkers are placed into the player's inventory (or dropped at feet if full).

**Starter gear (proposed default; see `snail.toml` `[starter]` to override):**

| Slot | Item | Enchantments |
|---|---|---|
| Helmet | Netherite Helmet | Protection IV, Unbreaking III, Mending, Respiration III, Aqua Affinity, Thorns III |
| Chestplate | Netherite Chestplate | Protection IV, Unbreaking III, Mending, Thorns III |
| Leggings | Netherite Leggings | Protection IV, Unbreaking III, Mending, Swift Sneak III, Thorns III |
| Boots | Netherite Boots | Protection IV, Unbreaking III, Mending, Feather Falling IV, Depth Strider III, Soul Speed III, Thorns III |
| Sword | Netherite Sword | Sharpness V, Sweeping Edge III, Unbreaking III, Mending, Looting III, Fire Aspect II |
| Pickaxe | Netherite Pickaxe | Efficiency V, Unbreaking III, Mending, Fortune III |
| Axe | Netherite Axe | Sharpness V, Efficiency V, Unbreaking III, Mending |
| Shovel | Netherite Shovel | Efficiency V, Unbreaking III, Mending, Silk Touch |
| Bow | Bow | Power V, Infinity, Flame, Punch II |
| Crossbow | Crossbow | Quick Charge III, Multishot, Mending |
| Trident | Trident | Loyalty III, Channeling, Impaling V, Mending, Unbreaking III |
| Mace (1.21.11) | Mace | Density V, Breach IV, Wind Burst III (where available) |
| Misc | Gold Blocks | — (64) |
| Misc | Enchanted Golden Apple | (4) |
| Misc | Ender Pearl | (16) |
| Misc | Golden Carrot | (64) |
| Misc | Stone | (64) |
| Misc | Oak Log | (32) |
| Misc | Torch | (64) |
| Misc | Diamond Blocks | (64) |
| Misc | Emerald Blocks | (64) |

All packed into a single Shulker Box (size 27) — overflow items go into the player's inventory directly. A `giveStarterShulker = true` config flag controls whether the whole bundle is also wrapped in a shulker or just placed loose.

### 4.3 The Snail

#### Spawn
On `ServerPlayConnectionEvents.JOIN`, `SnailManager.spawnIfFirstEver(server, player)`:
- Checks `<world>/immortalsnail/spawned.dat`.
- If absent:
  - Pick `r ∈ [minDistance, maxDistance]` uniformly (config; default 5,000–50,000).
  - Pick `θ ∈ [0, 2π)` uniformly.
  - Compute `(x, z) = (r·cosθ, r·sinθ)`.
  - Find highest non-air block; retry up to 8× with radius expansion if none.
  - If still none, place at `y = level.getSeaLevel()`.
  - Spawn `SnailEntity` at `(x, y+1, z)` facing the player.
  - Persist `{ spawned: true, snailUuid, snailPos }`.
  - Force-load the chunk and a 1-chunk ring (8 chunks total; config knob).

#### Movement
`SnailTickHandler` is a `ServerTickEvents.END` listener:
- Skip if `!SnailManager.hasSnail()`.
- Skip if `level.players().isEmpty()` → snail freezes (locked decision).
- Find nearest player by squared distance: `O(n)` over online players, fine for typical servers.
- Compute unit direction vector `d = (targetPos - snailPos).normalize()` (skip if length < 1e-6).
- Movement per tick: `v = speedBlocksPerMinute / (60 * 20)` blocks.
- Use an accumulator pattern: `accumulator += v; while (accumulator >= 1.0) { stepOneBlock(d); accumulator -= 1.0; }`. For sub-1.0 accumulators (default case — 0.25 bpm gives v ≈ 2.083e-4), the snail only moves once every ~4800 ticks (~4 minutes).
- For sub-block precision between steps, use `teleportTo(snailPos + d * accumulator)` and reset accumulator to 0 when consumed.
- When `accumulator` exceeds a threshold for the first time in many ticks, we "snap" by 1 block in `d`. This is a single `teleportTo` call, not a `setPos`, so vanilla client interpolation handles the visual smoothly.

#### Block-breaking (state machine)

The snail has two modes: **TRAVELING** and **BREAKING**. Default mode is TRAVELING.

**TRAVELING mode:**
- Compute target direction `d` from current snail position to nearest online non-op player (cheapest: nearest in XZ-plane, then if Y diff > 2 blocks, also a vertical component — but Y diff is ignored at default 0.25 bpm; the snail just slowly climbs/falls into the player's altitude).
- Movement per tick: `v = speedBlocksPerMinute / (60 * 20) ≈ 2.083e-4` blocks at the default 0.25 bpm.
- Accumulator pattern: `accumulator += v`. When `accumulator >= 1.0`, attempt a 1-block step in `d` (sub-block drift handled via `teleportTo` between steps).
- **Before each step**, do a proximity check for an obstructing block in the step direction at the snail's eye height:
  - `blockInFront = level.getBlockState(snailPos + d * 0.5)` — i.e. 0.5 blocks ahead of the snail center.
  - The snail is considered "in proximity" of the front block iff `distance(snailCenter, blockCenter) <= 0.1` blocks. At the default 0.25-block hitbox and 1-block step size, this is true whenever the front block is in the next 1×1×1 cell (since the snail's near edge will be within 0.125 blocks of the block edge). It will be **false** mid-step when the snail is more than ~0.6 blocks from the next solid block — that's the intentional gap between "approaching" and "touching."
  - This check is also what prevents the snail from attempting to break a block two cells away through a thin wall — it must be physically adjacent.
- If `level.getBlockState(blockInFront).isSolid()` AND the proximity check passes (snail is within 0.1 blocks of that block):
  - If `canBreakBlocks = false` → phase through (the block is unaffected server-side; see §9 caveat).
  - Else → switch to **BREAKING** mode. Persist `{ blockPos, ticksBroken: 0 }`.
- Movement accumulator does NOT advance while BREAKING (no lost progress — `accumulator` is preserved).

**BREAKING mode:**
- Every tick: `ticksBroken++`.
- Break time: `breakTicks = ceil(20 / breakSpeedBlocksPerSecond)` = `ceil(20 / (1/60))` = **1200 ticks = 60 seconds** at default.
- During BREAKING: snail position is held fixed; `setDeltaMovement(0,0,0)`; emit `ParticleTypes` ("block dust" of the target block's material) once per 10 ticks for client feedback.
- Each tick, also re-verify the proximity check (someone could theoretically place/break the block from a distance and invalidate the target — if the block is gone or no longer solid, return to TRAVELING).
- When `ticksBroken >= breakTicks`:
  - `level.destroyBlock(blockPos, false)` (no item drops — it's a snail, not a player).
  - Play `level.levelEvent(2001, blockPos, Block.getId(state))` (the vanilla block-break sound+particles).
  - Reset BREAKING state, return to TRAVELING.

**Block-break whitelist/blacklist semantics (locked):**
- If `canBreakBlocks = false` → the snail never enters BREAKING mode. Block above applies.
- If `canBreakBlocks = true` AND `breakBlocksWhitelist` is non-empty → only blocks whose id appears in the whitelist are breakable. Others cause the snail to freeze indefinitely (this is the cage strategy).
- If `canBreakBlocks = true` AND `breakBlocksWhitelist` is empty → ALL blocks are breakable EXCEPT those in `breakBlocksBlacklist`. The blacklist prevents the snail from breaking unbreakable-seeming material (bedrock, obsidian, end portal frames) and from breaking into admin territory (barrier, light, command blocks).

**Defaults (locked):**
- `canBreakBlocks = true`
- `breakSpeedBlocksPerSecond = 0.01666666...` (one block per minute)
- `breakBlocksWhitelist = []` (allow all)
- `breakBlocksBlacklist = ["minecraft:bedrock", "minecraft:obsidian", "minecraft:end_portal_frame"]`

#### Chunk loading
- `SnailManager.tick(level)` recomputes the chunk coordinates every tick.
- When the snail crosses a chunk boundary: release old forced chunks, force new ones.
- On `ServerLifecycleEvents.SERVER_STOPPING` and `LEVEL_UNLOAD`: release all.
- Forced chunks use the vanilla API `ServerLevel.setChunkForced(int, int, boolean)`. No external chunkloader dependency required.

#### Death/invulnerability
- `SnailEntity` overrides `hurt(...)` to always return false (cannot be damaged).
- `SnailEntity.setNoAi(true)`, `setSilent(true)`, `setCustomName("The Snail")`, `setCustomNameVisible(true)`.
- Custom spawn egg item registered for admin respawn / debugging only — not part of the bargain shulker.

#### Model & hitbox (locked)

The snail is a 4×4×4 "minecraft pixel" entity. Minecraft pixels are 1/16 of a block, so the bounding box is **0.25 × 0.25 × 0.25 blocks**. Hitbox equals the visual model.

**Why this size matters:** At 0.25 bpm movement and a 0.25-block hitbox, the snail is essentially "inside" any adjacent block at all times. This is what makes the "must be within 0.1 blocks to break a block in its way" proximity check trivially true whenever an obstruction exists.

**Model implementation: Java-defined multi-cube `HierarchicalModel`.** Important correction: vanilla entity models in 1.21.x are **defined in Java**, not in JSON files. The block/item JSON model format does not apply to living entities. The model will be a `SnailModel extends HierarchicalModel<SnailEntity>` whose `createBodyLayer()` returns a `LayerDefinition` built from 10 cube `BoxDefinition`s (each defined in pixel units — 1 unit = 1/16 block). The four texture PNGs under `assets/immortalsnail/textures/entity/` are loaded by `EntityRendererProvider.Context` and passed to `MobRenderer(snailEntity, snailModel, 0.5f)`. This is the standard vanilla pattern used by every small mob (slime, magma cube, bat, silverfish).

**Active cubes in the JSON (10 total):**

| # | Cube name | Origin (x, y, z) | Size (w×h×d) | Texture | Role |
|---|---|---|---|---|---|
| 1 | body_bottom    | (-2, 0, -2) | 4×2×4 | body_side | Lower body / foot |
| 2 | body_top       | (-2, 2, -2) | 4×1×4 | body_top  | Upper body band (where the shell sits) |
| 3 | shell_base     | (-1, 3, -1) | 2×1×2 | shell_side | Flat 2×2 plate on top of body_top |
| 4 | shell_spiral_back_r | (1, 3, 1)  | 1×1×1 | shell_top | Back-right spiral step (creates the 3D shell silhouette) |
| 5 | shell_spiral_back_l | (-1, 3, 1) | 1×1×1 | shell_top | Back-left spiral step |
| 6 | shell_spiral_front_l | (-1, 3, -1) | 1×1×1 | shell_top | Front-left spiral step (mostly hidden by antennae) |
| 7 | eye_l          | (1, 2, -1) | 1×1×1 | eye | Left eye dot (front-right face of body_top) |
| 8 | eye_r          | (1, 2, 0)  | 1×1×1 | eye | Right eye dot (front-right face, one pixel +Z of eye_l) |
| 9 | antenna_l      | (1, 3, -1) | 1×1×1 | antenna | Left antenna stalk (above eye_l) |
| 10 | antenna_r     | (1, 3, 0)  | 1×1×1 | antenna | Right antenna stalk (above eye_r) |

The "spiral" effect on the shell comes from the 2×1×2 base plate + the three 1×1×1 cubes around its perimeter (the back three corners; the front-right corner is replaced by the antennae). Visually: a flat top with raised back corners and a stepped front-right that doubles as the snail's "neck."

**Coordinate convention:** vanilla entity models anchor the model at the bottom-center, +Y up, X and Z centered at 0. The 4×4×4 envelope maps to x∈[-2, +2], y∈[0, 4], z∈[-2, +2]. The front of the snail (eyes and antennae) is the +X face.

> **Note on the envelope:** all cubes sit inside the 4×4×4-pixel envelope (x∈[0,4], y∈[0,4], z∈[0,4]). The "stepped" shell effect is achieved by using two separate cubes at the same y level (shell_base 2×1×2 plate + three 1×1×1 cubes around its edge) — visually a 2×2 spiral that reads as 3D detail without exceeding the bounding envelope. Total silhouette: a 4×4×4 cube that visually decomposes into a flat foot, a slightly-darker body band, a stepped pyramid shell, and four tiny front-face details (2 eyes + 2 antennae).

Total visual silhouette: a 4×4×4-pixel envelope with a recognizable snail shape — flat bottom, spiral stepped shell on top, two eye dots and two antennae at the front. The shell is built from a stepped pyramid rather than a single 2×1×2 cube, giving it visual detail without exceeding the 4×4×4 envelope.

**Texture files (each 32×32 PNG, loaded individually by the Java renderer):**
- `body_side.png` — tan body, soft shading.
- `body_top.png` — slightly darker brown, the rim where the shell sits.
- `shell_side.png` — muted yellow-brown vertical-stripe pattern (the shell's "ridges" when viewed from the side).
- `shell_top.png` — spiral coil pattern viewed from above.
- Eye and antenna reuse `body_side` (kept simple).

**Render registration:** `EntityRendererProvider` in a `client` package registers a `MobRenderer(snailEntity, snailModelLayer, 0.5f)` with the above model. **Render registration is the only piece of the mod that's environment=client-only** — it lives under `src/client/java/...` with a `ClientModInitializer` entrypoint, while everything else stays server-only.

**Animation:** None. The model is static. The snail's "movement" at 0.25 bpm would be visually imperceptible anyway; the only animation we need is the BREAKING-mode dust particles, which are server-side. If you want a tiny "wobble" while traveling, we can add a `setYRot` jitter driven by the position accumulator at trivial cost — say so later and I'll add it.

#### Offline behavior
Locked decision: when no players are online, the snail freezes in place. On the next player join it resumes pursuit.

### 4.4 Death → ban

`SnailDeathBan` listens on `ServerLivingEntityEvents.DAMAGE` finalization (or simply on `LivingDeathEvent`):
- If `entity instanceof ServerPlayer player && damageSource.causingEntity instanceof SnailEntity` (or `directEntity`):
  - Cancel the death event (optional — we still want to ban, but cancellation makes the ban less confusing on screen).
  - Add `player.getGameProfile()` to `server.getPlayerList().getBans()` with no expiry and the config's `banMessage`.
  - `player.connection.disconnect(Component.literal(banMessage))`.
  - Log to console with the player's name, position, and the snail's last-known position.

The ban uses vanilla's ban list which is shared across all worlds on the server. If your server uses a third-party ban plugin (LiteBans, etc.) you'll want to either disable it or convert bans from vanilla's ban list into your plugin's storage.

### 4.5 Admin commands

`SnailCommands` registered with `CommandRegistrationCallback.EVENT.register(...)` for perm level 2:

| Subcommand | Effect |
|---|---|
| `/snail locate` | Prints dim, coords, distance to nearest online player, ETA at current speed. Clickable `[x, y, z]` for the executor. |
| `/snail status` | Speed, distance from world origin `(0, 0)`, age (ticks alive), current target player, ETA. |
| `/snail respawn <here\|x y z>` | Kills existing snail entity, despawns, and spawns a new one at the given coords (or executor's feet for `here`). Re-persists, re-force-chunks. |
| `/snail reload` | Re-reads `config/immortalsnail-common.toml` via Cloth Config. |

Output goes only to the executor.

## 5. Config reference (`config/immortalsnail-common.toml`)

```toml
[snail]
minDistance = 5000
maxDistance = 50000
speedBlocksPerMinute = 0.25
canBreakBlocks = true                                       # default ON (player wants to cage the snail)
breakSpeedBlocksPerSecond = 0.01667                         # 1 block per 60 seconds
breakBlocksWhitelist = []                                   # empty = can break anything (subject to blacklist); populate to restrict
breakBlocksBlacklist = ["minecraft:bedrock", "minecraft:obsidian", "minecraft:end_portal_frame"]
breakProximityThreshold = 0.1                               # snail must be within this many blocks to count as "touching" the target block
chunkForceRadius = 1                                        # 1 = 3x3 chunks forced around the snail

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

Reloadable live via `/snail reload`. Restart needed only if you change `bargainShulkerCount` or `bargainShulkerSize` (those affect GUI sizing).

## 6. Persistence

Stored in `<worldDir>/immortalsnail/`:

- `spawned.dat` — `{ spawned: true, snailUuid, dim, x, y, z }` (JSON)
- `bargains/<uuid>.dat` — two `ItemStack[27]` arrays (NBT, written via `CompoundTag`)
- `config/immortalsnail-common.toml` — global settings (handled by Cloth Config)

`SnailManager` registers a `ServerLifecycleEvents.SERVER_STOPPING` listener to flush state to disk.

## 7. Item blacklist (locked: conservative)

Hardcoded (with config override allowed):
```
command_block, chain_command_block, repeating_command_block,
command_block_minecart,
structure_block, jigsaw,
barrier, light,
spawner
```

Rationale: these are items with no legitimate "normal gameplay" use. Players can still pick any other vanilla item, including spawn eggs of mobs that normally spawn, banners, books, fireworks, etc.

## 8. Operator exemption (locked)

Operators (`permission level >= 2`) are completely exempt:
- Never tracked in `BargainState` (the mod is invisible to them).
- Damage events are never intercepted for them.
- The snail never targets them (closest-player search skips ops).
- Death by snail does not ban them.
- They can still run all `/snail` admin commands.

This makes hosting the server painless — an op can stand next to a player being chased and observe without participating.

## 9. Known limitations & caveats

- **Offline players are ignored** — the snail simply stops until the nearest non-op online player exists. Locked decision.
- **The snail entity doesn't pathfind.** With the new freeze-while-breaking state machine, a "cage" is any solid block not in the break list placed directly in front of the snail. The snail will attempt to break it forever (or until the player switches the block). This is intentional gameplay.
- **No-players-online + freeze-while-breaking interaction:** if the last online player disconnects while the snail is mid-break, the snail remains in BREAKING mode (held in place by the freeze-while-breaking rule, which is dominant). It will resume TRAVELING toward the player on their next login. There's no edge case here — both rules effectively "stop the snail in place."
- **The snail is 0.25×0.25×0.25 blocks.** This is smaller than any vanilla mob. It will be visually easy to miss. The custom nameplate "The Snail" displayed above it should compensate; we're also adding a subtle ambient particle (small `entity_effect` sparkle) every few seconds to draw the eye.
- **Break-state preservation:** the movement accumulator is preserved across BREAKING phases, so the snail doesn't "lose progress" from being forced to break a block. It resumes moving at the exact step it left off at.
- **Block drops suppressed.** When the snail breaks a block, `level.destroyBlock(pos, false)` is called so the broken block drops no item. This is the natural choice (a snail doesn't carry a pickaxe) but means the snail can never "farm" players' traps for resources. If you want it to drop blocks normally, flip the second arg to `true`.
- **Vanilla ban list is global to the server.** If you use LiteBans or similar, disable it or convert bans.
- **No clientside mod needed** for *gameplay* — but a tiny client-side renderer registration is required for the snail to appear correctly. This is shipped in a `src/client/...` folder with a `ClientModInitializer` entrypoint, so vanilla clients see the model automatically as long as the server has the mod. (Technically the renderer only loads on the client side; on a vanilla client without the mod, the snail appears as a missing-model error cube. We're fine with that since this is intended for a hosted server where the op's client also has the mod.)
- **Mace enchantments on 1.21.11:** `Density V, Breach IV, Wind Burst III` are the max-level enchantments on the 1.21.11 Mace. If Mojang has changed the cap, we'll adjust at build time.
- **JEI integration depth:** We'll use JEI's `Internal` package APIs to open its item list programmatically. If JEI's API changes between versions, the fallback picker (`ItemPickerScreen`) is always available.
- **No cheat-mode bypassing:** A player who is `gamemode = creative` is still treated as a normal player by the mod (i.e. they take the immortality / snail treatment) UNLESS they're also an operator. We do not check `gamemode` for anything.

## 10. Testing plan

Local test server with two accounts: Alice (op), Bob (not op).

1. `gradle build` → drop jar into `mods/`. Start server.
2. Confirm `/snail status` → "no snail yet" (pre-first-join).
3. Bob joins → bargain GUI appears → submit → `BargainState.markCompleted(Bob)`.
4. `/snail respawn here` → snail at Alice's feet.
5. `/snail locate` → correct coords.
6. With `speedBlocksPerMinute = 1200`, walk Bob into the snail → Bob is killed and banned with `"The snail caught you."`
7. Confirm Bob can't reconnect; Alice can still `/snail status`.
8. Restart server → confirm snail state restored, bargains persisted.

## 11. Build sequence

When we start writing code (you said: not yet), the order will be:

1. Scaffold (Gradle, `fabric.mod.json`, entrypoints, mixin registration).
2. Config + Cloth Config wiring.
3. Player first-join → starter gear → bargain state (no GUI yet, just persistence).
4. Immortality events (damage cancellation, hunger cancel, suffocation cancel).
5. Custom SnailEntity + registration.
6. Server-tick snail movement + chunkloading.
7. World's-first-join snail auto-spawn.
8. Death → ban.
9. Admin commands.
10. Bargain GUI + JEI-backed item picker.
11. Polish: README updates, Javadoc, packaging.

## 12. What I will *not* include unless asked

- Any web/Telemetry/Discord integration.
- Any player economy integration (Vault etc.).
- Any cross-server mechanics.
- Any client-side UI customizations (this is a server mod).
- "Difficulty sliders" beyond what's in the config.
