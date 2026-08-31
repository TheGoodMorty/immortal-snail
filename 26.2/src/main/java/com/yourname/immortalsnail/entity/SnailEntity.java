package com.yourname.immortalsnail.entity;

import com.yourname.immortalsnail.config.SnailConfig;
import com.yourname.immortalsnail.snail.SnailMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The Snail — slow-moving entity that chases the nearest non-op player.
 *
 * Movement is driven manually in tick() via setDeltaMovement() because the
 * vanilla pathfinder doesn't work well with the snail's tiny 0.25×0.25 size.
 *
 * Spider-like wall climbing: when the snail collides with a wall, it
 * sets climbing and slowly moves upward. Falls via gravity.
 */
public class SnailEntity extends Spider {

    private static final EntityDataAccessor<String> DATA_MODE =
            SynchedEntityData.defineId(SnailEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_BREAK_TICKS =
            SynchedEntityData.defineId(SnailEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SURFACE_POSE =
            SynchedEntityData.defineId(SnailEntity.class, EntityDataSerializers.INT);

    /**
     * Surface poses synced to the client so the model can strike the pose
     * matching the surface the snail is on and the direction it is about
     * to travel. The server decides the pose every tick in orientToSurface().
     */
    public static final int POSE_FLOOR = 0;
    public static final int POSE_CEILING = 1;
    public static final int POSE_WALL_UP = 2;
    public static final int POSE_WALL_DOWN = 3;
    public static final int POSE_WALL_SIDE_LEFT = 4;
    public static final int POSE_WALL_SIDE_RIGHT = 5;

    // Manual movement state
    private BlockPos currentBreakTarget = null;
    private double movementAccumulator = 0.0;
    private int blockedSteps = 0;
    private int stuckTicks = 0;
    private double lastDistanceToTarget = -1.0;
    private Vec3 lastTickPos = null;
    private Vec3 lastToTarget = null;
    /**
     * The last projected movement direction (tangent to the current surface).
     * Used by findStuckBlock() so break-probing stays aligned with the snail's
     * actual travel even when the visual yaw is rotated to face a wall.
     */
    private Vec3 lastMoveDir = null;

    /** True while the snail is gripping a wall or ceiling (not the floor). */
    private boolean clingingToWall = false;

    /** Last wall normal, kept for the sticky grip at a ledge lip. */
    private Vec3 lastWallNormal = null;

    public SnailEntity(EntityType<? extends Spider> entityType, Level level) {
        super(entityType, level);
        this.setSilent(true);
        this.setCustomName(Component.literal("The Snail"));
        this.setCustomNameVisible(true);
        this.setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.08)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MODE, SnailMode.TRAVELING.name());
        builder.define(DATA_BREAK_TICKS, 0);
        builder.define(DATA_SURFACE_POSE, POSE_FLOOR);
    }

    public SnailMode getMode() {
        return SnailMode.valueOf(this.entityData.get(DATA_MODE));
    }

    public void setMode(SnailMode mode) {
        this.entityData.set(DATA_MODE, mode.name());
    }

    public int getBreakTicks() {
        return this.entityData.get(DATA_BREAK_TICKS);
    }

    public void setBreakTicks(int ticks) {
        this.entityData.set(DATA_BREAK_TICKS, ticks);
    }

    public int getSurfacePose() {
        return this.entityData.get(DATA_SURFACE_POSE);
    }

    public void setSurfacePose(int pose) {
        this.entityData.set(DATA_SURFACE_POSE, pose);
    }

    public BlockPos getCurrentBreakTarget() {
        return currentBreakTarget;
    }

    public void setCurrentBreakTarget(BlockPos pos) {
        this.currentBreakTarget = pos;
    }

    public double getMovementAccumulator() {
        return movementAccumulator;
    }

    public void setMovementAccumulator(double v) {
        this.movementAccumulator = v;
    }

