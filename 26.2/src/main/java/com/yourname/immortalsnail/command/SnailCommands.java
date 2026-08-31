package com.yourname.immortalsnail.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.yourname.immortalsnail.config.SnailConfig;
import com.yourname.immortalsnail.entity.SnailEntity;
import com.yourname.immortalsnail.snail.SnailManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Admin commands for the Immortal Snail mod. Operators only (level 2+).
 *
 * /snail status    — show snail state
 * /snail locate    — show coords + ETA
 * /snail respawn <here|nearby|pos> — respawn at pos (supports ~ relative coords), caller's feet, or 5 blocks ahead
 * /snail reload    — re-read config
 */
public final class SnailCommands {

    private SnailCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerSnailCommand(dispatcher);
        });
    }

    private static void registerSnailCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("snail")
                        // Operators only (level 2+).
                        .requires(src -> {
                            var entity = src.getEntity();
                            if (!(entity instanceof ServerPlayer p)) return false;
                            var server = p.level().getServer();
                            if (server == null) return false;

                            // Operators always have access.
                            var op = server.getPlayerList().getOps().get(new net.minecraft.server.players.NameAndId(p.getGameProfile()));
                            if (op != null) {
                                if (Commands.LEVEL_GAMEMASTERS.check(op.permissions())) return true;
                            }

                            // Otherwise, check the config allowlist. Matches by
                            // username (case-insensitive) or by UUID string.
                            var allowlist = SnailConfig.get().commandAllowedPlayers;
                            if (allowlist == null || allowlist.isEmpty()) return false;

                            String playerName = p.getName().getString().toLowerCase();
                            String playerUuid = p.getGameProfile().id().toString();
                            for (String entry : allowlist) {
                                if (entry == null) continue;
                                String trimmed = entry.trim();
                                if (trimmed.isEmpty()) continue;
                                if (trimmed.equalsIgnoreCase(playerName)) return true;
                                if (trimmed.equalsIgnoreCase(playerUuid)) return true;
                            }
                            return false;
                        })
                        .then(Commands.literal("status")
                                .executes(SnailCommands::runStatus))
                        .then(Commands.literal("locate")
                                .executes(SnailCommands::runLocate))
                        .then(Commands.literal("reload")
                                .executes(SnailCommands::runReload))
                        .then(Commands.literal("remove")
                                .executes(SnailCommands::runRemove))
                        .then(Commands.literal("respawn")
                                .then(Commands.literal("here")
                                        .executes(SnailCommands::runRespawnHere))
                                .then(Commands.literal("nearby")
                                        .executes(SnailCommands::runRespawnNearby))
                                .then(Commands.literal("random")
                                        .executes(SnailCommands::runRespawnRandom))
                                .then(Commands.argument("location", BlockPosArgument.blockPos())
                                        .executes(SnailCommands::runRespawnAt)))
        );
    }

    private static int runStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        SnailManager mgr = SnailManager.get();
        if (!mgr.isSpawned()) {
            src.sendSuccess(() -> Component.literal("No snail has been spawned yet."), false);
            return 0;
        }
        BlockPos pos = mgr.getSnailPos();
        if (pos == null) {
            src.sendSuccess(() -> Component.literal("Snail state is corrupt (no position)."), false);
            return 0;
        }
        double dx = pos.getX();
        double dz = pos.getZ();
        double distFromOrigin = Math.sqrt(dx * dx + dz * dz);
        SnailConfig.Snail cfg = SnailConfig.get().snail;
        String targetName = nearestPlayerName(src.getLevel(), pos);

        src.sendSuccess(() -> Component.literal(String.format(
                "Snail: pos=(%d, %d, %d), dist from origin=%.1f, speed=%.4f bpm, target=%s, canBreakBlocks=%s",
                pos.getX(), pos.getY(), pos.getZ(), distFromOrigin, cfg.speedBlocksPerMinute,
                targetName, cfg.canBreakBlocks)), false);
        return 1;
    }

    private static int runLocate(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        SnailManager mgr = SnailManager.get();
        if (!mgr.isSpawned()) {
            src.sendSuccess(() -> Component.literal("No snail has been spawned yet."), false);
            return 0;
        }
        BlockPos pos = mgr.getSnailPos();
        if (pos == null) return 0;
        String targetName = nearestPlayerName(src.getLevel(), pos);
        ServerPlayer target = SnailEntity.findClosestPlayer(src.getLevel(), Vec3.atCenterOf(pos));
        double targetDist = target != null ? target.position().distanceTo(Vec3.atCenterOf(pos)) : -1;
        double bpm = SnailConfig.get().snail.speedBlocksPerMinute;
        double eta = (targetDist > 0 && bpm > 0) ? targetDist / bpm : -1;
        String etaStr = eta > 0 ? String.format("%.2f minutes", eta) : "n/a";

        src.sendSuccess(() -> Component.literal(String.format(
                "Snail at %s — targeting %s (%.1f blocks away, ETA %s)",
                pos.toShortString(), targetName, targetDist, etaStr)), false);
        return 1;
    }

    private static int runReload(CommandContext<CommandSourceStack> ctx) {
        SnailConfig.get().reload();
        ctx.getSource().sendSuccess(() -> Component.literal("Reloaded config."), true);
        return 1;
    }

    private static int runRemove(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        SnailManager.get().remove(src.getLevel());
        src.sendSuccess(() -> Component.literal("Snail removed."), true);
        return 1;
    }

    private static int runRespawnHere(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayer p)) {
            src.sendFailure(Component.literal("/snail respawn here must be run by a player."));
            return 0;
        }
        BlockPos pos = p.blockPosition();
        try {
            SnailManager.get().respawn(src.getLevel(), pos);
            src.sendSuccess(() -> Component.literal("Respawned snail at " + pos.toShortString()), true);
            return 1;
        } catch (Exception e) {
            com.yourname.immortalsnail.ImmortalSnail.LOGGER.error("/snail respawn here failed", e);
            src.sendFailure(Component.literal("Respawn failed: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            return 0;
        }
    }

    /**
     * /snail respawn nearby — same as "here", but 5 blocks in the cardinal
     * direction the caller is facing. Handy for placing the snail right in
     * front of you without it spawning inside your hitbox.
     */
    private static int runRespawnNearby(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayer p)) {
            src.sendFailure(Component.literal("/snail respawn nearby must be run by a player."));
            return 0;
        }
        Direction facing = p.getDirection();
        BlockPos pos = p.blockPosition().relative(facing, 5);
        try {
            SnailManager.get().respawn(src.getLevel(), pos);
            src.sendSuccess(() -> Component.literal("Respawned snail 5 blocks in front of you ("
                    + facing.getSerializedName() + ") at " + pos.toShortString()), true);
            return 1;
        } catch (Exception e) {
            com.yourname.immortalsnail.ImmortalSnail.LOGGER.error("/snail respawn nearby failed", e);
            src.sendFailure(Component.literal("Respawn failed: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            return 0;
        }
    }

    private static int runRespawnRandom(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            BlockPos pos = SnailManager.pickRandomSpawnPos(src.getLevel());
            SnailManager.get().respawn(src.getLevel(), pos);
            src.sendSuccess(() -> Component.literal("Re-rolled snail spawn to " + pos.toShortString()), true);
            return 1;
        } catch (Exception e) {
            com.yourname.immortalsnail.ImmortalSnail.LOGGER.error("/snail respawn random failed", e);
            src.sendFailure(Component.literal("Respawn failed: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            return 0;
        }
    }

    private static int runRespawnAt(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "location");
        try {
            SnailManager.get().respawn(src.getLevel(), pos);
            src.sendSuccess(() -> Component.literal("Respawned snail at " + pos.toShortString()), true);
            return 1;
        } catch (Exception e) {
            com.yourname.immortalsnail.ImmortalSnail.LOGGER.error("/snail respawn at failed", e);
            src.sendFailure(Component.literal("Respawn failed: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            return 0;
        }
    }

    private static String nearestPlayerName(ServerLevel level, BlockPos snailPos) {
        ServerPlayer p = SnailEntity.findClosestPlayer(level, Vec3.atCenterOf(snailPos));
        return p == null ? "(none)" : p.getName().getString();
    }
}