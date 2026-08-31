package com.yourname.immortalsnail.snail;

import com.yourname.immortalsnail.ImmortalSnail;
import com.yourname.immortalsnail.config.SnailConfig;
import com.yourname.immortalsnail.entity.SnailEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.damagesource.DamageSource;

import java.util.Date;

/**
 * Handles permanent ban when a player dies to the snail.
 */
public final class SnailDeathBan {

    private SnailDeathBan() {}

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player)) return true;

            // Only ban if killed by snail
            if (!isSnailKill(source)) return true;

            if (!SnailConfig.get().death.banOnSnailKill) return true;

            String msg = SnailConfig.get().death.banMessage;
            ImmortalSnail.LOGGER.info("Player {} was caught by the snail. Banning.", player.getName().getString());

            // Add to vanilla ban list (no expiry = permanent)
            // UserBanListEntry(NameAndId, Date created, String source, Date expires, String reason)
            var entry = new UserBanListEntry(
                    new net.minecraft.server.players.NameAndId(player.getGameProfile()),
                    new Date(),               // created
                    "The Snail",              // source
                    null,                     // expires (null = permanent)
                    msg                       // reason
            );
            player.level().getServer().getPlayerList().getBans().add(entry);

            // Disconnect with our custom message
            player.connection.disconnect(Component.literal(msg));

            return false;  // cancel death (we already disconnected)
        });
    }

    private static boolean isSnailKill(DamageSource source) {
        return source.getEntity() instanceof SnailEntity;
    }
}