    @Override
    protected void registerGoals() {
        // Movement is driven manually in tick() (straight-line pursuit
        // projected onto surfaces); these goals are cosmetic only.
        this.goalSelector.addGoal(4, new FloatGoal(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /**
     * Minimum size of a single horizontal step, in blocks.
     *
     * Vanilla LivingEntity zeroes any velocity component below ~0.003 per
     * tick, so a snail moving at 1 block/minute (0.00083 blocks/tick) can
     * never move via setDeltaMovement. Instead we bank the per-tick distance in
     * an accumulator and spend it as one discrete step once it is large
     * enough to survive that clamp.
     */
    private static final double MIN_STEP = 0.05;

    @Override
    public void tick() {
        // Update the cling state before super.tick() so travel() knows
        // whether to hold the snail on a wall/ceiling this tick.
        if (!this.level().isClientSide()) {
            updateCling((ServerLevel) this.level());
        }

        super.tick();

        if (!this.level().isClientSide()) {
            ServerLevel level = (ServerLevel) this.level();

            // Track actual movement for stuck detection: reset the counter
            // whenever the snail genuinely moves (including gravity falls).
            Vec3 pos = this.position();
            if (lastTickPos != null && pos.distanceTo(lastTickPos) > 1.0e-6) {
                stuckTicks = 0;
            }
            lastTickPos = pos;

            stepTowardTarget(level);
            tickStuckDetection(level);
            orientToSurface(level);
        }
    }

    /**
     * Hold the snail on a wall or ceiling between steps. Gravity would
     * otherwise sag it off the surface; the step logic in tick() owns all
     * actual movement, so while clinging the snail simply stays put.
     */
    @Override
    public void travel(Vec3 movementInput) {
        if (!this.level().isClientSide() && this.clingingToWall) {
            this.setDeltaMovement(Vec3.ZERO);
            this.fallDistance = 0.0f;
            return;
        }
        super.travel(movementInput);
    }

    /**
     * The snail's own cling flag drives vanilla's climb physics. While
     * clinging, travel() holds the snail in place, so the vanilla climb
     * lift never actually fires; returning the flag here keeps vanilla
     * from applying its own wall ascent on top of ours.
     */
    @Override
    public boolean isClimbing() {
        return this.clingingToWall;
    }

    /**
     * Decide whether the snail is gripping a wall or ceiling this tick.
     * The grip only holds while the player is not below the snail - a
     * snail that has climbed past its target releases and falls rather
     * than re-gripping the wall on the way down.
     */
    private void updateCling(ServerLevel level) {
        if (this.onGround()) {
            this.clingingToWall = false;
            return;
        }
        ServerPlayer target = findClosestPlayer(level, this.position());
        if (target == null) {
            this.clingingToWall = false;
            return;
        }
        double dy = target.getY() - this.getY();
        if (dy < -0.3) {
            this.clingingToWall = false;  // player below: drop
            return;
        }
        Vec3 wallNormal = findWallNormal(level);
        if (wallNormal != null && dy <= 0.3) {
            // Player at/below the snail's height: release if they are on
            // the air side of the wall (walk off), cling if behind it
            // (crawl sideways around a corner).
            Vec3 toTarget = target.position().subtract(this.position());
            if (toTarget.normalize().dot(wallNormal) > 0.3) {
                this.clingingToWall = false;
                return;
            }
        }
        this.clingingToWall = this.horizontalCollision
                || wallNormal != null
                || touchingCeiling(level);
    }

    /**
     * Straight-line pursuit projected onto the surface the snail is on.
     *
     * The snail always aims directly at the player. On the floor (or in
     * free air) it crawls horizontally; on a wall it crawls along the face
     * (climbing up, or sideways around a corner) while pressing into the
     * wall to keep its grip; on a ceiling it crawls along it and drops off
     * when the player is below. The four surface transitions - cresting a
     * cliff, mounting a wall from the ground, concave/convex wall corners,
     * and ceiling on/off - all fall out of this one projection rule plus
     * the contact detection, with no special cases and no jumping.
     */
    private void stepTowardTarget(ServerLevel level) {
        ServerPlayer target = findClosestPlayer(level, this.position());
        if (target == null) return;

        Vec3 toTarget = target.position().subtract(this.position());
        double dist = toTarget.length();
        if (dist < 0.4) return;  // reached

        Vec3 rawDir = toTarget.scale(1.0 / dist);

        // Bank per-tick distance and spend it as one discrete step once it
        // is large enough to survive vanilla's velocity zero-clamp.
        SnailConfig.Snail cfg = SnailConfig.get().snail;
        double perTick = cfg.speedBlocksPerMinute / 1200.0;
        this.movementAccumulator += perTick;
        if (this.movementAccumulator < MIN_STEP) return;
        double step = this.movementAccumulator;
        this.movementAccumulator = 0.0;

        Vec3 normal = contactNormal(level);
        Vec3 moveDir;

        if (normal == null || normal.y > 0.5) {
            // Floor or free air: crawl horizontally toward the player.
            moveDir = horizontal(rawDir);
        } else if (normal.y < -0.5) {
            // Ceiling: drop off when the player is below, else crawl along it.
            if (rawDir.y < -0.3) {
                this.clingingToWall = false;
                moveDir = rawDir;
            } else {
                Vec3 tangent = projectOntoPlane(rawDir, normal);
                if (tangent.lengthSqr() < 0.1) tangent = horizontal(rawDir);
                else tangent = tangent.normalize();
                moveDir = tangent.add(normal.scale(-0.4)).normalize();
            }
        } else {
            // Wall: climb up or crawl sideways, pressing in to keep the grip.
            double dy = target.getY() - this.getY();
            double dot = rawDir.dot(normal);
            if (dy < -0.3 || (dot > 0.3 && dy <= 0.3)) {
                // Player below, or on the air side of the wall at/below the
                // snail's height: release and move off the wall toward them.
                // (Without the air-side check, a player directly away from
                // the wall projects to zero and the snail wrongly climbs up.)
                this.clingingToWall = false;
                moveDir = horizontal(rawDir);
            } else {
                Vec3 tangent = projectOntoPlane(rawDir, normal);
                if (tangent.lengthSqr() < 0.1) {
                    tangent = new Vec3(0.0, 1.0, 0.0);
                } else {
                    tangent = tangent.normalize();
                }
                moveDir = tangent.add(normal.scale(-0.4)).normalize();
            }
        }

        this.lastMoveDir = moveDir;
        Vec3 before = this.position();
        this.move(MoverType.SELF, moveDir.scale(step));
        if (before.distanceTo(this.position()) < step * 0.5) {
            onBlocked(level, moveDir, step);
        }
    }

    /**
     * A step was blocked. Try a diagonal step-up (climb a small obstacle
     * like a one-block lip); if that also fails, the stuck detection in
     * tickStuckDetection() will eventually break through the block.
     */
    private void onBlocked(ServerLevel level, Vec3 dir, double step) {
        Vec3 lunge = new Vec3(dir.x, 0.4, dir.z).normalize().scale(step);
        this.move(MoverType.SELF, lunge);
    }

    /** The horizontal component of a vector, normalized (or zero). */
    private static Vec3 horizontal(Vec3 v) {
        Vec3 h = new Vec3(v.x, 0.0, v.z);
        return h.lengthSqr() < 1.0e-6 ? Vec3.ZERO : h.normalize();
    }

    /**
     * A snail never hops. Vanilla applies the jump in tickMovement BEFORE
     * travel runs, so a gated travel cannot prevent it: the +0.42 hop
     * velocity moves the box even through a zero-input travel, which
     * clears horizontalCollision and kills the climb lift - the exact
     * hop-fall-repeat loop. The ascent comes from vanilla's climb lift
     * ((horizontalCollision || jumping) && isClimbing -> velocity.y=0.2),
     * which never needs the hop, so jumping is disabled outright.
     */
    @Override
    public void jumpFromGround() {
        // No-op: snails press and climb; they do not hop.
    }

    /**
     * The single choke point for a snail's upward intent. Vanilla mob
     * pathfinding raises the jump flag when the next node is higher, and
     * JumpControl re-asserts it every tick from inside super.tick() — a
     * pre-clear in tick() can never win that race. For a climbing spider
     * this flag IS the climb input: pressed into a wall, vanilla turns it
     * into the 0.2/tick wall ascent instead of a hop. So the rule is
     * simple: the flag only flows while the snail is pressed against
     * something (climb), and is dropped in open air (no hopping).
     */
    @Override
    public void setJumping(boolean jumping) {
        super.setJumping(jumping && this.horizontalCollision);
    }



    /**
     * Increment stuckTicks whenever the snail is on a surface but not
     * making progress. Reset only happens in stepTowardTarget() when a
     * step actually moves the snail meaningfully toward the player. This
     * avoids the bug where micro-jitter from collision response resets
     * the counter every tick and the threshold is never reached.
     *
     * Once stuckTicks crosses the configured threshold, immediately try to
     * break through the block the snail is pressing against. This runs in
     * the per-tick path so we don't have to wait for the next step attempt
     * (which at 1 bpm only fires once per minute).
     */
    private void tickStuckDetection(ServerLevel level) {
        // Free mid-air with no contact and no progress is fine — just
        // falling. Don't count it as stuck.
        if (!this.onGround() && !this.horizontalCollision) {
            return;
        }

        // In BREAKING mode, the snail is intentionally standing still.
        if (this.getMode() == SnailMode.BREAKING) {
            return;
        }

        // On a surface but not making forward progress — increment.
        stuckTicks++;
        // Log every 600 ticks (30s) so we can confirm the counter is
        // actually accumulating without spamming every tick.
        if (stuckTicks > 0 && stuckTicks % 600 == 0) {
            com.yourname.immortalsnail.ImmortalSnail.LOGGER.info(
                    "[Snail] stuckTicks={} (threshold={})",
                    stuckTicks, SnailConfig.get().snail.stuckBreakAfterTicks);
        }

        // Threshold reached: try to break through whatever is blocking us.
        // Runs here so we don't depend on the next step firing to check it.
        SnailConfig.Snail snailCfg = SnailConfig.get().snail;
        if (stuckTicks < snailCfg.stuckBreakAfterTicks) return;
        if (!snailCfg.canBreakBlocks) return;

        BlockPos target = findStuckBlock(level);
        if (target == null) return;
        BlockState state = level.getBlockState(target);
        if (state.isAir() || !state.isSolid()) return;
        if (!this.canBreakBlock(state)) {
            // Log once so we can see why a stuck snail isn't breaking.
            if (stuckTicks == snailCfg.stuckBreakAfterTicks) {
                com.yourname.immortalsnail.ImmortalSnail.LOGGER.info(
                        "[Snail] stuck-but-not-breakable at {} ({})",
                        target, state.getBlock());
            }
            return;
        }

        // Per user spec: replace the block with air after the threshold.
        // Cling state is preserved so the snail stays where it is and
        // resumes climbing the moment the obstruction is gone.
        level.removeBlock(target, false);
        level.levelEvent(null, 2001, target, Block.getId(state));
        com.yourname.immortalsnail.ImmortalSnail.LOGGER.info(
                "[Snail] broke through {} after {} stuck ticks",
                target, stuckTicks);
        stuckTicks = 0;
    }

    /**
     * Find the block the snail is currently touching, preferring the one
     * directly ahead in the direction it's looking. Falls back to any
     * touching block if no clear "ahead" candidate exists.
     */
    private BlockPos findStuckBlock(ServerLevel level) {
        // Use the last projected movement direction (tangent to the surface).
        // This stays aligned with the snail's actual travel even when the
        // visual yaw has been rotated to face a wall. Fall back to yaw if no
        // movement direction has been recorded yet.
        Vec3 facingDir;
        if (lastMoveDir != null) {
            facingDir = new Vec3(lastMoveDir.x, 0.0, lastMoveDir.z);
        } else {
            float yaw = this.getYRot();
            facingDir = new Vec3(-Math.sin(Math.toRadians(yaw)), 0.0, Math.cos(Math.toRadians(yaw)));
        }
        if (facingDir.length() < 1e-6) return null;

        // Probe at three heights: foot, body, head. Return the first solid
        // block we find directly ahead in any of those columns.
        double bodyY = this.getY() + this.getBbHeight() * 0.5;
        for (double y : new double[]{this.getY() + 0.1, bodyY, this.getY() + this.getBbHeight() + 0.05}) {
            BlockPos ahead = BlockPos.containing(new Vec3(
                    this.getX() + facingDir.x * 0.4,
                    y,
                    this.getZ() + facingDir.z * 0.4));
            BlockState s = level.getBlockState(ahead);
            if (!s.isAir() && s.isSolid()) return ahead;
        }

        // Fallback: any solid block adjacent to the snail's hitbox.
        AABB box = this.getBoundingBox().inflate(0.05);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos probe = BlockPos.containing(new Vec3(
                            this.getX() + dx * 0.5,
                            this.getY() + dy * 0.5,
                            this.getZ() + dz * 0.5));
                    if (box.intersects(new AABB(probe.getX(), probe.getY(), probe.getZ(),
                            probe.getX() + 1, probe.getY() + 1, probe.getZ() + 1))) {
                        BlockState s = level.getBlockState(probe);
                        if (!s.isAir() && s.isSolid()) return probe;
                    }
                }
            }
        }

        return null;
    }

