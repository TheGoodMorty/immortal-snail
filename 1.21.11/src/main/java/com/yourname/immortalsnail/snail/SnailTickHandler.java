package com.yourname.immortalsnail.snail;

import com.yourname.immortalsnail.config.SnailConfig;
import com.yourname.immortalsnail.entity.SnailEntity;
import com.yourname.immortalsnail.player.ImmortalityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;

/**
 * Server tick handler for the Snail's BREAKING state machine.
 *
 * Movement is handled by the snail's own tick() method (manual setVelocity
 * toward nearest non-op player). This handler only:
 *  1. Detects collision with the player (deal lethal damage)
 *  2. Detects proximity to a breakable block (enter BREAKING mode)
 *  3. Ticks the BREAKING state machine (block break animation)
 */
public final class SnailTickHandler {

    private SnailTickHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SnailTickHandler::onTick);
    }

    private static void onTick(MinecraftServer server) {
        // Keep the snail's chunk force-loaded and re-spawn it if it unloaded.
        SnailManager.get().tick(server);
        SnailEntity snail = null;
        ServerWorld snailLevel = null;
        for (ServerWorld level : server.getWorlds()) {
            snail = SnailManager.get().getSnail(level);
            if (snail != null) {
                snailLevel = level;
                break;
            }
        }
        if (snail != null && snailLevel != null) {
            tickSnail(snailLevel, snail);
        }
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ImmortalityEvents.tickPlayer(p);
        }
    }

    private static void tickSnail(ServerWorld level, SnailEntity snail) {
        SnailConfig.Snail cfg = SnailConfig.get().snail;
        ServerPlayerEntity target = snail.findNearestPlayer(level);

        // Collision check: lethal damage on touch
        if (target != null && snail.getBoundingBox().expand(0.25).intersects(target.getBoundingBox())) {
            float lethal = 1000000.0f;
            target.damage(level, level.getDamageSources().mobAttack(snail), lethal);
            return;
        }

        if (snail.getMode() == SnailMode.BREAKING) {
            tickBreaking(level, snail, cfg);
            return;
        }

        // Block breaking is now driven by a stuck timer in the entity itself
        // (SnailEntity.runStuckBreakCheck). The tick handler used to scan
        // for nearby blocks every tick, but that scan triggered on every
        // random block near the snail and locked it in place. Block breaking
        // now only starts after the snail has been genuinely stuck for a
        // configurable duration, and the entity decides whether to break
        // while preserving its cling state.
    }

    private static void tickBreaking(ServerWorld level, SnailEntity snail, SnailConfig.Snail cfg) {
        BlockPos target = snail.getCurrentBreakTarget();
        if (target == null) {
            snail.setMode(SnailMode.TRAVELING);
            return;
        }
        BlockState state = level.getBlockState(target);
        if (state.isAir() || !state.isSolid() || !snail.canBreakBlock(state)) {
            snail.setMode(SnailMode.TRAVELING);
            snail.setCurrentBreakTarget(null);
            snail.setBreakTicks(0);
            return;
        }

        int ticks = snail.getBreakTicks() + 1;
        snail.setBreakTicks(ticks);

        if (ticks % 10 == 0) {
            net.minecraft.particle.BlockStateParticleEffect blockDust =
                    new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, state);
            var packet = new ParticleS2CPacket(
                    blockDust, false, false,
                    target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                    0.3f, 0.3f, 0.3f, 0.05f, 8
            );
            for (ServerPlayerEntity p : level.getPlayers()) {
                p.networkHandler.sendPacket(packet);
            }
        }

        int requiredTicks = (int) Math.ceil(20.0 / cfg.breakSpeedBlocksPerSecond);
        if (ticks >= requiredTicks) {
            level.removeBlock(target, false);
            level.syncWorldEvent(null, 2001, target, net.minecraft.block.Block.getRawIdFromState(state));
            snail.setMode(SnailMode.TRAVELING);
            snail.setCurrentBreakTarget(null);
            snail.setBreakTicks(0);
        }
    }
}