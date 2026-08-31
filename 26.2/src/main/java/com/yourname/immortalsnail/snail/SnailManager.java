package com.yourname.immortalsnail.snail;

import com.yourname.immortalsnail.ImmortalSnail;
import com.yourname.immortalsnail.config.SnailConfig;
import com.yourname.immortalsnail.entity.ModEntities;
import com.yourname.immortalsnail.entity.SnailEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

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
 */
public final class SnailManager {

    private static final SnailManager INSTANCE = new SnailManager();
    public static SnailManager get() { return INSTANCE; }

    private boolean spawned = false;
    private UUID snailUuid = null;
    private BlockPos snailPos = null;
    private ServerLevel lastLevel = null;
    private final Set<ChunkPos> forcedChunks = new HashSet<>();
    private int diagTicks = 0;
    private int respawnCooldown = 0;
    private int dedupeTicks = 0;

    private SnailManager() {}

    public boolean isSpawned() { return spawned; }
    public UUID getSnailUuid() { return snailUuid; }
    public BlockPos getSnailPos() { return snailPos; }

    public SnailEntity getSnail(ServerLevel level) {
        if (snailUuid == null) return null;
        Entity e = level.getEntity(snailUuid);
        return e instanceof SnailEntity s ? s : null;
    }

    public void load(MinecraftServer server) {
        Path file = getStateFile(server);
        if (!Files.exists(file)) return;
        try {
            byte[] data = Files.readAllBytes(file);
            CompoundTag tag;
            try (var bais = new java.io.ByteArrayInputStream(data);
                 var dis = new java.io.DataInputStream(bais)) {
                tag = NbtIo.read(dis);
            }
            if (tag == null) return;
            spawned = tag.getBooleanOr("spawned", false);
            UUID uuid = null;
            String uuidStr = tag.getStringOr("snailUuid", "");
            if (!uuidStr.isEmpty()) {
                try { uuid = UUID.fromString(uuidStr); } catch (Exception ignored) {}
            }
            if (uuid != null) {
                snailUuid = uuid;
            }
            if (tag.contains("x") && tag.contains("y") && tag.contains("z")) {
                snailPos = new BlockPos(tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0));
            }
            ImmortalSnail.LOGGER.info("Loaded snail state: spawned={}, pos={}", spawned, snailPos);
        } catch (IOException e) {
            ImmortalSnail.LOGGER.error("Failed to load snail state", e);
        }
    }

    public void save(MinecraftServer server) {
        Path file = getStateFile(server);
        CompoundTag tag = new CompoundTag();
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
                NbtIo.write(tag, dos);
                data = baos.toByteArray();
            }
            Files.write(file, data);
        } catch (IOException e) {
            ImmortalSnail.LOGGER.error("Failed to save snail state", e);
        }
    }

    private Path getStateFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("immortalsnail")
                .resolve("spawned.dat");
    }

    public void tick(MinecraftServer server) {
        if (++dedupeTicks % 40 == 0) dedupeSnails(server);
        if (!spawned || snailUuid == null) return;
        if (lastLevel == null) {
            lastLevel = server.getLevel(Level.OVERWORLD);
            if (lastLevel == null) return;
        }
        SnailEntity snail = getSnail(lastLevel);
        if (snail == null) {
            if (snailPos != null) {
                forceChunksAround(lastLevel, snailPos);
                if (respawnCooldown > 0) {
                    respawnCooldown--;
                } else if (!isChunkLoaded(lastLevel, snailPos)) {
                    respawnCooldown = 20;
                } else {
                    List<SnailEntity> loaded = findLoadedSnails(server);
                    if (loaded.isEmpty()) {
                        spawnEntityAt(lastLevel, snailPos);
                    } else {
                        SnailEntity found = loaded.get(0);
                        snailUuid = found.getUUID();
                        snailPos = found.blockPosition();
                        if (found.level() instanceof ServerLevel sw) lastLevel = sw;
                        ImmortalSnail.LOGGER.info(
                                "Snail already exists at {}; adopting it instead of respawning a duplicate",
                                found.blockPosition());
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
        BlockPos pos = snail.blockPosition();
        if (!pos.equals(snailPos)) {
            snailPos = pos;
            forceChunksAround(lastLevel, pos);
        }
        if (++diagTicks % 6000 == 0) {
            ImmortalSnail.LOGGER.info("Snail diag: entity loaded at block={}, exact={}", pos, snail.position());
        }
    }

    private static boolean isChunkLoaded(ServerLevel level, BlockPos pos) {
        return level.getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                ChunkStatus.FULL, false) != null;
    }

    private static List<SnailEntity> findLoadedSnails(MinecraftServer server) {
        List<SnailEntity> snails = new ArrayList<>();
        if (server == null) return snails;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof SnailEntity s) snails.add(s);
            }
        }
        return snails;
    }

    private void dedupeSnails(MinecraftServer server) {
        List<SnailEntity> loaded = findLoadedSnails(server);
        if (loaded.isEmpty()) return;
        if (!spawned || snailUuid == null) {
            for (SnailEntity s : loaded) {
                ImmortalSnail.LOGGER.warn(
                        "Discarding stray snail {} at {} (snail state is empty)",
                        s.getUUID(), s.blockPosition());
                s.discard();
            }
            return;
        }
        SnailEntity keep = null;
        for (SnailEntity s : loaded) {
            if (s.getUUID().equals(snailUuid)) {
                keep = s;
                break;
            }
        }
        if (keep == null) {
            keep = loaded.get(0);
            snailUuid = keep.getUUID();
            snailPos = keep.blockPosition();
            if (keep.level() instanceof ServerLevel sw) lastLevel = sw;
            ImmortalSnail.LOGGER.warn("Adopted stray snail {} at {} as the tracked snail",
                    keep.getUUID(), keep.blockPosition());
        }
        for (SnailEntity s : loaded) {
            if (s != keep) {
                ImmortalSnail.LOGGER.warn("Discarding duplicate snail {} at {}",
                        s.getUUID(), s.blockPosition());
                s.discard();
            }
        }
    }

    public void spawnIfFirstEver(ServerLevel level, ServerPlayer triggerPlayer) {
        if (spawned) return;
        List<SnailEntity> loaded = findLoadedSnails(level.getServer());
        if (!loaded.isEmpty()) {
            SnailEntity found = loaded.get(0);
            spawned = true;
            snailUuid = found.getUUID();
            snailPos = found.blockPosition();
            if (found.level() instanceof ServerLevel sw) lastLevel = sw;
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

    public void respawn(ServerLevel level, BlockPos pos) {
        for (SnailEntity s : findLoadedSnails(level.getServer())) {
            s.discard();
        }
        releaseAllChunks(level);
        spawned = true;
        spawnEntityAt(level, pos);
        forceChunksAround(level, pos);
    }

    public void respawnRandom(ServerLevel level) {
        BlockPos spawnPos = pickRandomSpawnPos(level);
        respawn(level, spawnPos);
        ImmortalSnail.LOGGER.info("Re-rolled snail spawn to {}", spawnPos);
    }

    public static BlockPos pickRandomSpawnPos(ServerLevel level) {
        SnailConfig.Snail cfg = SnailConfig.get().snail;
        int minR = cfg.minDistance;
        int maxR = cfg.maxDistance;
        if (minR < 0) minR = 0;
        if (maxR < minR) maxR = minR;

        double r = minR + Math.random() * (maxR - minR);
        double theta = Math.random() * Math.PI * 2;
        int x = (int) Math.round(Math.cos(theta) * r);
        int z = (int) Math.round(Math.sin(theta) * r);

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(x, level.getMaxY() - 1, z);
        BlockPos groundPos = null;
        for (int y = level.getMaxY() - 1; y >= level.getMinY(); y--) {
            mutable.setY(y);
            BlockState s = level.getBlockState(mutable);
            if (!s.isAir() && !s.liquid()) {
                groundPos = mutable.immutable();
                break;
            }
        }
        if (groundPos != null) return groundPos.above();
        return new BlockPos(x, level.getSeaLevel(), z);
    }

    public void remove(ServerLevel level) {
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

    private void spawnEntityAt(ServerLevel level, BlockPos pos) {
        SnailEntity snail = (SnailEntity) ModEntities.SNAIL.create(level, EntitySpawnReason.COMMAND);
        if (snail == null) return;
        snail.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        snail.setPersistenceRequired();
        level.addFreshEntity(snail);
        snailUuid = snail.getUUID();
        snailPos = pos;
        lastLevel = level;
    }

    private void forceChunksAround(ServerLevel level, BlockPos center) {
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
        for (ChunkPos old : forcedChunks) {
            if (!newForced.contains(old)) {
                level.setChunkForced(old.x(), old.z(), false);
            }
        }
        for (ChunkPos np : newForced) {
            level.setChunkForced(np.x(), np.z(), true);
        }
        forcedChunks.clear();
        forcedChunks.addAll(newForced);
    }

    private void releaseAllChunks(ServerLevel level) {
        for (ChunkPos cp : forcedChunks) {
            level.setChunkForced(cp.x(), cp.z(), false);
        }
        forcedChunks.clear();
    }

    public void onServerStopping(MinecraftServer server) {
        if (lastLevel != null) {
            releaseAllChunks(lastLevel);
        }
    }
}