    /** True when a solid block sits directly ahead, at foot or body height. */
    private boolean wallAhead(ServerLevel level, Vec3 dir) {
        double probe = 0.35;
        double px = this.getX() + dir.x * probe;
        double pz = this.getZ() + dir.z * probe;
        BlockPos low = BlockPos.containing(px, this.getY() + 0.1, pz);
        BlockPos high = BlockPos.containing(px, this.getY() + this.getBbHeight() * 0.9, pz);
        return level.getBlockState(low).isSolid() || level.getBlockState(high).isSolid();
    }

    /**
     * Returns the outward unit normal of whichever surface the snail is
     * touching: the floor beneath it, the wall against its facing, or the
     * ceiling above. Returns null if the snail is in free air, in which
     * case the step should use the raw direction toward the player.
     */
    private Vec3 contactNormal(ServerLevel level) {
        // Grounded beats everything. A snail standing on top of a ledge is
        // on the floor even while its hitbox still brushes the wall it just
        // crested - locking onto the wall there pinned the model and yaw to
        // the wall face, which read as the snail turning back toward the
        // block instead of walking off toward the player.
        if (this.onGround()) {
            this.lastWallNormal = null;
            BlockPos below = BlockPos.containing(new Vec3(this.getX(), this.getY() - 0.05, this.getZ()));
            if (!level.getBlockState(below).isAir()) {
                return new Vec3(0.0, 1.0, 0.0);
            }
        }

        Vec3 wallNormal = findWallNormal(level);
        if (wallNormal != null) {
            this.lastWallNormal = wallNormal;
            return wallNormal;
        }

        // Sticky grip: while still pressed against a wall (the climb step
        // presses into it), keep the last wall normal even if the raycast
        // momentarily misses at a ledge lip - this lets the snail finish
        // the final climb step onto the ledge instead of falling short.
        if (this.horizontalCollision && this.lastWallNormal != null) {
            return this.lastWallNormal;
        }

        // Ceiling - only when not grounded (the floor check above already
        // returned for grounded snails, even in a 1-block tunnel).
        if (!this.onGround() && touchingCeiling(level)) {
            return new Vec3(0.0, -1.0, 0.0);
        }

        return null;
    }

