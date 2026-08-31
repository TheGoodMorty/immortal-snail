package com.yourname.immortalsnail.snail;

import com.yourname.immortalsnail.ImmortalSnail;
import com.yourname.immortalsnail.config.SnailConfig;
import com.yourname.immortalsnail.entity.ModEntities;
import com.yourname.immortalsnail.entity.SnailEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Central manager for the Snail.
 *
 * State:
 *  - spawned: whether the snail has been spawned in this world
 *  - snailUuid: UUID of the snail entity (for despawn/respawn)
 *  - snailPos: last known position (for chunk forcing on load)
 *  - forcedChunks: chunks currently forced
 *
 * Behavior:
 *  - load/save on server lifecycle
 *  - spawnIfFirstEver: picks random spawn within config range and creates the entity
 *  - respawn: kills existing snail, spawns new at given position
 *  - tick: re-runs chunk forcing if the snail moves
 */
public final class SnailManager {

    private static final SnailManager INSTANCE = new SnailManager();
    public static SnailManager get() { return INSTANCE; }

    private boolean spawned = false;
    private UUID snailUuid = null;
    private BlockPos snailPos = null;
    private ServerWorld lastLevel = null;
    private final Set<ChunkPos> forcedChunks = new HashSet<>();
    private int diagTicks = 0;
    private int respawnCooldown = 0;
    private int dedupeTicks = 0;

    private SnailManager() {}

    public boolean isSpawned() { return spawned; }
    public UUID getSnailUuid() { return snailUuid; }
    public BlockPos getSnailPos() { return snailPos; }

    public SnailEntity getSnail(ServerWorld level) {
        if (snailUuid == null) return null;
        Entity e = level.getEntity(snailUuid);
        return e instanceof SnailEntity s ? s : null;
    }

    public void load(MinecraftServer server) {
        Path file = getStateFile(server);
        if (!Files.exists(file)) return;
        try {
            byte[] data = Files.readAllBytes(file);
            NbtCompound tag;
            try (var bais = new java.io.ByteArrayInputStream(data);
                 var dis = new java.io.DataInputStream(bais)) {
                tag = net.minecraft.nbt.NbtIo.readCompound(dis);
            }
            if (tag == null) return;
            spawned = tag.getBoolean("spawned", false);
            UUID uuid = null;
            var uuidStr = tag.getString("snailUuid");
            if (uuidStr.isPresent() && !uuidStr.get().isEmpty()) {
                try { uuid = UUID.fromString(uuidStr.get()); } catch (Exception ignored) {}
            }
            if (uuid != null) {
                snailUuid = uuid;
            }
            if (tag.contains("x") && tag.contains("y") && tag.contains("z")) {
                snailPos = new BlockPos(tag.getInt("x", 0), tag.getInt("y", 0), tag.getInt("z", 0));
            }
            ImmortalSnail.LOGGER.info("Loaded snail state: spawned={}, pos={}", spawned, snailPos);
        } catch (IOException e) {
            ImmortalSnail.LOGGER.error("Failed to load snail state", e);
        }
    }

    public void save(MinecraftServer server) {
        Path file = getStateFile(server);
        NbtCompound tag = new NbtCompound();
        tag.putBoolean("spawned", spawned);
        if (snailUuid != null) tag.putString("snailUuid", snailUuid.toString());
        if (snailPos != null) {
            tag.putInt("x", snailPos.getX());
            tag.putInt("y", snailPos.getY());
            tag.putInt("z", snailPos.getZ());
        }
        try {
            Files.createDirectories(file.getParent());
            byte[] data;
            try (var baos = new java.io.ByteArrayOutputStream();
                 var dos = new java.io.DataOutputStream(baos)) {
                net.minecraft.nbt.NbtIo.writeCompound(tag, dos);
                data = baos.toByteArray();
            }
            Files.write(file, data);
        } catch (IOException e) {
            ImmortalSnail.LOGGER.error("Failed to save snail state", e);
        }
    }

    private Path getStateFile(MinecraftServer server) {
        return server.getSavePath(net.minecraft.util.WorldSavePath.ROOT)
                .resolve("immortalsnail")
                .resolve("spawned.dat");
    }

