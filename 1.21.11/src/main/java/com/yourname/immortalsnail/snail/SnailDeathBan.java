package com.yourname.immortalsnail.snail;

import com.yourname.immortalsnail.ImmortalSnail;
import com.yourname.immortalsnail.config.SnailConfig;
import com.yourname.immortalsnail.entity.SnailEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.BannedPlayerEntry;

import java.util.Date;

/**
 * Handles permanent ban when a player dies to the snail.
 */
public final class SnailDeathBan {

    private SnailDeathBan() {}

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return true;

            // Only ban if killed by snail
            if (!isSnailKill(source)) return true;

            if (!SnailConfig.get().death.banOnSnailKill) return true;

            String msg = SnailConfig.get().death.banMessage;
            ImmortalSnail.LOGGER.info("Player {} was caught by the snail. Banning.", player.getName().getString());

            // Add to vanilla ban list (no expiry = permanent)
            // BannedPlayerEntry(PlayerConfigEntry, Date created, String source, Date expires, String reason)
            var entry = new BannedPlayerEntry(
                    player.getPlayerConfigEntry(),
                    new Date(),               // created
                    "The Snail",              // source
                    null,                     // expires (null = permanent)
                    msg                       // reason
            );
            player.getEntityWorld().getServer().getPlayerManager().getUserBanList().add(entry);

            // Disconnect with our custom message
            player.networkHandler.disconnect(Text.literal(msg));

            return false;  // cancel death (we already disconnected)
        });
    }

    private static boolean isSnailKill(net.minecraft.entity.damage.DamageSource source) {
        return source.getSource() instanceof SnailEntity;
    }
}
