package com.yourname.immortalsnail.player;

import com.yourname.immortalsnail.ImmortalSnail;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the per-player first-join bargain trigger.
 *
 * On first ever join (no bargain state on disk):
 *  - Load any existing bargain state from disk
 *  - If still no bargain: deliver starter gear, queue the bargain GUI to open after a short delay
 *  - Mark player as "pending bargain" so we don't re-trigger on re-login
 *
 * The bargain GUI is opened via a 10-tick delay to let the client finish loading.
 */
public final class PlayerFirstJoinHandler {

    /** Players who are pending their bargain (have joined but haven't submitted) */
    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    private PlayerFirstJoinHandler() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            UUID id = player.getGameProfile().id();

            // Try loading existing bargain state
            BargainState.load(server, id);

            if (BargainState.hasCompletedBargain(id)) {
                ImmortalSnail.LOGGER.info("Player {} already completed the bargain; skipping trigger", player.getName().getString());
                return;
            }

            ImmortalSnail.LOGGER.info("First-join for player {}; delivering starter gear and bargaining GUI",
                    player.getName().getString());
            PENDING.put(id, 10);  // delay before opening GUI

            // Deliver starter gear immediately
            StarterGear.giveStarter(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getGameProfile().id();
            BargainState.save(server, id);
            PENDING.remove(id);
        });

        // Open GUI after delay
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Snapshot entries (to avoid ConcurrentModificationException)
            java.util.List<UUID> toOpen = new java.util.ArrayList<>();
            java.util.Iterator<java.util.Map.Entry<UUID, Integer>> it = PENDING.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<UUID, Integer> entry = it.next();
                int ticks = entry.getValue();
                if (ticks <= 1) {
                    toOpen.add(entry.getKey());
                    it.remove();
                } else {
                    entry.setValue(ticks - 1);
                }
            }
            // Open the GUI outside of the iteration
            for (UUID id : toOpen) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null && player.connection.isAcceptingMessages()) {
                    try {
                        player.openMenu(new com.yourname.immortalsnail.player.BargainScreenHandler.Factory());
                    } catch (Exception e) {
                        com.yourname.immortalsnail.ImmortalSnail.LOGGER.error("Failed to open bargain GUI for {}", player.getName().getString(), e);
                    }
                }
            }
        });
    }

    /**
     * Called by the bargain-submit payload handler when the player confirms.
     */
    public static void onBargainSubmitted(MinecraftServer server, ServerPlayer player, ItemStack[] shulkerA, ItemStack[] shulkerB) {
        UUID id = player.getGameProfile().id();
        BargainState.setBargainContents(id, shulkerA, shulkerB);
        BargainState.markCompleted(id);
        BargainState.save(server, id);
        PENDING.remove(id);

        // Give the player the two filled shulkers as items
        ItemStack boxA = fillShulkerBox(shulkerA, player);
        ItemStack boxB = fillShulkerBox(shulkerB, player);
        boolean gaveA = player.getInventory().add(boxA);
        boolean gaveB = player.getInventory().add(boxB);
        ImmortalSnail.LOGGER.info("Gave shulker A: {} (count={}), shulker B: {} (count={})",
                gaveA, boxA.getCount(), gaveB, boxB.getCount());
        if (!gaveA || !gaveB) {
            // Inventory full — drop at player's feet instead
            if (!gaveA) player.drop(boxA, false);
            if (!gaveB) player.drop(boxB, false);
        }
    }

    private static ItemStack fillShulkerBox(ItemStack[] contents, ServerPlayer player) {
        ItemStack box = new ItemStack(Items.SHULKER_BOX);

        // In 1.21+, shulker box contents are stored under the
        // minecraft:container data component as a List<ItemStack>.
        // We need to build a container with up to 27 slots.
        java.util.List<ItemStack> containerItems = new java.util.ArrayList<>(27);
        for (int i = 0; i < 27; i++) containerItems.add(ItemStack.EMPTY);
        for (int i = 0; i < contents.length && i < 27; i++) {
            ItemStack s = contents[i];
            if (s != null && !s.isEmpty()) {
                containerItems.set(i, s.copy());
            }
        }

        box.set(DataComponents.CONTAINER,
                ItemContainerContents.fromItems(containerItems));

        return box;
    }
}