    /**
     * Called on every server tick.
     *  - If no snail exists yet, spawn one on the first non-op player join (handled separately)
     *  - If a snail exists, update its position and force-load chunks
     */
    public void tick(MinecraftServer server) {
        // Enforce the "at most one snail exists" invariant (throttled sweep).
        if (++dedupeTicks % 40 == 0) dedupeSnails(server);
        if (!spawned || snailUuid == null) return;
        if (lastLevel == null) {
            // Pick the overworld
            lastLevel = server.getWorld(World.OVERWORLD);
            if (lastLevel == null) return;
        }
        SnailEntity snail = getSnail(lastLevel);
        if (snail == null) {
            // Try respawning at the last-known position. Chunk loading is
            // asynchronous: spawning into a chunk that is not actually
            // loaded yet just queues duplicate entities. Keep the tickets
            // fresh, then spawn only once the chunk is really loaded, and
            // retry at most every few seconds.
            if (snailPos != null) {
                forceChunksAround(lastLevel, snailPos);
                if (respawnCooldown > 0) {
                    respawnCooldown--;
                } else if (!isChunkLoaded(lastLevel, snailPos)) {
                    respawnCooldown = 20;
                } else {
                    // Chunk is loaded - but the entity restored from disk can
                    // lag a few ticks behind chunk FULL status. Never spawn
                    // a second snail: if ANY snail is loaded somewhere, adopt
                    // it instead of respawning.
                    List<SnailEntity> loaded = findLoadedSnails(server);
                    if (loaded.isEmpty()) {
                        spawnEntityAt(lastLevel, snailPos);
                    } else {
                        SnailEntity found = loaded.get(0);
                        snailUuid = found.getUuid();
                        snailPos = found.getBlockPos();
                        if (found.getEntityWorld() instanceof ServerWorld sw) lastLevel = sw;
                        ImmortalSnail.LOGGER.info(
                                "Snail already exists at {}; adopting it instead of respawning a duplicate",
                                found.getBlockPos());
                    }
                    respawnCooldown = 100;
                }
            }
            if (++diagTicks % 6000 == 0) {
                ImmortalSnail.LOGGER.info("Snail diag: entity NOT loaded, recorded pos={}", snailPos);
            }
            return;
        }
        respawnCooldown = 0;
        BlockPos pos = snail.getBlockPos();
        if (!pos.equals(snailPos)) {
            snailPos = pos;
            forceChunksAround(lastLevel, pos);
        }
        if (++diagTicks % 6000 == 0) {
            ImmortalSnail.LOGGER.info("Snail diag: entity loaded at block={}, exact={}", pos, snail.getEntityPos());
        }
    }

