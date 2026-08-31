package com.yourname.immortalsnail;

import com.yourname.immortalsnail.config.SnailConfig;
import com.yourname.immortalsnail.entity.ModEntities;
import com.yourname.immortalsnail.player.BargainState;
import com.yourname.immortalsnail.player.ImmortalityEvents;
import com.yourname.immortalsnail.player.PlayerFirstJoinHandler;
import com.yourname.immortalsnail.snail.SnailDeathBan;
import com.yourname.immortalsnail.snail.SnailManager;
import com.yourname.immortalsnail.snail.SnailTickHandler;
import com.yourname.immortalsnail.command.SnailCommands;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImmortalSnail implements DedicatedServerModInitializer {
    public static final String MOD_ID = "immortalsnail";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Items that cannot be put into the bargain shulkers. */
    public static final java.util.List<String> BLACKLIST = java.util.List.of(
            "minecraft:command_block",
            "minecraft:chain_command_block",
            "minecraft:repeating_command_block",
            "minecraft:command_block_minecart",
            "minecraft:structure_block",
            "minecraft:structure_void",
            "minecraft:jigsaw",
            "minecraft:barrier",
            "minecraft:light",
            "minecraft:spawner"
    );

    @Override
    public void onInitializeServer() {
        LOGGER.info("Initializing Immortal Snail mod");

        // Load config
        SnailConfig.get().load();

        // Register entities (snail entity type)
        ModEntities.register();

        // Register screen handler types
        com.yourname.immortalsnail.player.ModMenus.register();

        // Register network payloads
        PayloadTypeRegistry.playC2S().register(BargainOpenPayload.ID, BargainOpenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BargainSubmitPayload.ID, BargainSubmitPayload.CODEC);

        // Register bargain submit handler
        ServerPlayNetworking.registerGlobalReceiver(BargainSubmitPayload.ID, (payload, context) -> {
            var player = context.player();
            var contents = payload.contents();
            int nonEmpty = 0;
            for (ItemStack s : contents) if (!s.isEmpty()) nonEmpty++;
            LOGGER.info("Player {} sent bargain with {} non-empty items out of {}",
                    player.getName().getString(), nonEmpty, contents.size());
            if (contents.size() != 54) {
                LOGGER.warn("Player {} sent bargain with {} items, expected 54",
                        player.getName().getString(), contents.size());
                return;
            }
            ItemStack[] a = new ItemStack[27];
            ItemStack[] b = new ItemStack[27];
            for (int i = 0; i < 27; i++) {
                a[i] = contents.get(i);
                b[i] = contents.get(i + 27);
            }
            player.getEntityWorld().getServer().execute(() -> {
                PlayerFirstJoinHandler.onBargainSubmitted(player.getEntityWorld().getServer(), player, a, b);
            });
        });

        // Register event handlers
        ImmortalityEvents.register();
        PlayerFirstJoinHandler.register();
        SnailTickHandler.register();
        SnailDeathBan.register();

        // Trigger first-ever-player-join snail spawn
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.getPlayer();
            // Spawn the snail on whoever joins first — operators included,
            // so the host's singleplayer world also gets a snail on launch.
            net.minecraft.server.world.ServerWorld ow = server.getWorld(net.minecraft.world.World.OVERWORLD);
            if (ow != null && !SnailManager.get().isSpawned()) {
                SnailManager.get().spawnIfFirstEver(ow, player);
            }
        });

        // Server lifecycle: load/save snail state
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SnailManager.get().load(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SnailManager.get().onServerStopping(server);
            SnailManager.get().save(server);
        });

        // Admin commands
        SnailCommands.register();

        LOGGER.info("Immortal Snail mod initialized");
    }
}