    /** Extra slop (in blocks) allowed between hitbox edge and block face when gripping. */
    private static final double WALL_GRIP_TOLERANCE = 0.05;

    /**
     * Find the wall the snail is actually pressed against, as an outward
     * normal, or null. Uses vanilla's own contact tool — a collision-shape
     * raycast from two heights (just above the foot, and the body centre)
     * toward each horizontal direction. The ray length is capped at half
     * the hitbox width plus slop, so "hit" means genuinely touching the
     * face (a snail drifting through the air block beside a wall gets no
     * grip, which previously read as climbing in mid-air), and because the
     * test runs against collision shapes it stays correct for partial
     * blocks like slabs, stairs and fences, which the old block-slot probe
     * misread. The foot-height probe matters at a ledge lip: while the
     * snail's lower body is still beside the wall it is still gripping it,
     * even after its centre of mass has risen past the top.
     */
    private Vec3 findWallNormal(ServerLevel level) {
        double maxDist = this.getBbWidth() * 0.5 + WALL_GRIP_TOLERANCE;
        double[] probeHeights = new double[]{
                this.getY() + 0.05,
                this.getY() + this.getBbHeight() * 0.5};
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Vec3 dir = d.getUnitVec3();
            for (double h : probeHeights) {
                Vec3 start = new Vec3(this.getX(), h, this.getZ());
                BlockHitResult hit = level.clip(new ClipContext(
                        start,
                        start.add(dir.scale(maxDist)),
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        this));
                // Only a face pointing back at the snail along the probe
                // axis makes a grip; a stair's angled or side face does not.
                if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == d.getOpposite()) {
                    return hit.getDirection().getUnitVec3();
                }
            }
        }
        return null;
    }

    /**
     * True when the hitbox is pressed against a ceiling above the body
     * centre (within grip slop). Same raycast reasoning as
     * {@link #findWallNormal}.
     */
    private boolean touchingCeiling(ServerLevel level) {
        Vec3 center = new Vec3(
                this.getX(),
                this.getY() + this.getBbHeight() * 0.5,
                this.getZ());
        BlockHitResult hit = level.clip(new ClipContext(
                center,
                center.add(0.0, this.getBbHeight() * 0.5 + WALL_GRIP_TOLERANCE, 0.0),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this));
        return hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.DOWN;
    }

    /**
     * Project a vector onto the plane perpendicular to the given normal.
     * Returns the tangential component (the original minus its projection
     * onto the normal). Caller should renormalize if length matters.
     */
    private static Vec3 projectOntoPlane(Vec3 v, Vec3 normal) {
        double d = v.dot(normal);
        return v.subtract(normal.scale(d));
    }

    /**
     * Rotate the snail so its underside (the model's bottom face) presses
     * against whichever surface it is travelling on, spider-style:
     *
     *   floor   -> flat, head faces the travel direction
     *   ceiling -> pitch 180, head faces the travel direction
     *   wall    -> head points up, down, or sideways along the wall to
     *              match the direction of travel; the exact pose is synced
     *              to the client via the DATA_SURFACE_POSE tracked datum.
     *
     * For a wall we also rotate the yaw so the bottom actually points into
     * the wall rather than along it: yaw faces INTO the wall in the head-up
     * pose, AWAY from it in the head-down pose, and along the travel
     * direction in the sideways poses. The movement direction is stored
     * separately in lastMoveDir, so this visual yaw change does not disturb
     * stepping or break-probing.
     */
    private void orientToSurface(ServerLevel level) {
        Vec3 normal = contactNormal(level);
        if (normal == null) {
            // Free air — flatten out and face the travel direction.
            this.setXRot(0.0f);
            this.setSurfacePose(POSE_FLOOR);
            faceTravelDirection();
            return;
        }

        if (normal.y > 0.5) {
            // Floor.
            this.setXRot(0.0f);
            this.setSurfacePose(POSE_FLOOR);
            faceTravelDirection();
        } else if (normal.y < -0.5) {
            // Ceiling — flip upside down; the yaw keeps facing the travel
            // direction so the head leads horizontally along the ceiling.
            this.setXRot(180.0f);
            this.setSurfacePose(POSE_CEILING);
            faceTravelDirection();
        } else {
            // Wall — pick the pose whose head points where the snail is
            // about to go:
            //   travel mostly UP    -> yaw INTO the wall,      pitch -90
            //   travel mostly DOWN  -> yaw AWAY from the wall, pitch +90
            //   travel mostly SIDEWAYS -> face along the travel and roll
            //       the foot onto the wall on whichever side it is.
            // The model reads the pose (not the pitch) and mirrors the
            // matching pivot shift.
            Vec3 travel = lastMoveDir != null ? lastMoveDir : Vec3.ZERO;
            float vy = (float) travel.y;
            double hLen = Math.sqrt(travel.x * travel.x + travel.z * travel.z);

            if (hLen > Math.abs(vy) && hLen > 1.0e-4) {
                // Sideways along the wall face: the entity faces the
                // horizontal travel direction and the model rolls onto its
                // side, foot toward the wall.
                float yaw = (float) Math.toDegrees(Math.atan2(-travel.x, travel.z));
                this.setYRot(yaw);
                this.setYHeadRot(yaw);
                this.setYBodyRot(yaw);
                this.setXRot(0.0f);

                // The entity's left vector for this yaw is
                // (cos yaw, 0, sin yaw); the into-wall direction is -normal.
                // A positive dot means the wall is on the snail's left.
                double psi = Math.toRadians(yaw);
                double dot = -normal.x * Math.cos(psi) - normal.z * Math.sin(psi);
                this.setSurfacePose(dot > 0.0 ? POSE_WALL_SIDE_LEFT : POSE_WALL_SIDE_RIGHT);
            } else if (vy >= 0.0F) {
                float yaw = (float) Math.toDegrees(Math.atan2(normal.x, -normal.z));
                this.setYRot(yaw);
                this.setYHeadRot(yaw);
                this.setYBodyRot(yaw);
                this.setXRot(-90.0f);
                this.setSurfacePose(POSE_WALL_UP);
            } else {
                float yaw = (float) Math.toDegrees(Math.atan2(-normal.x, normal.z));
                this.setYRot(yaw);
                this.setYHeadRot(yaw);
                this.setYBodyRot(yaw);
                this.setXRot(90.0f);
                this.setSurfacePose(POSE_WALL_DOWN);
            }
        }
    }

    /**
     * Point the model's yaw at the horizontal travel direction, so the
     * snail faces where it is actually going rather than snapping to a
     * cardinal direction. No-op when the travel direction has no horizontal
     * component (a pure vertical climb keeps its wall-facing yaw).
     */
    private void faceTravelDirection() {
        if (lastMoveDir == null) return;
        double hLen = Math.sqrt(lastMoveDir.x * lastMoveDir.x + lastMoveDir.z * lastMoveDir.z);
        if (hLen < 1.0e-4) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-lastMoveDir.x, lastMoveDir.z));
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
    }

    /**
     * Find the closest non-spectator player. Operators are now valid
     * targets — singleplayer auto-OPs the host, and the original op-filter
     * would have meant the mod never engaged the host at all.
     */
    public static ServerPlayer findClosestPlayer(ServerLevel level, Vec3 from) {
        ServerPlayer nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            double d = p.position().distanceToSqr(from);
            if (d < nearestDist) {
                nearestDist = d;
                nearest = p;
            }
        }
        return nearest;
    }

    public ServerPlayer findNearestPlayer(ServerLevel level) {
        return findClosestPlayer(level, this.position());
    }

    public boolean canBreakBlock(BlockState state) {
        SnailConfig.Snail cfg = SnailConfig.get().snail;
        if (!cfg.canBreakBlocks) return false;
        var keyOpt = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getResourceKey(state.getBlock());
        if (keyOpt.isEmpty()) return false;
        Identifier id = keyOpt.get().identifier();
        String idStr = id.toString();
        if (cfg.breakBlocksBlacklist.contains(idStr)) return false;
        if (!cfg.breakBlocksWhitelist.isEmpty()) {
            return cfg.breakBlocksWhitelist.contains(idStr);
        }
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    /**
     * The snail NEVER despawns. Vanilla hostile-mob logic discards mobs
     * with no player within 128 blocks - which instantly deleted a snail
     * spawned 5000+ blocks out, seconds after every spawn (and again after
     * every manager respawn). That is why the snail "spawned" but the
     * entity was never loaded and never moved.
     */
    @Override
    public void checkDespawn() {
    }
}