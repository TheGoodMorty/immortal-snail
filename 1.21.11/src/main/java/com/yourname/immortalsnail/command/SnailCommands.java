package com.yourname.immortalsnail.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.yourname.immortalsnail.config.SnailConfig;
import com.yourname.immortalsnail.entity.SnailEntity;
import com.yourname.immortalsnail.snail.SnailManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;

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

    private static void registerSnailCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("snail")
                        // Operators only (level 2+).
                        // Yarn 1.21.11 changed the permission system — we use a simple op check
                        // via server ops list rather than the new PermissionPredicate system.
                        .requires(src -> {
                            var entity = src.getEntity();
                            if (!(entity instanceof ServerPlayerEntity p)) return false;
                            var server = p.getEntityWorld().getServer();
                            if (server == null) return false;

                            // Operators always have access.
                            var op = server.getPlayerManager().getOpList().get(p.getPlayerConfigEntry());
                            if (op != null) {
                                var level = op.getLevel().getLevel();
                                if (level.compareTo(PermissionLevel.GAMEMASTERS) >= 0) return true;
                            }

                            // Otherwise, check the config allowlist. Matches by
                            // username (case-insensitive) or by UUID string.
                            var allowlist = SnailConfig.get().commandAllowedPlayers;
                            if (allowlist == null || allowlist.isEmpty()) return false;

                            String playerName = p.getName().getString().toLowerCase();
                            String playerUuid = p.getPlayerConfigEntry().id().toString();
                            for (String entry : allowlist) {
                                if (entry == null) continue;
                                String trimmed = entry.trim();
                                if (trimmed.isEmpty()) continue;
                                if (trimmed.equalsIgnoreCase(playerName)) return true;
                                if (trimmed.equalsIgnoreCase(playerUuid)) return true;
                            }
                            return false;
                        })
                        .then(CommandManager.literal("status")
                                .executes(SnailCommands::runStatus))
                        .then(CommandManager.literal("locate")
                                .executes(SnailCommands::runLocate))
                        .then(CommandManager.literal("reload")
                                .executes(SnailCommands::runReload))
                        .then(CommandManager.literal("remove")
                                .executes(SnailCommands::runRemove))
                        .then(CommandManager.literal("respawn")
                                .then(CommandManager.literal("here")
                                        .executes(SnailCommands::runRespawnHere))
                                .then(CommandManager.literal("nearby")
                                        .executes(SnailCommands::runRespawnNearby))
                                .then(CommandManager.literal("random")
                                        .executes(SnailCommands::runRespawnRandom))
                                .then(CommandManager.argument("location", BlockPosArgumentType.blockPos())
                                        .executes(SnailCommands::runRespawnAt)))
        );
    }

    private static int runStatus(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        SnailManager mgr = SnailManager.get();
        if (!mgr.isSpawned()) {
            src.sendFeedback(() -> Text.literal("No snail has been spawned yet."), false);
            return 0;
        }
        BlockPos pos = mgr.getSnailPos();
        if (pos == null) {
            src.sendFeedback(() -> Text.literal("Snail state is corrupt (no position)."), false);
            return 0;
        }
        double dx = pos.getX();
        double dz = pos.getZ();
        double distFromOrigin = Math.sqrt(dx * dx + dz * dz);
        SnailConfig.Snail cfg = SnailConfig.get().snail;
        String targetName = nearestPlayerName(src.getWorld(), pos);

        src.sendFeedback(() -> Text.literal(String.format(
                "Snail: pos=(%d, %d, %d), dist from origin=%.1f, speed=%.4f bpm, target=%s, canBreakBlocks=%s",
                pos.getX(), pos.getY(), pos.getZ(), distFromOrigin, cfg.speedBlocksPerMinute,
                targetName, cfg.canBreakBlocks)), false);
        return 1;
    }

    private static int runLocate(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        SnailManager mgr = SnailManager.get();
        if (!mgr.isSpawned()) {
            src.sendFeedback(() -> Text.literal("No snail has been spawned yet."), false);
            return 0;
        }
        BlockPos pos = mgr.getSnailPos();
        if (pos == null) return 0;
        String targetName = nearestPlayerName(src.getWorld(), pos);
        ServerPlayerEntity target = SnailEntity.findClosestPlayer(src.getWorld(), Vec3d.ofCenter(pos));
        double targetDist = target != null ? target.getEntityPos().distanceTo(Vec3d.ofCenter(pos)) : -1;
        double bpm = SnailConfig.get().snail.speedBlocksPerMinute;
        double eta = (targetDist > 0 && bpm > 0) ? targetDist / bpm : -1;
        String etaStr = eta > 0 ? String.format("%.2f minutes", eta) : "n/a";

        src.sendFeedback(() -> Text.literal(String.format(
                "Snail at %s — targeting %s (%.1f blocks away, ETA %s)",
                pos.toShortString(), targetName, targetDist, etaStr)), false);
        return 1;
    }

    private static int runReload(CommandContext<ServerCommandSource> ctx) {
        SnailConfig.get().reload();
        ctx.getSource().sendFeedback(() -> Text.literal("Reloaded config."), true);
        return 1;
    }

    private static int runRemove(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        SnailManager.get().remove(src.getWorld());
        src.sendFeedback(() -> Text.literal("Snail removed."), true);
        return 1;
    }

    private static int runRespawnHere(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayerEntity p)) {
            src.sendError(Text.literal("/snail respawn here must be run by a player."));
            return 0;
        }
        BlockPos pos = p.getBlockPos();
        try {
            SnailManager.get().respawn(src.getWorld(), pos);
            src.sendFeedback(() -> Text.literal("Respawned snail at " + pos.toShortString()), true);
            return 1;
        } catch (Exception e) {
            com.yourname.immortalsnail.ImmortalSnail.LOGGER.error("/snail respawn here failed", e);
            src.sendError(Text.literal("Respawn failed: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            return 0;
        }
    }

    /**
     * /snail respawn nearby — same as "here", but 5 blocks in the cardinal
     * direction the caller is facing. Handy for placing the snail right in
     * front of you without it spawning inside your hitbox.
     */
    private static int runRespawnNearby(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayerEntity p)) {
            src.sendError(Text.literal("/snail respawn nearby must be run by a player."));
            return 0;
        }
        Direction facing = p.getHorizontalFacing();
        BlockPos pos = p.getBlockPos().offset(facing, 5);
        try {
            SnailManager.get().respawn(src.getWorld(), pos);
            src.sendFeedback(() -> Text.literal("Respawned snail 5 blocks in front of you ("
                    + facing.asString() + ") at " + pos.toShortString()), true);
            return 1;
        } catch (Exception e) {
            com.yourname.immortalsnail.ImmortalSnail.LOGGER.error("/snail respawn nearby failed", e);
            src.sendError(Text.literal("Respawn failed: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            return 0;
        }
    }

    private static int runRespawnRandom(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        try {
            BlockPos pos = SnailManager.pickRandomSpawnPos(src.getWorld());
            SnailManager.get().respawn(src.getWorld(), pos);
            src.sendFeedback(() -> Text.literal("Re-rolled snail spawn to " + pos.toShortString()), true);
            return 1;
        } catch (Exception e) {
            com.yourname.immortalsnail.ImmortalSnail.LOGGER.error("/snail respawn random failed", e);
            src.sendError(Text.literal("Respawn failed: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            return 0;
        }
    }

    private static int runRespawnAt(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        BlockPos pos = BlockPosArgumentType.getBlockPos(ctx, "location");
        try {
            SnailManager.get().respawn(src.getWorld(), pos);
            src.sendFeedback(() -> Text.literal("Respawned snail at " + pos.toShortString()), true);
            return 1;
        } catch (Exception e) {
            com.yourname.immortalsnail.ImmortalSnail.LOGGER.error("/snail respawn at failed", e);
            src.sendError(Text.literal("Respawn failed: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            return 0;
        }
    }

    private static String nearestPlayerName(ServerWorld level, BlockPos snailPos) {
        ServerPlayerEntity p = SnailEntity.findClosestPlayer(level, Vec3d.ofCenter(snailPos));
        return p == null ? "(none)" : p.getName().getString();
    }
}