    /** True only when the chunk (at FULL status) is really loaded right now. */
    private static boolean isChunkLoaded(ServerWorld level, BlockPos pos) {
        return level.getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                net.minecraft.world.chunk.ChunkStatus.FULL, false) != null;
    }

    /**
     * Find every snail entity currently loaded, across all worlds.
     * Entities sitting in unloaded chunks are NOT visible here - they
     * surface (and get swept) once their chunk loads.
     */
    private static List<SnailEntity> findLoadedSnails(MinecraftServer server) {
        List<SnailEntity> snails = new ArrayList<>();
        if (server == null) return snails;
        for (ServerWorld level : server.getWorlds()) {
            for (Entity e : level.iterateEntities()) {
                if (e instanceof SnailEntity s) snails.add(s);
            }
        }
        return snails;
    }

    /**
     * Enforce the "at most one snail exists" invariant. Duplicates can
     * appear when an entity restored from disk coexists with a
     * manager-spawned one (entity restoration lags chunk loading by a few
     * ticks). This keeps the tracked entity, adopts a stray when the
     * tracked one is missing, and discards the rest.
     */
    private void dedupeSnails(MinecraftServer server) {
        List<SnailEntity> loaded = findLoadedSnails(server);
        if (loaded.isEmpty()) return;
        if (!spawned || snailUuid == null) {
            // State says no snail, yet one is loaded (e.g. leftover entity
            // from a wiped state file) - discard them all.
            for (SnailEntity s : loaded) {
                ImmortalSnail.LOGGER.warn(
                        "Discarding stray snail {} at {} (snail state is empty)",
                        s.getUuid(), s.getBlockPos());
                s.discard();
            }
            return;
        }
        SnailEntity keep = null;
        for (SnailEntity s : loaded) {
            if (s.getUuid().equals(snailUuid)) {
                keep = s;
                break;
            }
        }
        if (keep == null) {
            // Tracked entity is gone but a stray is alive: adopt it rather
            // than letting tick() spawn a second one next to it.
            keep = loaded.get(0);
            snailUuid = keep.getUuid();
            snailPos = keep.getBlockPos();
            if (keep.getEntityWorld() instanceof ServerWorld sw) lastLevel = sw;
            ImmortalSnail.LOGGER.warn("Adopted stray snail {} at {} as the tracked snail",
                    keep.getUuid(), keep.getBlockPos());
        }
        for (SnailEntity s : loaded) {
            if (s != keep) {
                ImmortalSnail.LOGGER.warn("Discarding duplicate snail {} at {}",
                        s.getUuid(), s.getBlockPos());
                s.discard();
            }
        }
    }

    /**
     * Called on first-ever non-op player join to spawn the snail.
     */
    public void spawnIfFirstEver(ServerWorld level, ServerPlayerEntity triggerPlayer) {
        if (spawned) return;
        // If a snail is somehow already loaded (leftover entity from a
        // partially-wiped world), adopt it instead of spawning a second.
        List<SnailEntity> loaded = findLoadedSnails(level.getServer());
        if (!loaded.isEmpty()) {
            SnailEntity found = loaded.get(0);
            spawned = true;
            snailUuid = found.getUuid();
            snailPos = found.getBlockPos();
            if (found.getEntityWorld() instanceof ServerWorld sw) lastLevel = sw;
            forceChunksAround(lastLevel, snailPos);
            ImmortalSnail.LOGGER.info("Adopted existing snail at {} instead of spawning a new one", snailPos);
            return;
        }
        BlockPos spawnPos = pickRandomSpawnPos(level);
        spawned = true;
        spawnEntityAt(level, spawnPos);
        forceChunksAround(level, spawnPos);
        ImmortalSnail.LOGGER.info("Spawned the Snail at {} (triggered by player {})", spawnPos,
                triggerPlayer.getName().getString());
    }

    /**
     * Respawn at given coords. Used by /snail respawn command.
     * If the stored state has no position (corrupt), treat it as a fresh spawn.
     */
    public void respawn(ServerWorld level, BlockPos pos) {
        // Kill EVERY loaded snail, not just the tracked one - a stale uuid
        // can leave the real entity untracked, and any survivor would
        // become a duplicate next to the new spawn.
        for (SnailEntity s : findLoadedSnails(level.getServer())) {
            s.discard();
        }
        // Release old chunks
        releaseAllChunks(level);
        // Spawn new
        spawned = true;
        spawnEntityAt(level, pos);
        forceChunksAround(level, pos);
    }

    /**
     * Pick a random spawn position within the configured distance range
     * and respawn there. Used by /snail respawn random.
     */
    public void respawnRandom(ServerWorld level) {
        BlockPos spawnPos = pickRandomSpawnPos(level);
        respawn(level, spawnPos);
        ImmortalSnail.LOGGER.info("Re-rolled snail spawn to {}", spawnPos);
    }

    /**
     * Compute a random spawn position within the configured distance range.
     * Pure function — doesn't actually spawn anything.
     */
    public static BlockPos pickRandomSpawnPos(ServerWorld level) {
        SnailConfig.Snail cfg = SnailConfig.get().snail;
        int minR = cfg.minDistance;
        int maxR = cfg.maxDistance;
        if (minR < 0) minR = 0;
        if (maxR < minR) maxR = minR;

        double r = minR + Math.random() * (maxR - minR);
        double theta = Math.random() * Math.PI * 2;
        int x = (int) Math.round(Math.cos(theta) * r);
        int z = (int) Math.round(Math.sin(theta) * r);

        // Find highest non-air block
        BlockPos.Mutable mutable = new BlockPos.Mutable(x, World.MAX_Y - 1, z);
        BlockPos groundPos = null;
        for (int y = World.MAX_Y - 1; y >= World.MIN_Y; y--) {
            mutable.setY(y);
            BlockState s = level.getBlockState(mutable);
            if (!s.isAir() && !s.isLiquid()) {
                groundPos = mutable.toImmutable();
                break;
            }
        }
        if (groundPos != null) return groundPos.up();
        return new BlockPos(x, level.getSeaLevel(), z);
    }

    /**
     * Remove the snail entirely (kill the entity and clear state).
     * Used by /snail remove command.
     */
    public void remove(ServerWorld level) {
        if (level != null) {
            for (SnailEntity s : findLoadedSnails(level.getServer())) {
                s.discard();
            }
            releaseAllChunks(level);
        }
        spawned = false;
        snailUuid = null;
        snailPos = null;
        lastLevel = null;
    }

    private void spawnEntityAt(ServerWorld level, BlockPos pos) {
        SnailEntity snail = (SnailEntity) ModEntities.SNAIL.create(level, net.minecraft.entity.SpawnReason.COMMAND);
        if (snail == null) return;
        // Spawn 1 block above the target position so the snail's hitbox
        // doesn't overlap with any solid block. The snail will fall and
        // land on top of the block at `pos`.
        snail.setPosition(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        snail.setPersistent();  // belt-and-braces alongside the checkDespawn() no-op
        level.spawnEntity(snail);
        snailUuid = snail.getUuid();
        snailPos = pos;
        lastLevel = level;
    }

    private void forceChunksAround(ServerWorld level, BlockPos center) {
        if (level == null) return;
        int radius = Math.max(0, SnailConfig.get().snail.chunkForceRadius);
        Set<ChunkPos> newForced = new HashSet<>();
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                newForced.add(new ChunkPos(cx + dx, cz + dz));
            }
        }
        // Release chunks no longer in range
        for (ChunkPos old : forcedChunks) {
            if (!newForced.contains(old)) {
                level.setChunkForced(old.x, old.z, false);
            }
        }
        // Force new chunks
        for (ChunkPos np : newForced) {
            level.setChunkForced(np.x, np.z, true);
        }
        forcedChunks.clear();
        forcedChunks.addAll(newForced);
    }

    private void releaseAllChunks(ServerWorld level) {
        for (ChunkPos cp : forcedChunks) {
            level.setChunkForced(cp.x, cp.z, false);
        }
        forcedChunks.clear();
    }

    public void onServerStopping(MinecraftServer server) {
        if (lastLevel != null) {
            releaseAllChunks(lastLevel);
        }
    }
}
