package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.ArcherMoveControl;
import net.conczin.mca.entity.ai.RangedWeaponHelper;
import net.conczin.mca.entity.ai.brain.sensor.GuardEnemiesSensor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class ArcherMovementTask<E extends VillagerEntityMCA> extends Behavior<E> {
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
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.REGISTERED
        ), 1200);
        this.maximumRangeSquared = maximumRange * maximumRange;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, E entity) {
        return hasValidTarget(getAttackTarget(entity)) && isHoldingRangedWeapon(entity);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return hasValidTarget(getAttackTarget(entity)) && isHoldingRangedWeapon(entity);
    }

    @Override
    protected void start(ServerLevel level, E entity, long gameTime) {
        resetState();
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        entity.getNavigation().stop();
    }

    @Override
    protected void tick(ServerLevel level, E entity, long gameTime) {
        LivingEntity target = getAttackTarget(entity);
        if (!hasValidTarget(target)) {
            return;
        }

        entity.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);

        if (target != this.lastTarget) {
            this.lastTarget = target;
            this.seeTime = 0;
            enterState(entity, MovementState.IDLE);
            entity.getNavigation().stop();
        }

        boolean visible = entity.getSensing().hasLineOfSight(target);
        updateSeeTime(visible);

        LivingEntity movementThreat = getNearestMovementThreat(entity, target);
        double targetDistanceSquared = entity.distanceToSqr(target);
        double threatDistanceSquared = entity.distanceToSqr(movementThreat);
        double threatVerticalDistance = Math.abs(entity.getY() - movementThreat.getY());
        double attackRangeSquared = RangedWeaponHelper.getAttackRangeSquared(entity, this.maximumRangeSquared);
        MovementState nextState = selectState(targetDistanceSquared, threatDistanceSquared, threatVerticalDistance, attackRangeSquared);
        enterState(entity, nextState);

        ArcherMoveControl moveControl = entity.getArcherMoveControl();
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

        if (MCA.platformHelper.isDevelopmentEnvironment()) {
            logDebugState(level, entity, target, movementThreat, visible, targetDistanceSquared, threatDistanceSquared, threatVerticalDistance);
        }
    }

    @Override
    protected void stop(ServerLevel level, E entity, long gameTime) {
        resetState();
        ArcherMoveControl moveControl = entity.getArcherMoveControl();
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
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

        if (--this.repathCooldown <= 0 || entity.getNavigation().isDone()) {
            entity.getNavigation().moveTo(target, SPEED_MODIFIER);
            this.repathCooldown = getNextRepathCooldown(entity);
        }
    }

    private void moveAway(E entity, LivingEntity target, double desiredDistance, int pathTicks, double speedModifier, boolean allowPartialPath) {
        this.strafingTime = -1;
        this.blockedStrafeTicks = 0;
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

        if (isCurrentPathBlocked(entity)) {
            this.blockedPathTicks++;
        } else {
            this.blockedPathTicks = 0;
        }

        if (this.awayPathTicks > 0
                && !entity.getNavigation().isDone()
                && this.blockedPathTicks < BLOCKED_PATH_TICKS_BEFORE_REPATH) {
            this.awayPathTicks--;
            return;
        }

        if (--this.repathCooldown > 0 && !entity.getNavigation().isDone()) {
            return;
        }

        Path path = findPathAway(entity, target, desiredDistance, allowPartialPath);
        if (path != null) {
            entity.getNavigation().moveTo(path, speedModifier);
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
        if (entity.getNavigation().isDone()) {
            return false;
        }

        return entity.onGround()
                && (entity.horizontalCollision
                || entity.minorHorizontalCollision
                || entity.getDeltaMovement().horizontalDistanceSqr() < STUCK_HORIZONTAL_SPEED_SQUARED);
    }

    private Path findPathAway(E entity, LivingEntity target, double desiredDistance, boolean allowPartialPath) {
        double currentDistanceSquared = entity.distanceToSqr(target);
        double desiredDistanceSquared = desiredDistance * desiredDistance;
        Path bestPath = null;
        double bestScore = currentDistanceSquared;

        for (int i = 0; i < AWAY_PATH_ATTEMPTS; i++) {
            Vec3 candidate = LandRandomPos.getPosAway(entity, AWAY_HORIZONTAL_DISTANCE, AWAY_VERTICAL_DISTANCE, target.position());
            if (candidate == null || candidate.distanceToSqr(target.position()) <= currentDistanceSquared + MIN_USEFUL_DISTANCE_GAIN) {
                continue;
            }

            Path path = entity.getNavigation().createPath(candidate.x, candidate.y, candidate.z, 0);
            if (path == null || !allowPartialPath && !path.canReach()) {
                continue;
            }

            double endDistanceSquared = getPathEndDistanceSquared(path, target);
            if (endDistanceSquared <= currentDistanceSquared + MIN_USEFUL_DISTANCE_GAIN) {
                continue;
            }
            if (path.canReach() && endDistanceSquared >= desiredDistanceSquared) {
                return path;
            }

            double score = endDistanceSquared + (path.canReach() ? this.maximumRangeSquared : 0.0);
            if (score > bestScore) {
                bestScore = score;
                bestPath = path;
            }
        }

        return bestPath;
    }

    private static double getPathEndDistanceSquared(Path path, LivingEntity target) {
        return path.getEndNode() == null ? 0.0 : path.getEndNode().asBlockPos().distToCenterSqr(target.position());
    }

    private void strafe(E entity) {
        this.awayPathTicks = 0;
        this.blockedPathTicks = 0;
        this.repathCooldown = 0;
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        entity.getNavigation().stop();

        ArcherMoveControl moveControl = entity.getArcherMoveControl();
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
                && entity.onGround()
                && (entity.horizontalCollision
                || entity.minorHorizontalCollision
                || entity.getDeltaMovement().horizontalDistanceSqr() < STUCK_HORIZONTAL_SPEED_SQUARED);
    }

    private void hold(E entity) {
        this.strafingTime = -1;
        this.awayPathTicks = 0;
        this.blockedPathTicks = 0;
        this.blockedStrafeTicks = 0;
        this.repathCooldown = 0;
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        entity.getNavigation().stop();
    }

    private void trackTarget(E entity, LivingEntity target) {
        entity.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        entity.getLookControl().setLookAt(target, LOOK_SPEED, LOOK_SPEED);
    }

    private void faceTargetForStrafe(E entity, LivingEntity target) {
        entity.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        entity.lookAt(target, LOOK_SPEED, LOOK_SPEED);
    }

    private void clearLookTarget(E entity) {
        entity.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    private static int getNextRepathCooldown(Mob entity) {
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

    private void logDebugState(ServerLevel level, E entity, LivingEntity target, LivingEntity movementThreat, boolean visible,
                               double targetDistanceSquared, double threatDistanceSquared, double threatVerticalDistance) {
        ArcherMoveControl moveControl = entity.getArcherMoveControl();
        String stateKey = target.getUUID() + ":" + movementThreat.getUUID() + ":" + this.state + ":" + visible;
        long gameTime = level.getGameTime();
        if (stateKey.equals(this.lastDebugState) && gameTime - this.lastDebugLogTime < DEBUG_LOG_INTERVAL_TICKS) {
            return;
        }

        this.lastDebugState = stateKey;
        this.lastDebugLogTime = gameTime;
        MCA.LOGGER.info(
                "[MCA Archer Movement] entity={} entityName=\"{}\" target={} targetName=\"{}\" threat={} threatName=\"{}\" movement={} targetDistSqr={} threatDistSqr={} threatVerticalDist={} los={} seeTime={} strafingTime={} strafingClockwise={} strafeResult={} awayPathTicks={} blockedPathTicks={} blockedStrafeTicks={} navDone={} horizontalCollision={} minorHorizontalCollision={} onGround={} yRot={} targetYRot={} yHeadRot={} yBodyRot={} movementSpeed={} deltaMovement={} pos={} targetPos={} threatPos={}",
                entity.getStringUUID(),
                entity.getName().getString(),
                target.getStringUUID(),
                target.getName().getString(),
                movementThreat.getStringUUID(),
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
                entity.getNavigation().isDone(),
                entity.horizontalCollision,
                entity.minorHorizontalCollision,
                entity.onGround(),
                String.format("%.2f", entity.getYRot()),
                String.format("%.2f", getTargetYRot(entity, target)),
                String.format("%.2f", entity.yHeadRot),
                String.format("%.2f", entity.yBodyRot),
                String.format("%.4f", entity.getAttributeValue(Attributes.MOVEMENT_SPEED)),
                entity.getDeltaMovement(),
                entity.blockPosition(),
                target.blockPosition(),
                movementThreat.blockPosition()
        );
    }

    private static LivingEntity getNearestMovementThreat(LivingEntity entity, LivingEntity fallback) {
        return entity.getBrain().getMemoryInternal(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                .flatMap(visible -> visible.find(ArcherMovementTask::hasValidTarget)
                        .filter(candidate -> Math.abs(entity.getY() - candidate.getY()) <= CLOSE_RANGE_VERTICAL_THREAT_DISTANCE)
                        .filter(candidate -> GuardEnemiesSensor.isGuardEnemy(candidate, entity))
                        .findFirst())
                .orElse(fallback);
    }

    private static float getTargetYRot(Mob entity, LivingEntity target) {
        double dx = target.getX() - entity.getX();
        double dz = target.getZ() - entity.getZ();
        return Math.abs(dx) <= 1.0E-5 && Math.abs(dz) <= 1.0E-5
                ? entity.getYRot()
                : (float)(Math.atan2(dz, dx) * 180.0F / Math.PI) - 90.0F;
    }

    private static LivingEntity getAttackTarget(LivingEntity entity) {
        return entity.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    private static boolean hasValidTarget(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved();
    }

    private static boolean isHoldingRangedWeapon(Mob entity) {
        return RangedWeaponHelper.isHoldingSupportedWeapon(entity);
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
