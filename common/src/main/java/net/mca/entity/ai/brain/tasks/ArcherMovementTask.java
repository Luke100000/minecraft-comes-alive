package net.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import dev.architectury.platform.Platform;
import net.mca.MCA;
import net.mca.entity.ai.ArcherMoveControl;
import net.mca.entity.ai.RangedWeaponHelper;
import net.mca.entity.ai.brain.sensor.GuardEnemiesSensor;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.brain.EntityLookTarget;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.LookTargetUtil;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public class ArcherMovementTask<E extends PathAwareEntity> extends MultiTickTask<E> {
    private static final double SPEED_MODIFIER = 0.5;
    private static final double KITE_SPEED_MODIFIER = 0.85;
    private static final double EMERGENCY_SPEED_MODIFIER = 0.9;
    private static final float LOOK_SPEED = 30.0F;
    private static final int VISIBLE_TICKS_BEFORE_STRAFE = 20;
    private static final int LOST_SIGHT_BEFORE_APPROACH = 10;
    private static final int DEBUG_LOG_INTERVAL_TICKS = 20;
    private static final double EMERGENCY_ENTER_DISTANCE_SQUARED = 12.25;
    private static final double EMERGENCY_EXIT_DISTANCE_SQUARED = 25.0;
    private static final double KITE_ENTER_DISTANCE_SQUARED = 36.0;
    private static final double KITE_EXIT_DISTANCE_SQUARED = 81.0;
    private static final double CLOSE_RANGE_VERTICAL_THREAT_DISTANCE = 2.5;
    private static final double KITE_SAFE_DISTANCE = 9.0;
    private static final double EMERGENCY_SAFE_DISTANCE = 6.0;
    private static final int AWAY_HORIZONTAL_DISTANCE = 12;
    private static final int AWAY_VERTICAL_DISTANCE = 5;
    private static final int AWAY_PATH_ATTEMPTS = 10;
    private static final int EMERGENCY_PATH_TICKS = 10;
    private static final int KITE_PATH_TICKS = 16;
    private static final int BLOCKED_PATH_TICKS_BEFORE_REPATH = 5;
    private static final int BLOCKED_STRAFE_TICKS_BEFORE_PAUSE = 6;
    private static final double MIN_USEFUL_DISTANCE_GAIN = 0.5;
    private static final double STUCK_HORIZONTAL_SPEED_SQUARED = 2.5E-5;

    private final double maximumRangeSquared;
    private LivingEntity lastTarget;
    private MovementState state = MovementState.IDLE;
    private int seeTime;
    private long lastDebugLogTime = Long.MIN_VALUE;
    private String lastDebugState = "";
    private boolean strafingClockwise;
    private int strafingTime = -1;
    private int repathCooldown;
    private int awayPathTicks;
    private int blockedPathTicks;
    private int blockedStrafeTicks;

    public ArcherMovementTask(int maximumRange) {
        super(ImmutableMap.of(
                MemoryModuleType.ATTACK_TARGET, MemoryModuleState.VALUE_PRESENT,
                MemoryModuleType.VISIBLE_MOBS, MemoryModuleState.REGISTERED
        ), 1200);
        this.maximumRangeSquared = maximumRange * maximumRange;
    }

    @Override
    protected boolean shouldRun(ServerWorld world, E entity) {
        return RangedWeaponHelper.isValidAttackTarget(entity, getAttackTarget(entity))
                && RangedWeaponHelper.isHoldingSupportedWeapon(entity);
    }

    @Override
    protected boolean shouldKeepRunning(ServerWorld world, E entity, long gameTime) {
        return RangedWeaponHelper.isValidAttackTarget(entity, getAttackTarget(entity))
                && RangedWeaponHelper.isHoldingSupportedWeapon(entity);
    }

    @Override
    protected void run(ServerWorld world, E entity, long gameTime) {
        resetState();
        entity.getBrain().forget(MemoryModuleType.WALK_TARGET);
        entity.getNavigation().stop();
    }

    @Override
    protected void keepRunning(ServerWorld world, E entity, long gameTime) {
        LivingEntity target = getAttackTarget(entity);
        if (!RangedWeaponHelper.isValidAttackTarget(entity, target)) {
            return;
        }

        entity.getBrain().forget(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);

        if (target != this.lastTarget) {
            this.lastTarget = target;
            this.seeTime = 0;
            enterState(entity, MovementState.IDLE);
            entity.getNavigation().stop();
        }

        boolean visible = LookTargetUtil.isVisibleInMemory(entity, target);
        updateSeeTime(visible);

        LivingEntity movementThreat = getNearestMovementThreat(entity, target);
        double targetDistanceSquared = entity.squaredDistanceTo(target);
        double threatDistanceSquared = entity.squaredDistanceTo(movementThreat);
        double threatVerticalDistance = Math.abs(entity.getY() - movementThreat.getY());
        double attackRangeSquared = RangedWeaponHelper.getAttackRangeSquared(entity, this.maximumRangeSquared);
        MovementState nextState = selectState(targetDistanceSquared, threatDistanceSquared, threatVerticalDistance, attackRangeSquared);
        enterState(entity, nextState);

        ArcherMoveControl moveControl = getArcherMoveControl(entity);
        moveControl.setEmergencyFleeing(this.state == MovementState.EMERGENCY_FLEE);

        switch (this.state) {
            case APPROACH -> {
                trackTarget(entity, target);
                approach(entity, target);
            }
            case EMERGENCY_FLEE -> {
                clearLookTarget(entity);
                moveAway(entity, movementThreat, EMERGENCY_SAFE_DISTANCE, EMERGENCY_PATH_TICKS, EMERGENCY_SPEED_MODIFIER, true);
            }
            case KITE -> {
                trackTarget(entity, target);
                moveAway(entity, movementThreat, KITE_SAFE_DISTANCE, KITE_PATH_TICKS, KITE_SPEED_MODIFIER, false);
            }
            case SIDE_STRAFE -> {
                faceTargetForStrafe(entity, target);
                strafe(entity);
            }
            case HOLD, IDLE -> {
                trackTarget(entity, target);
                hold(entity);
            }
        }

        if (Platform.isDevelopmentEnvironment()) {
            logDebugState(world, entity, target, movementThreat, visible, targetDistanceSquared, threatDistanceSquared, threatVerticalDistance);
        }
    }

    @Override
    protected void finishRunning(ServerWorld world, E entity, long gameTime) {
        resetState();
        ArcherMoveControl moveControl = getArcherMoveControl(entity);
        moveControl.setEmergencyFleeing(false);
        entity.getNavigation().stop();
    }

    private void resetState() {
        this.lastTarget = null;
        this.state = MovementState.IDLE;
        this.seeTime = 0;
        this.lastDebugState = "";
        this.strafingClockwise = false;
        this.strafingTime = -1;
        this.repathCooldown = 0;
        this.awayPathTicks = 0;
        this.blockedPathTicks = 0;
        this.blockedStrafeTicks = 0;
    }

    private MovementState selectState(double targetDistanceSquared, double threatDistanceSquared,
                                      double threatVerticalDistance, double attackRangeSquared) {
        boolean closeRangeThreat = threatVerticalDistance <= CLOSE_RANGE_VERTICAL_THREAT_DISTANCE;
        boolean wasEmergencyFleeing = this.state == MovementState.EMERGENCY_FLEE;
        double emergencyThreshold = wasEmergencyFleeing ? EMERGENCY_EXIT_DISTANCE_SQUARED : EMERGENCY_ENTER_DISTANCE_SQUARED;
        if (closeRangeThreat && threatDistanceSquared < emergencyThreshold) {
            return MovementState.EMERGENCY_FLEE;
        }

        boolean wasKiting = wasEmergencyFleeing || this.state == MovementState.KITE;
        double kiteThreshold = wasKiting ? KITE_EXIT_DISTANCE_SQUARED : KITE_ENTER_DISTANCE_SQUARED;
        if (closeRangeThreat && threatDistanceSquared < kiteThreshold) {
            return MovementState.KITE;
        }

        if (targetDistanceSquared > attackRangeSquared || this.seeTime < -LOST_SIGHT_BEFORE_APPROACH) {
            return MovementState.APPROACH;
        }

        return this.seeTime >= VISIBLE_TICKS_BEFORE_STRAFE ? MovementState.SIDE_STRAFE : MovementState.HOLD;
    }

    private void enterState(E entity, MovementState nextState) {
        if (this.state == nextState) {
            return;
        }

        this.state = nextState;
        this.strafingTime = nextState == MovementState.SIDE_STRAFE ? 0 : -1;
        this.repathCooldown = 0;
        this.awayPathTicks = 0;
        this.blockedPathTicks = 0;
        this.blockedStrafeTicks = 0;
        entity.getNavigation().stop();
    }

    private void approach(E entity, LivingEntity target) {
        this.awayPathTicks = 0;
        this.blockedPathTicks = 0;
        this.blockedStrafeTicks = 0;
        entity.getBrain().forget(MemoryModuleType.WALK_TARGET);

        if (--this.repathCooldown <= 0 || entity.getNavigation().isIdle()) {
            entity.getNavigation().startMovingTo(target, SPEED_MODIFIER);
            this.repathCooldown = getNextRepathCooldown(entity);
        }
    }

    private void moveAway(E entity, LivingEntity target, double desiredDistance, int pathTicks, double speedModifier, boolean allowPartialPath) {
        this.strafingTime = -1;
        this.blockedStrafeTicks = 0;
        entity.getBrain().forget(MemoryModuleType.WALK_TARGET);

        if (isCurrentPathBlocked(entity)) {
            this.blockedPathTicks++;
        } else {
            this.blockedPathTicks = 0;
        }

        if (this.awayPathTicks > 0
                && !entity.getNavigation().isIdle()
                && this.blockedPathTicks < BLOCKED_PATH_TICKS_BEFORE_REPATH) {
            this.awayPathTicks--;
            return;
        }

        if (--this.repathCooldown > 0 && !entity.getNavigation().isIdle()) {
            return;
        }

        Path path = findPathAway(entity, target, desiredDistance, allowPartialPath);
        if (path != null) {
            entity.getNavigation().startMovingAlong(path, speedModifier);
            this.awayPathTicks = pathTicks;
            this.repathCooldown = pathTicks;
            this.blockedPathTicks = 0;
        } else {
            entity.getNavigation().stop();
            this.awayPathTicks = 0;
            this.repathCooldown = 4;
        }
    }

    private boolean isCurrentPathBlocked(E entity) {
        if (entity.getNavigation().isIdle()) {
            return false;
        }

        return entity.isOnGround()
                && (entity.horizontalCollision
                || entity.collidedSoftly
                || entity.getVelocity().horizontalLengthSquared() < STUCK_HORIZONTAL_SPEED_SQUARED);
    }

    private Path findPathAway(E entity, LivingEntity target, double desiredDistance, boolean allowPartialPath) {
        double currentDistanceSquared = entity.squaredDistanceTo(target);
        double desiredDistanceSquared = desiredDistance * desiredDistance;
        Path bestPath = null;
        double bestScore = currentDistanceSquared;

        for (int i = 0; i < AWAY_PATH_ATTEMPTS; i++) {
            Vec3d candidate = NoPenaltyTargeting.findFrom(entity, AWAY_HORIZONTAL_DISTANCE, AWAY_VERTICAL_DISTANCE, target.getPos());
            if (candidate == null || candidate.squaredDistanceTo(target.getPos()) <= currentDistanceSquared + MIN_USEFUL_DISTANCE_GAIN) {
                continue;
            }

            Path path = entity.getNavigation().findPathTo(candidate.x, candidate.y, candidate.z, 0);
            if (path == null || !allowPartialPath && !path.reachesTarget()) {
                continue;
            }

            double endDistanceSquared = getPathEndDistanceSquared(path, target);
            if (endDistanceSquared <= currentDistanceSquared + MIN_USEFUL_DISTANCE_GAIN) {
                continue;
            }
            if (path.reachesTarget() && endDistanceSquared >= desiredDistanceSquared) {
                return path;
            }

            double score = endDistanceSquared + (path.reachesTarget() ? this.maximumRangeSquared : 0.0);
            if (score > bestScore) {
                bestScore = score;
                bestPath = path;
            }
        }

        return bestPath;
    }

    private static double getPathEndDistanceSquared(Path path, LivingEntity target) {
        return path.getEnd() == null ? 0.0 : path.getEnd().getBlockPos().toCenterPos().squaredDistanceTo(target.getPos());
    }

    private void strafe(E entity) {
        this.awayPathTicks = 0;
        this.blockedPathTicks = 0;
        this.repathCooldown = 0;
        entity.getBrain().forget(MemoryModuleType.WALK_TARGET);
        entity.getNavigation().stop();

        ArcherMoveControl moveControl = getArcherMoveControl(entity);
        this.strafingTime++;
        if (moveControl.wasLastStrafeBlocked() || isStrafeBlocked(entity)) {
            this.blockedStrafeTicks++;
        } else {
            this.blockedStrafeTicks = 0;
        }

        if (this.blockedStrafeTicks >= BLOCKED_STRAFE_TICKS_BEFORE_PAUSE) {
            this.strafingClockwise = !this.strafingClockwise;
            this.blockedStrafeTicks = 0;
            this.strafingTime = 0;
        }

        if (entity.horizontalCollision) {
            this.strafingClockwise = !this.strafingClockwise;
            this.strafingTime = 0;
        } else if (this.strafingTime >= 20) {
            if (entity.getRandom().nextFloat() < 0.3F) {
                this.strafingClockwise = !this.strafingClockwise;
            }
            this.strafingTime = 0;
        }

        moveControl.strafeForArcher(0.0F, this.strafingClockwise ? 0.5F : -0.5F);
    }

    private boolean isStrafeBlocked(E entity) {
        return this.strafingTime > 3
                && entity.isOnGround()
                && (entity.horizontalCollision
                || entity.collidedSoftly
                || entity.getVelocity().horizontalLengthSquared() < STUCK_HORIZONTAL_SPEED_SQUARED);
    }

    private void hold(E entity) {
        this.strafingTime = -1;
        this.awayPathTicks = 0;
        this.blockedPathTicks = 0;
        this.blockedStrafeTicks = 0;
        this.repathCooldown = 0;
        entity.getBrain().forget(MemoryModuleType.WALK_TARGET);
        entity.getNavigation().stop();
    }

    private void trackTarget(E entity, LivingEntity target) {
        entity.getBrain().remember(MemoryModuleType.LOOK_TARGET, new EntityLookTarget(target, true));
        entity.lookAtEntity(target, LOOK_SPEED, LOOK_SPEED);
    }

    private void faceTargetForStrafe(E entity, LivingEntity target) {
        entity.getBrain().forget(MemoryModuleType.LOOK_TARGET);
        entity.lookAtEntity(target, LOOK_SPEED, LOOK_SPEED);
    }

    private void clearLookTarget(E entity) {
        entity.getBrain().forget(MemoryModuleType.LOOK_TARGET);
    }

    private static int getNextRepathCooldown(MobEntity entity) {
        return 10 + entity.getRandom().nextInt(10);
    }

    private void updateSeeTime(boolean visible) {
        boolean hadLineOfSight = this.seeTime > 0;
        if (visible != hadLineOfSight) {
            this.seeTime = 0;
        }

        if (visible) {
            this.seeTime++;
        } else {
            this.seeTime--;
        }
    }

    private void logDebugState(ServerWorld world, E entity, LivingEntity target, LivingEntity movementThreat, boolean visible,
                               double targetDistanceSquared, double threatDistanceSquared, double threatVerticalDistance) {
        ArcherMoveControl moveControl = getArcherMoveControl(entity);
        String stateKey = target.getUuid() + ":" + movementThreat.getUuid() + ":" + this.state + ":" + visible;
        long gameTime = world.getTime();
        if (stateKey.equals(this.lastDebugState) && gameTime - this.lastDebugLogTime < DEBUG_LOG_INTERVAL_TICKS) {
            return;
        }

        this.lastDebugState = stateKey;
        this.lastDebugLogTime = gameTime;
        MCA.LOGGER.info(
                "[MCA Archer Movement] entity={} entityName=\"{}\" target={} targetName=\"{}\" threat={} threatName=\"{}\" movement={} targetDistSqr={} threatDistSqr={} threatVerticalDist={} los={} seeTime={} strafingTime={} strafingClockwise={} strafeResult={} awayPathTicks={} blockedPathTicks={} blockedStrafeTicks={} navIdle={} horizontalCollision={} onGround={} yaw={} targetYaw={} headYaw={} bodyYaw={} movementSpeed={} velocity={} pos={} targetPos={} threatPos={}",
                entity.getUuidAsString(),
                entity.getName().getString(),
                target.getUuidAsString(),
                target.getName().getString(),
                movementThreat.getUuidAsString(),
                movementThreat.getName().getString(),
                this.state.debugName,
                String.format("%.2f", targetDistanceSquared),
                String.format("%.2f", threatDistanceSquared),
                String.format("%.2f", threatVerticalDistance),
                visible,
                this.seeTime,
                this.strafingTime,
                this.strafingClockwise,
                moveControl.getLastStrafeResult(),
                this.awayPathTicks,
                this.blockedPathTicks,
                this.blockedStrafeTicks,
                entity.getNavigation().isIdle(),
                entity.horizontalCollision,
                entity.isOnGround(),
                String.format("%.2f", entity.getYaw()),
                String.format("%.2f", getTargetYaw(entity, target)),
                String.format("%.2f", entity.headYaw),
                String.format("%.2f", entity.bodyYaw),
                String.format("%.4f", entity.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED)),
                entity.getVelocity(),
                entity.getBlockPos(),
                target.getBlockPos(),
                movementThreat.getBlockPos()
        );
    }

    private static LivingEntity getNearestMovementThreat(MobEntity entity, LivingEntity fallback) {
        return entity.getBrain().getOptionalMemory(MemoryModuleType.VISIBLE_MOBS)
                .flatMap(visible -> visible.stream(candidate -> RangedWeaponHelper.isValidAttackTarget(entity, candidate))
                        .filter(candidate -> Math.abs(entity.getY() - candidate.getY()) <= CLOSE_RANGE_VERTICAL_THREAT_DISTANCE)
                        .filter(candidate -> GuardEnemiesSensor.isGuardEnemy(candidate, entity))
                        .findFirst())
                .orElse(fallback);
    }

    private static float getTargetYaw(MobEntity entity, LivingEntity target) {
        double dx = target.getX() - entity.getX();
        double dz = target.getZ() - entity.getZ();
        return Math.abs(dx) <= 1.0E-5 && Math.abs(dz) <= 1.0E-5
                ? entity.getYaw()
                : (float) (Math.atan2(dz, dx) * 180.0F / Math.PI) - 90.0F;
    }

    private static LivingEntity getAttackTarget(LivingEntity entity) {
        return entity.getBrain().getOptionalMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    private static ArcherMoveControl getArcherMoveControl(MobEntity entity) {
        if (entity.getMoveControl() instanceof ArcherMoveControl archerMoveControl) {
            return archerMoveControl;
        }
        throw new IllegalStateException(entity.getType() + " must use ArcherMoveControl for archer movement");
    }

    private enum MovementState {
        IDLE("idle"),
        APPROACH("approach"),
        EMERGENCY_FLEE("emergency_flee"),
        KITE("kite"),
        SIDE_STRAFE("strafe"),
        HOLD("hold");

        private final String debugName;

        MovementState(String debugName) {
            this.debugName = debugName;
        }
    }
}
