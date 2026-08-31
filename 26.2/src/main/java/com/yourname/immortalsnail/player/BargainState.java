package com.yourname.immortalsnail.player;

import com.yourname.immortalsnail.ImmortalSnail;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player persistent state for the bargain.
 *
 * Storage layout: <worldDir>/immortalsnail/bargains/<uuid>.dat
 * Format: NBT compound with two 27-slot ItemStack lists named "shulker_a" and "shulker_b"
 *
 * For convenience, in-memory cached state is kept in a ConcurrentHashMap.
 * On server start, files are loaded lazily on first access.
 */
public final class BargainState {

    private static final ConcurrentHashMap<UUID, boolean[]> COMPLETED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, ItemStack[][]> CONTENTS = new ConcurrentHashMap<>();

    private BargainState() {}

    public static boolean hasCompletedBargain(UUID playerId) {
        return COMPLETED.getOrDefault(playerId, new boolean[]{false})[0];
    }

    public static void markCompleted(UUID playerId) {
        COMPLETED.computeIfAbsent(playerId, k -> new boolean[1])[0] = true;
    }

    public static ItemStack[][] getBargainContents(UUID playerId) {
        return CONTENTS.computeIfAbsent(playerId, k -> new ItemStack[2][27]);
    }

    public static void setBargainContents(UUID playerId, ItemStack[] shulkerA, ItemStack[] shulkerB) {
        ItemStack[][] contents = getBargainContents(playerId);
        contents[0] = shulkerA;
        contents[1] = shulkerB;
    }

    public static Path getFilePath(MinecraftServer server, UUID playerId) {
        Path dir = server.getWorldPath(LevelResource.ROOT)
                .resolve("immortalsnail")
                .resolve("bargains");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            ImmortalSnail.LOGGER.error("Failed to create bargain state directory", e);
        }
        return dir.resolve(playerId.toString() + ".dat");
    }

    public static void save(MinecraftServer server, UUID playerId) {
        Path file = getFilePath(server, playerId);
        ItemStack[][] contents = getBargainContents(playerId);
        CompoundTag tag = new CompoundTag();
        ListTag listA = new ListTag();
        for (int i = 0; i < 27; i++) {
            CompoundTag slot = new CompoundTag();
            slot.putByte("Slot", (byte) i);
            if (contents[0][i] != null && !contents[0][i].isEmpty()) {
                slot.put("Item", stackToNbt(contents[0][i], server));
            }
            listA.add(slot);
        }
        tag.put("shulker_a", listA);
        ListTag listB = new ListTag();
        for (int i = 0; i < 27; i++) {
            CompoundTag slot = new CompoundTag();
            slot.putByte("Slot", (byte) i);
            if (contents[1][i] != null && !contents[1][i].isEmpty()) {
                slot.put("Item", stackToNbt(contents[1][i], server));
            }
            listB.add(slot);
        }
        tag.put("shulker_b", listB);
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            byte[] data;
            try (var baos = new java.io.ByteArrayOutputStream();
                 var dos = new java.io.DataOutputStream(baos)) {
                NbtIo.write(tag, dos);
                data = baos.toByteArray();
            }
            Files.write(file, data);
        } catch (IOException e) {
            ImmortalSnail.LOGGER.error("Failed to save bargain state for " + playerId, e);
        }
    }

    public static void load(MinecraftServer server, UUID playerId) {
        Path file = getFilePath(server, playerId);
        if (!Files.exists(file)) return;
        try {
            byte[] data = Files.readAllBytes(file);
            CompoundTag tag;
            try (var bais = new java.io.ByteArrayInputStream(data);
                 var dis = new java.io.DataInputStream(bais)) {
                tag = NbtIo.read(dis);
            }
            if (tag == null) return;
            ItemStack[] a = new ItemStack[27];
            ItemStack[] b = new ItemStack[27];
            ListTag listA = tag.getListOrEmpty("shulker_a");
            for (int i = 0; i < listA.size(); i++) {
                CompoundTag slot = listA.getCompoundOrEmpty(i);
                int idx = slot.getByteOr("Slot", (byte) 0);
                if (slot.contains("Item")) {
                    CompoundTag itemNbt = slot.getCompoundOrEmpty("Item");
                    a[idx] = ItemStack.CODEC.parse(
                            server.registryAccess().createSerializationContext(NbtOps.INSTANCE),
                            itemNbt).result().orElse(ItemStack.EMPTY);
                }
            }
            ListTag listB = tag.getListOrEmpty("shulker_b");
            for (int i = 0; i < listB.size(); i++) {
                CompoundTag slot = listB.getCompoundOrEmpty(i);
                int idx = slot.getByteOr("Slot", (byte) 0);
                if (slot.contains("Item")) {
                    CompoundTag itemNbt = slot.getCompoundOrEmpty("Item");
                    b[idx] = ItemStack.CODEC.parse(
                            server.registryAccess().createSerializationContext(NbtOps.INSTANCE),
                            itemNbt).result().orElse(ItemStack.EMPTY);
                }
            }
            setBargainContents(playerId, a, b);
            markCompleted(playerId);
        } catch (IOException e) {
            ImmortalSnail.LOGGER.error("Failed to load bargain state for " + playerId, e);
        }
    }

    /**
     * Convert an ItemStack to a Tag using the registry manager's NbtOps.
     */
    private static Tag stackToNbt(ItemStack stack, MinecraftServer server) {
        return (Tag) ItemStack.CODEC.encodeStart(
                server.registryAccess().createSerializationContext(NbtOps.INSTANCE),
                stack
        ).getOrThrow();
    }
}