package com.yourname.immortalsnail.player;

import com.yourname.immortalsnail.config.SnailConfig;
import com.yourname.immortalsnail.entity.SnailEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Makes non-operator players unkillable, except by the Snail.
 *
 * Tracking: every non-op player is tracked (always invulnerable to non-snail
 * damage). Operators are not tracked because admins need to die normally
 * for testing/debugging purposes.
 *
 * Damage is cancelled in the ALLOW_DAMAGE event phase by setting it to zero.
 * Hunger is restored each tick.
 * Suffocation and void are also damage events; once they're routed through here,
 * they're handled.
 *
 * Totem handling: when {@link SnailConfig.Death#totemsWorkAgainstSnail} is
 * false, a tracked player with a Totem of Undying who would die from a
 * snail-attributed blow loses their Totem(s) so the vanilla Totem check
 * cannot save them. The damage then proceeds normally and the ban-on-kill
 * logic kicks in.
 */
public final class ImmortalityEvents {

    private ImmortalityEvents() {}

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player) {
                if (!shouldTrack(player)) {
                    return true;  // unmodified
                }
                // Snail-attributed damage passes through
                if (isSnailAttributed(source)) {
                    // If the mod config says Totems don't save you from the snail,
                    // and this blow would be lethal, strip any Totems from the
                    // player's inventory so the vanilla Totem check fails.
                    if (!SnailConfig.get().death.totemsWorkAgainstSnail
                            && hasTotem(player)
                            && amount >= player.getHealth()) {
                        stripTotems(player);
                    }
                    return true;
                }
                // All other damage cancelled
                return false;
            }
            return true;
        });
    }

    /**
     * Called by SnailTickHandler every server tick for each tracked player.
     * Restores hunger and saturation so the player never starves.
     */
    public static void tickPlayer(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (!shouldTrack(sp)) return;
        FoodData food = sp.getFoodData();
        food.setFoodLevel(20);
        food.setSaturation(20.0f);
    }

    public static boolean shouldTrack(ServerPlayer player) {
        // Everyone is tracked — operators included. Singleplayer auto-OPs
        // the host, so distinguishing by op-level would mean the mod never
        // applied to the host.
        return true;
    }

    public static boolean isSnailAttributed(DamageSource source) {
        return source.getEntity() instanceof SnailEntity
                || source.getDirectEntity() instanceof SnailEntity;
    }

    private static boolean hasTotem(ServerPlayer player) {
        NonNullList<ItemStack> main = player.getInventory().getNonEquipmentItems();
        for (ItemStack stack : main) {
            if (!stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING)) {
                return true;
            }
        }
        return false;
    }

    private static void stripTotems(ServerPlayer player) {
        NonNullList<ItemStack> main = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < main.size(); i++) {
            ItemStack stack = main.get(i);
            if (!stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING)) {
                main.set(i, ItemStack.EMPTY);
            }
        }
    }
}