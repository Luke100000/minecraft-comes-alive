package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MCAMoveControl;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.RangedWeaponHelper;
import net.conczin.mca.entity.ai.navigation.CombatEscapePositionTracker;
import net.conczin.mca.entity.ai.navigation.MultiTargetPositionTracker;
import net.conczin.mca.entity.ai.navigation.PersistentPathTarget;
import net.conczin.mca.entity.ai.brain.sensor.GuardEnemiesSensor;
import net.conczin.mca.entity.ai.brain.UnreachableCombatTargets;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public class ArcherMovementTask<E extends VillagerEntityMCA> extends Behavior<E> {
    private static final double SPEED_MODIFIER = 0.5D;
    private static final double KITE_RETREAT_SPEED_MODIFIER = 0.75D;
    private static final double EMERGENCY_RETREAT_SPEED_MODIFIER = 0.9D;
    private static final float LOOK_SPEED = 30.0F;
    private static final int LOST_SIGHT_BEFORE_REPOSITION = 20;
    private static final int BLOCKED_SHOT_BEFORE_REPOSITION = 20;
    private static final int CLEAR_SHOT_BEFORE_HOLD = 3;
    private static final int LOST_CONTACT_WITHOUT_ROUTE_TICKS = 400;
    private static final int DEBUG_LOG_INTERVAL_TICKS = 20;
    private static final int FAILED_ROUTE_STALL_TICKS = 80;

    static final double EMERGENCY_ENTER_DISTANCE_SQUARED = 12.25D;
    private static final double EMERGENCY_EXIT_DISTANCE_SQUARED = 25.0D;
    static final double KITE_ENTER_DISTANCE_SQUARED = 36.0D;
    private static final double KITE_EXIT_DISTANCE_SQUARED = 81.0D;
    private static final double RANGED_THREAT_ENTER_DISTANCE_SQUARED = 64.0D;
    private static final double RANGED_THREAT_EXIT_DISTANCE_SQUARED = 121.0D;
    static final double CLOSE_RANGE_VERTICAL_THREAT_DISTANCE = 2.5D;
    private static final double KITE_SAFE_DISTANCE = 9.0D;
    private static final double EMERGENCY_SAFE_DISTANCE = 6.0D;
    private static final double APPROACH_EXIT_RANGE_BUFFER = 1.0D;

    private static final int MIN_HOLD_TICKS_BEFORE_STRAFE = 20;
    private static final int STRAFE_LOST_SIGHT_GRACE = 4;
    private static final int MIN_STRAFE_TICKS = 24;
    private static final int MAX_STRAFE_TICKS = 40;
    private static final int MIN_STRAFE_COOLDOWN = 20;
    private static final int MAX_STRAFE_COOLDOWN = 40;
    private static final float STRAFE_INPUT = 0.35F;
    private static final double STUCK_HORIZONTAL_SPEED_SQUARED = 2.5E-5D;

    static RangedCombatState selectBaseState(
            RangedCombatState currentState,
            double targetDistanceSquared,
            double threatDistanceSquared,
            double threatVerticalDistance,
            double attackRangeSquared,
            int seeTime,
            int blockedShotTicks,
            int clearShotTicks
    ) {
        return selectBaseDecision(
                currentState,
                targetDistanceSquared,
                threatDistanceSquared,
                threatVerticalDistance,
                attackRangeSquared,
                seeTime,
                blockedShotTicks,
                clearShotTicks
        ).state();
    }

    private static BaseStateDecision selectBaseDecision(
            RangedCombatState currentState,
            double targetDistanceSquared,
            double threatDistanceSquared,
            double threatVerticalDistance,
            double attackRangeSquared,
            int seeTime,
            int blockedShotTicks,
            int clearShotTicks
    ) {
        boolean closeRangeThreat = threatVerticalDistance <= CLOSE_RANGE_VERTICAL_THREAT_DISTANCE;
        if (closeRangeThreat) {
            if (currentState == RangedCombatState.EMERGENCY_FLEE
                    && threatDistanceSquared < EMERGENCY_EXIT_DISTANCE_SQUARED) {
                return new BaseStateDecision(RangedCombatState.EMERGENCY_FLEE, "emergency_hysteresis");
            }
            if (threatDistanceSquared < EMERGENCY_ENTER_DISTANCE_SQUARED) {
                return new BaseStateDecision(RangedCombatState.EMERGENCY_FLEE, "emergency_close_threat");
            }
        }

        if (closeRangeThreat) {
            if ((currentState == RangedCombatState.EMERGENCY_FLEE || currentState == RangedCombatState.KITE)
                    && threatDistanceSquared < KITE_EXIT_DISTANCE_SQUARED) {
                return new BaseStateDecision(RangedCombatState.KITE, "kite_hysteresis");
            }
            if (threatDistanceSquared < KITE_ENTER_DISTANCE_SQUARED) {
                return new BaseStateDecision(RangedCombatState.KITE, "kite_close_threat");
            }
        }

        if (targetDistanceSquared > attackRangeSquared) {
            return new BaseStateDecision(RangedCombatState.APPROACH, "out_of_range");
        }
        if (seeTime < -LOST_SIGHT_BEFORE_REPOSITION) {
            return new BaseStateDecision(RangedCombatState.REPOSITION, "eye_los_lost");
        }
        if (blockedShotTicks >= BLOCKED_SHOT_BEFORE_REPOSITION) {
            return new BaseStateDecision(RangedCombatState.REPOSITION, "trajectory_blocked");
        }
        if (currentState == RangedCombatState.REPOSITION && clearShotTicks < CLEAR_SHOT_BEFORE_HOLD) {
            return new BaseStateDecision(RangedCombatState.REPOSITION, "shot_clearance_settling");
        }

        if (currentState == RangedCombatState.APPROACH) {
            double attackRange = Math.sqrt(attackRangeSquared);
            double approachExitRange = Math.max(0.0D, attackRange - APPROACH_EXIT_RANGE_BUFFER);
            if (targetDistanceSquared > approachExitRange * approachExitRange) {
                return new BaseStateDecision(RangedCombatState.APPROACH, "approach_hysteresis");
            }
        }
        return new BaseStateDecision(RangedCombatState.HOLD, "hold");
    }

    static boolean shouldStartStrafe(int holdTicks, int strafeCooldown) {
        return holdTicks >= MIN_HOLD_TICKS_BEFORE_STRAFE && strafeCooldown <= 0;
    }

    static boolean shouldCancelStrafe(boolean visible, boolean inRange, boolean closeThreat, boolean collided, boolean stalled) {
        return strafeCancelReason(visible, inRange, closeThreat, collided, stalled) != null;
    }

    private final double maximumRangeSquared;
    private LivingEntity lastTarget;
    private int seeTime;
    private int blockedShotTicks;
    private int clearShotTicks;
    private int lostContactTicks;
    private int walkTargetRetryCooldown;
    private WalkTarget combatWalkTarget;
    private long lastEscapeRouteCheck = Long.MIN_VALUE;

    private int holdTicks;
    private int strafeCooldown;
    private int strafeTicksRemaining;
    private int strafeTicksElapsed;
    private float strafeDirection;
    private int blockedStrafeAttempts;
    private Vec3 progressPosition;
    private Vec3 progressTargetPosition;
    private long lastProgressTime;
    private boolean failedCombatRoute;

    private long lastDebugLogTime = Long.MIN_VALUE;
    private String lastDebugState = "";

    public ArcherMovementTask(int maximumRange) {
        super(ImmutableMap.of(
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.REGISTERED
        ), 1200);
        this.maximumRangeSquared = maximumRange * maximumRange;
    }

    public static Optional<LivingEntity> findImmediateArcherAttacker(VillagerEntityMCA archer) {
        LivingEntity current = archer.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (isImmediateAttacker(archer, current)) {
            return Optional.of(current);
        }
        return archer.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElse(List.of()).stream()
                .filter(candidate -> isImmediateAttacker(archer, candidate))
                .min(java.util.Comparator.comparingDouble(archer::distanceToSqr));
    }

    private static boolean isImmediateAttacker(VillagerEntityMCA archer, LivingEntity candidate) {
        return RangedWeaponHelper.isValidAttackTarget(archer, candidate)
                && archer.distanceToSqr(candidate) < KITE_EXIT_DISTANCE_SQUARED
                && (archer.getLastHurtByMob() == candidate || candidate instanceof Mob mob && mob.getTarget() == archer)
                && GuardEnemiesSensor.isGuardEnemy(candidate, archer)
                && RangedCombatPositioning.isCloseRangeMovementThreat(archer, candidate);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, E entity) {
        return !entity.getBrain().isActive(Activity.PANIC)
                && RangedWeaponHelper.isValidAttackTarget(entity, getAttackTarget(entity))
                && RangedWeaponHelper.isHoldingSupportedWeapon(entity);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return !entity.getBrain().isActive(Activity.PANIC)
                && RangedWeaponHelper.isValidAttackTarget(entity, getAttackTarget(entity))
                && RangedWeaponHelper.isHoldingSupportedWeapon(entity);
    }

    @Override
    protected void start(ServerLevel level, E entity, long gameTime) {
        resetLocalState();
        WalkTarget previousWalk = entity.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (previousWalk != null && previousWalk.getTarget() instanceof BlockPosTracker
                && !(previousWalk.getTarget() instanceof PersistentPathTarget)
                && !entity.getBrain().hasMemoryValue(MemoryModuleTypeMCA.PLAYER_FOLLOWING)
                && !entity.getBrain().hasMemoryValue(MemoryModuleTypeMCA.STAYING)) {
            // Ordinary stroll/patrol intent predates combat. Release it once;
            // MoveToTargetSink owns stopping its path on the next Brain tick.
            // Commands, persistent journeys and later intents retain ownership.
            entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            logMovementIntent(entity, "release_walk", "combat_replaces_stroll", previousWalk);
        }
    }

    @Override
    protected void tick(ServerLevel level, E entity, long gameTime) {
        if (entity.getBrain().isActive(Activity.PANIC)) {
            clearCombatState(entity);
            return;
        }

        LivingEntity target = getAttackTarget(entity);
        if (!RangedWeaponHelper.isValidAttackTarget(entity, target)) {
            clearCombatState(entity);
            return;
        }

        tickCooldowns();

        boolean targetChanged = target != this.lastTarget;
        if (targetChanged) {
            clearCombatState(entity);
            resetTacticalTimers();
            this.lastTarget = target;
            this.progressPosition = entity.position();
            this.progressTargetPosition = target.position();
            this.lastProgressTime = gameTime;
        }

        boolean visible = entity.getSensing().hasLineOfSight(target);
        this.lostContactTicks = visible ? 0 : this.lostContactTicks + 1;
        if (this.lostContactTicks >= LOST_CONTACT_WITHOUT_ROUTE_TICKS
                && !entity.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                && !entity.getBrain().hasMemoryValue(MemoryModuleType.PATH)
                && entity.getNavigation().isDone()) {
            clearCombatState(entity);
            entity.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            return;
        }
        double attackRangeSquared = RangedWeaponHelper.getAttackRangeSquared(entity, this.maximumRangeSquared);
        boolean usableShot = visible && (RangedWeaponHelper.getBowHoldingHand(entity) == null
                || RangedWeaponHelper.canShootBowFrom(entity, entity.position(), target));
        if (abandonUnreachableTarget(entity, target, gameTime,
                usableShot && entity.distanceToSqr(target) <= attackRangeSquared)) {
            return;
        }
        observeWalkTargetLifecycle(entity);
        updateSeeTime(visible);
        if (visible && entity.distanceToSqr(target) <= attackRangeSquared) {
            this.blockedShotTicks = usableShot ? 0 : Math.min(this.blockedShotTicks + 1, BLOCKED_SHOT_BEFORE_REPOSITION);
            this.clearShotTicks = usableShot ? Math.min(this.clearShotTicks + 1, CLEAR_SHOT_BEFORE_HOLD) : 0;
        } else {
            this.blockedShotTicks = 0;
            this.clearShotTicks = 0;
        }

        RangedCombatPositioning.ThreatContext threatContext =
                RangedCombatPositioning.collectThreatContext(entity, target);
        revalidateOwnedEscape(entity, gameTime);
        LivingEntity movementThreat = threatContext.nearestEscapeThreat();
        if (movementThreat == null) {
            movementThreat = target;
        }
        double targetDistanceSquared = entity.distanceToSqr(target);
        double threatDistanceSquared = entity.distanceToSqr(movementThreat);
        double threatVerticalDistance = Math.abs(entity.getY() - movementThreat.getY());
        double tacticalVerticalDistance = RangedCombatPositioning.isCloseRangeMovementThreat(entity, movementThreat)
                ? Math.min(threatVerticalDistance, CLOSE_RANGE_VERTICAL_THREAT_DISTANCE) : threatVerticalDistance;

        RangedCombatState currentState = RangedCombatState.current(entity).orElse(RangedCombatState.HOLD);
        RangedCombatState hysteresisState = currentState == RangedCombatState.STRAFE
                ? RangedCombatState.HOLD
                : currentState;
        BaseStateDecision baseDecision = selectBaseDecision(
                hysteresisState,
                targetDistanceSquared,
                threatDistanceSquared,
                tacticalVerticalDistance,
                attackRangeSquared,
                this.seeTime,
                this.blockedShotTicks,
                this.clearShotTicks
        );
        RangedCombatState baseState = baseDecision.state();
        // Melee spacing is too late against an archer already being aimed at.
        // Only retreat early from a ranged mob that is actually targeting us;
        // nearby passive ranged mobs should not force constant kiting.
        boolean evadingRangedAttacker = baseState != RangedCombatState.EMERGENCY_FLEE
                && movementThreat instanceof RangedAttackMob
                && movementThreat instanceof Mob rangedMob
                && (rangedMob.getTarget() == entity || entity.getLastHurtByMob() == movementThreat)
                && (threatDistanceSquared < RANGED_THREAT_ENTER_DISTANCE_SQUARED
                    || isRetreatState(currentState)
                    && threatDistanceSquared < RANGED_THREAT_EXIT_DISTANCE_SQUARED);
        if (evadingRangedAttacker) {
            baseState = RangedCombatState.KITE;
            baseDecision = new BaseStateDecision(baseState, "evade_active_ranged_attacker");
        }
        if (isRetreatState(currentState) && !isRetreatState(baseState) && ownsCombatWalkTarget(entity)
                && this.combatWalkTarget.getTarget() instanceof CombatEscapePositionTracker escape
                && !escape.isReached(entity, this.combatWalkTarget.getCloseEnoughDist())) {
            baseState = RangedCombatState.KITE;
            baseDecision = new BaseStateDecision(baseState, "finish_escape_waypoint");
        }
        if (currentState == RangedCombatState.RECOVER && ownsCombatWalkTarget(entity)
                && !isRetreatState(baseState)) {
            baseState = RangedCombatState.RECOVER;
        }

        boolean closeThreat = tacticalVerticalDistance <= CLOSE_RANGE_VERTICAL_THREAT_DISTANCE
                && threatDistanceSquared < KITE_ENTER_DISTANCE_SQUARED;
        boolean inRange = targetDistanceSquared <= attackRangeSquared;

        if (baseState != RangedCombatState.HOLD) {
            boolean stateChanged = currentState != baseState;
            if (currentState == RangedCombatState.STRAFE) {
                finishStrafe(entity, "base_state_change");
            }
            boolean preserveRetreatWalkTarget = stateChanged
                    && isRetreatState(currentState)
                    && isRetreatState(baseState)
                    && ownsCombatWalkTarget(entity);
            if (stateChanged && !preserveRetreatWalkTarget) {
                clearCombatWalkTarget(entity);
            }
            this.holdTicks = 0;
            setState(entity, baseState);

            if (MCA.platformHelper.isDevelopmentEnvironment() && (stateChanged || targetChanged)) {
                logStateTransition(
                        entity,
                        target,
                        movementThreat,
                        currentState,
                        baseState,
                        baseDecision.reason(),
                        targetChanged,
                        targetDistanceSquared,
                        threatDistanceSquared,
                        visible
                );
            }

            switch (baseState) {
                case APPROACH -> {
                    trackTarget(entity, target);
                    publishApproach(entity, target, threatContext.hazards());
                }
                case REPOSITION, RECOVER -> {
                    trackTarget(entity, target);
                    publishReposition(
                            entity,
                            target,
                            threatContext,
                            attackRangeSquared
                    );
                }
                case KITE -> {
                    trackTarget(entity, target);
                    publishKiteAway(entity, threatContext, target, attackRangeSquared,
                            evadingRangedAttacker && threatDistanceSquared >= KITE_EXIT_DISTANCE_SQUARED);
                }
                case EMERGENCY_FLEE -> {
                    publishEmergencyAway(entity, threatContext);
                    trackEscapeOrTarget(entity, target);
                }
                default -> throw new IllegalStateException("Unexpected ranged combat base state: " + baseState);
            }
        } else {
            handleHoldOrStrafe(
                    entity,
                    target,
                    currentState,
                    targetChanged,
                    visible,
                    inRange,
                    closeThreat,
                    threatContext,
                    attackRangeSquared
            );
        }

        if (MCA.platformHelper.isDevelopmentEnvironment()) {
            logDebugState(
                    level,
                    entity,
                    target,
                    movementThreat,
                    visible,
                    targetDistanceSquared,
                    threatDistanceSquared,
                    threatVerticalDistance
            );
        }
    }

    @Override
    protected void stop(ServerLevel level, E entity, long gameTime) {
        clearCombatState(entity);
        resetLocalState();
    }

    private void handleHoldOrStrafe(
            E entity,
            LivingEntity target,
            RangedCombatState currentState,
            boolean targetChanged,
            boolean visible,
            boolean inRange,
            boolean closeThreat,
            RangedCombatPositioning.ThreatContext threats,
            double attackRangeSquared
    ) {
        List<? extends LivingEntity> hazards = threats.hazards();
        if (targetChanged || (currentState != RangedCombatState.HOLD && currentState != RangedCombatState.STRAFE)) {
            clearCombatWalkTarget(entity);
        }
        trackTarget(entity, target);

        if (entity.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            if (currentState == RangedCombatState.STRAFE) {
                finishStrafe(entity, "walk_target_preempted");
            } else {
                this.holdTicks = 0;
                setState(entity, RangedCombatState.HOLD);
            }
            return;
        }

        if (currentState == RangedCombatState.STRAFE) {
            continueStrafe(entity, target, visible, inRange, closeThreat, hazards);
            return;
        }

        if (this.blockedStrafeAttempts >= 2 && publishRecovery(entity, target,
                threats, attackRangeSquared, "leave_blocked_corner")) {
            return;
        }
        setState(entity, RangedCombatState.HOLD);
        if (entity.getNavigation().isDone()) {
            // LookControl turns the head, but a stationary mob's movement yaw
            // can remain unchanged. Strafe admission uses that movement yaw.
            float targetYaw = (float) (Mth.atan2(target.getZ() - entity.getZ(), target.getX() - entity.getX())
                    * 180.0 / Math.PI) - 90.0F;
            entity.setYRot(Mth.approachDegrees(entity.getYRot(), targetYaw, LOOK_SPEED));
        }
        this.holdTicks++;
        if (shouldStartStrafe(this.holdTicks, this.strafeCooldown)) {
            tryStartStrafe(entity, target, visible, inRange, closeThreat, hazards);
        }
    }

    private void tryStartStrafe(
            E entity,
            LivingEntity target,
            boolean visible,
            boolean inRange,
            boolean closeThreat,
            List<? extends LivingEntity> hazards
    ) {
        if (!visible || !inRange || closeThreat) {
            return;
        }
        trackTarget(entity, target);
        entity.lookAt(target, LOOK_SPEED, LOOK_SPEED);
        float targetYaw = (float) (Mth.atan2(target.getZ() - entity.getZ(), target.getX() - entity.getX())
                * 180.0 / Math.PI) - 90.0F;
        if (Math.abs(Mth.wrapDegrees(targetYaw - entity.getYRot())) > 10.0F) {
            return;
        }
        float direction = RangedCombatPositioning.findBestStrafeDirection(entity, target, hazards);
        if (direction == 0.0F) {
            this.strafeCooldown = nextStrafeCooldown(entity);
            logMovementIntent(entity, "strafe_skip", "no_safe_side", null);
            this.blockedStrafeAttempts++;
            return;
        }

        this.holdTicks = 0;
        this.strafeDirection = direction;
        this.strafeTicksRemaining = MIN_STRAFE_TICKS
                + entity.getRandom().nextInt(MAX_STRAFE_TICKS - MIN_STRAFE_TICKS + 1);
        this.strafeTicksElapsed = 0;
        setState(entity, RangedCombatState.STRAFE);
        logMovementIntent(entity, "strafe_start", direction > 0.0F ? "right" : "left", null);
        continueStrafe(entity, target, visible, inRange, closeThreat, hazards);
    }

    private void continueStrafe(
            E entity,
            LivingEntity target,
            boolean visible,
            boolean inRange,
            boolean closeThreat,
            List<? extends LivingEntity> hazards
    ) {
        if (this.strafeTicksRemaining <= 0) {
            finishStrafe(entity, "duration_complete");
            return;
        }

        trackTarget(entity, target);
        // Startup already selected a side at this tick's heading. Turning twice
        // would validate a different first step from the one just selected.
        if (this.strafeTicksElapsed > 0) {
            entity.lookAt(target, LOOK_SPEED, LOOK_SPEED);
        }

        Vec3 landing = RangedCombatPositioning.findSafeStrafeLanding(
                entity,
                this.strafeDirection,
                hazards
        );
        boolean sideBlocked = landing == null;
        boolean changingHeight = landing != null && (!entity.onGround() || Math.abs(landing.y - entity.getY()) > 0.05);
        boolean collided = this.strafeTicksElapsed > 0 && !changingHeight
                && (entity.horizontalCollision || entity.minorHorizontalCollision);
        boolean stalled = this.strafeTicksElapsed > 3
                && !changingHeight
                && entity.onGround()
                && entity.getDeltaMovement().horizontalDistanceSqr() < STUCK_HORIZONTAL_SPEED_SQUARED;
        String cancelReason = strafeCancelReason(visible || this.seeTime >= -STRAFE_LOST_SIGHT_GRACE,
                inRange, closeThreat, collided, stalled);
        if (sideBlocked) {
            cancelReason = "unsafe_side_step";
        } else if (visible && !RangedCombatPositioning.hasVisibleStrafeStep(entity, target, landing)) {
            // Grace covers an unexpected brief occlusion, not a deliberate step
            // out of the only known firing slit while the current ray is clear.
            cancelReason = "firing_lane_edge";
        }
        if (cancelReason != null) {
            finishStrafe(entity, cancelReason);
            return;
        }

        ((MCAMoveControl)entity.getMoveControl()).strafe(
                0.0F,
                this.strafeDirection * STRAFE_INPUT,
                SPEED_MODIFIER
        );
        this.strafeTicksElapsed++;
        this.strafeTicksRemaining--;
    }

    private void finishStrafe(E entity, String reason) {
        if (this.strafeDirection != 0.0F || this.strafeTicksElapsed > 0 || this.strafeTicksRemaining > 0) {
            ((MCAMoveControl) entity.getMoveControl()).stopStrafing();
            this.strafeCooldown = nextStrafeCooldown(entity);
            if ("duration_complete".equals(reason)) {
                this.blockedStrafeAttempts = 0;
            } else if ("unsafe_side_step".equals(reason) || "firing_lane_edge".equals(reason)
                    || "physical_collision".equals(reason) || "stalled".equals(reason)) {
                this.blockedStrafeAttempts++;
            }
            logMovementIntent(entity, "strafe_stop", reason, null);
        }
        this.strafeDirection = 0.0F;
        this.strafeTicksElapsed = 0;
        this.strafeTicksRemaining = 0;
        this.holdTicks = 0;
        setState(entity, RangedCombatState.HOLD);
    }

    private void publishApproach(
            E entity,
            LivingEntity target,
            List<? extends LivingEntity> hazards
    ) {
        if (!canPublishCombatWalkTarget(entity)) {
            return;
        }

        List<? extends LivingEntity> secondaryHazards = hazards.stream()
                .filter(hazard -> hazard != target)
                .toList();
        publishApproachTarget(entity, target, secondaryHazards, "approach_target", "approach_blocked_by_hazard");
    }

    private void publishReposition(
            E entity,
            LivingEntity target,
            RangedCombatPositioning.ThreatContext threats,
            double attackRangeSquared
    ) {
        if (!canPublishCombatWalkTarget(entity)) {
            return;
        }

        Optional<MultiTargetPositionTracker> firingTarget = RangedCombatPositioning.findRepositionTarget(
                entity, target, threats, attackRangeSquared);
        if (firingTarget.isEmpty()) {
            scheduleWalkTargetRetry(entity);
            logMovementIntent(entity, "hold", "no_safe_firing_position", null);
            return;
        }

        if (firingTarget.orElseThrow() instanceof CombatEscapePositionTracker) {
            setState(entity, RangedCombatState.RECOVER);
        }
        WalkTarget walkTarget = new WalkTarget(firingTarget.orElseThrow(), (float)SPEED_MODIFIER, 0);
        setCombatWalkTarget(entity, walkTarget);
        logMovementIntent(entity, "walk", "reposition_firing_position", walkTarget);
    }

    private boolean publishRecovery(E entity, LivingEntity target,
                                    RangedCombatPositioning.ThreatContext threats,
                                    double attackRangeSquared, String reason) {
        // Seeking more room is optional; finish a usable draw before leaving.
        if (!entity.onGround() || entity.isUsingItem() || !canPublishCombatWalkTarget(entity)) {
            return false;
        }
        var recovery = RangedCombatPositioning.findRoomierTarget(entity, target, threats, attackRangeSquared);
        if (recovery.isEmpty()) {
            scheduleWalkTargetRetry(entity);
            return false;
        }
        setState(entity, RangedCombatState.RECOVER);
        WalkTarget walk = new WalkTarget(recovery.orElseThrow(), (float) SPEED_MODIFIER, 0);
        setCombatWalkTarget(entity, walk);
        logMovementIntent(entity, "walk", reason, walk);
        return true;
    }

    private void publishApproachTarget(
            E entity,
            LivingEntity target,
            List<? extends LivingEntity> secondaryHazards,
            String movementReason,
            String blockedReason
    ) {
        if (secondaryHazards.isEmpty()) {
            WalkTarget walkTarget = new WalkTarget(new EntityTracker(target, false), (float)SPEED_MODIFIER, 0);
            setCombatWalkTarget(entity, walkTarget);
            logMovementIntent(entity, "walk", movementReason, walkTarget);
            return;
        }

        Optional<Vec3> waypoint = RangedCombatPositioning.findApproachPosition(entity, target, secondaryHazards);
        if (waypoint.isEmpty()) {
            clearCombatWalkTarget(entity);
            scheduleWalkTargetRetry(entity);
            logMovementIntent(entity, "hold", blockedReason, null);
            return;
        }

        WalkTarget walkTarget = new WalkTarget(waypoint.orElseThrow(), (float)SPEED_MODIFIER, 0);
        setCombatWalkTarget(entity, walkTarget);
        logMovementIntent(entity, "walk", movementReason + "_waypoint", walkTarget);
    }

    private void publishKiteAway(
            E entity,
            RangedCombatPositioning.ThreatContext threatContext,
            LivingEntity target,
            double attackRangeSquared,
            boolean rangedEvasion
    ) {
        if (!canPublishCombatWalkTarget(entity)) {
            return;
        }
        publishGroupAway(
                entity,
                rangedEvasion
                        ? RangedCombatPositioning.findRangedEvasionTarget(
                                entity, target, threatContext, KITE_SAFE_DISTANCE, attackRangeSquared)
                        : RangedCombatPositioning.findGroupEscapeTarget(
                        entity,
                        threatContext.escapeThreats(),
                        threatContext.hazards(),
                        KITE_SAFE_DISTANCE
                ),
                KITE_RETREAT_SPEED_MODIFIER
        );
    }

    private void publishEmergencyAway(
            E entity,
            RangedCombatPositioning.ThreatContext threatContext
    ) {
        if (!canPublishCombatWalkTarget(entity)) {
            return;
        }
        publishGroupAway(
                entity,
                RangedCombatPositioning.findEmergencyEscapeTarget(
                        entity,
                        threatContext.escapeThreats(),
                        threatContext.hazards(),
                        EMERGENCY_SAFE_DISTANCE
                ),
                EMERGENCY_RETREAT_SPEED_MODIFIER
        );
    }

    private void publishGroupAway(
            E entity,
            Optional<? extends PositionTracker> awayTarget,
            double speedModifier
    ) {
        if (awayTarget.isEmpty()) {
            if (this.combatWalkTarget != null) {
                scheduleWalkTargetRetry(entity);
                logMovementIntent(entity, "keep_walk", "retreat_replan_unavailable", this.combatWalkTarget);
                return;
            }
            clearCombatWalkTarget(entity);
            scheduleWalkTargetRetry(entity);
            logMovementIntent(entity, "hold", "no_group_escape_position", null);
            return;
        }

        WalkTarget walkTarget = new WalkTarget(awayTarget.orElseThrow(), (float)speedModifier, 0);
        setCombatWalkTarget(entity, walkTarget);
        logMovementIntent(entity, "walk", "away_from_threat_group", walkTarget);
    }

    private static boolean isRetreatState(RangedCombatState state) {
        return state == RangedCombatState.KITE || state == RangedCombatState.EMERGENCY_FLEE;
    }

    private boolean ownsCombatWalkTarget(E entity) {
        return this.combatWalkTarget != null
                && entity.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == this.combatWalkTarget;
    }

    private boolean canPublishCombatWalkTarget(E entity) {
        return !entity.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                && !entity.getBrain().hasMemoryValue(MemoryModuleType.PATH)
                && this.walkTargetRetryCooldown <= 0;
    }

    private void setCombatWalkTarget(E entity, WalkTarget walkTarget) {
        entity.getBrain().setMemory(MemoryModuleType.WALK_TARGET, walkTarget);
        this.combatWalkTarget = walkTarget;
        this.blockedStrafeAttempts = 0;
        this.lastEscapeRouteCheck = Long.MIN_VALUE;
        scheduleWalkTargetRetry(entity);
    }

    private void revalidateOwnedEscape(E entity, long gameTime) {
        if (!ownsCombatWalkTarget(entity)
                || !(this.combatWalkTarget.getTarget() instanceof CombatEscapePositionTracker escape)) {
            return;
        }

        Path path = entity.getNavigation().getPath();
        if (path == null || entity.getNavigation().isDone()
                || entity.getBrain().getMemory(MemoryModuleType.PATH).orElse(null) != path) {
            return;
        }

        if (this.lastEscapeRouteCheck != Long.MIN_VALUE && gameTime - this.lastEscapeRouteCheck < 5) {
            return;
        }
        this.lastEscapeRouteCheck = gameTime;
        if (escape.isPathSafe(entity, path) || escape.isDegradedEmergencyPathSafe(entity, path)) {
            return;
        }

        // Both the published walk intent and the native path still belong to this
        // behavior. Never stop an unrelated PATH or erase a foreign WALK_TARGET.
        if (ownsCombatWalkTarget(entity)
                && entity.getNavigation().getPath() == path
                && entity.getBrain().getMemory(MemoryModuleType.PATH).orElse(null) == path) {
            entity.getNavigation().stop();
            entity.getBrain().eraseMemory(MemoryModuleType.PATH);
            clearCombatWalkTarget(entity);
            // Leave one tick for MoveToTargetSink.stop to release its own stale path
            // before the next tactical intent is published.
            this.walkTargetRetryCooldown = 2;
            logMovementIntent(entity, "cancel_walk", "escape_route_became_unsafe", null);
        }
    }

    private void scheduleWalkTargetRetry(E entity) {
        this.walkTargetRetryCooldown = 10 + entity.getRandom().nextInt(10);
    }

    private void clearCombatWalkTarget(E entity) {
        if (this.combatWalkTarget == null) {
            return;
        }

        WalkTarget current = entity.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (current == this.combatWalkTarget) {
            entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
        this.combatWalkTarget = null;
        this.lastEscapeRouteCheck = Long.MIN_VALUE;
        this.walkTargetRetryCooldown = 0;
    }

    private void observeWalkTargetLifecycle(E entity) {
        if (this.combatWalkTarget == null) {
            return;
        }

        WalkTarget current = entity.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (current != this.combatWalkTarget) {
            this.combatWalkTarget = null;
            this.lastEscapeRouteCheck = Long.MIN_VALUE;
        }
    }

    private boolean abandonUnreachableTarget(E entity, LivingEntity target, long gameTime, boolean canShoot) {
        RangedCombatState state = RangedCombatState.current(entity).orElse(null);
        WalkTarget walk = entity.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        Path path = entity.getNavigation().getPath();
        boolean seeking = state == RangedCombatState.APPROACH || state == RangedCombatState.REPOSITION;
        boolean foreignWalk = walk != null && walk != this.combatWalkTarget;
        if (canShoot || !seeking || foreignWalk || entity.position().distanceToSqr(this.progressPosition) >= 1.0
                || target.position().distanceToSqr(this.progressTargetPosition) >= 4.0
                || path != null && path.canReach() && !entity.getNavigation().isDone()) {
            this.progressPosition = entity.position();
            this.progressTargetPosition = target.position();
            this.lastProgressTime = gameTime;
            this.failedCombatRoute = false;
        }
        if (seeking && !foreignWalk && this.combatWalkTarget != null
                && WalkTargetFailureMemory.hasFailureFor(entity, this.combatWalkTarget.getTarget().currentBlockPosition())) {
            this.failedCombatRoute = true;
        }
        if (!canShoot && seeking && !foreignWalk && this.failedCombatRoute
                && gameTime - this.lastProgressTime >= FAILED_ROUTE_STALL_TICKS && entity.getNavigation().isDone()) {
            UnreachableCombatTargets.remember(entity, target, gameTime);
            logMovementIntent(entity, "abandon_target", "failed_routes_without_progress", this.combatWalkTarget);
            clearCombatState(entity);
            entity.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            if (entity.getBrain().isMemoryValue(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY, target)) {
                entity.getBrain().eraseMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY);
            }
            return true;
        }
        return false;
    }

    private void tickCooldowns() {
        if (this.walkTargetRetryCooldown > 0) {
            this.walkTargetRetryCooldown--;
        }
        if (this.strafeCooldown > 0) {
            this.strafeCooldown--;
        }
    }

    private void clearCombatState(E entity) {
        if (this.strafeDirection != 0.0F) {
            ((MCAMoveControl) entity.getMoveControl()).stopStrafing();
        }
        clearCombatWalkTarget(entity);
        if (entity.getBrain().hasMemoryValue(MemoryModuleTypeMCA.RANGED_COMBAT_STATE)) {
            entity.getBrain().eraseMemory(MemoryModuleTypeMCA.RANGED_COMBAT_STATE);
            entity.setAggressive(false);
        }
        this.strafeDirection = 0.0F;
        this.strafeTicksElapsed = 0;
        this.strafeTicksRemaining = 0;
    }

    private void resetLocalState() {
        this.lastTarget = null;
        this.walkTargetRetryCooldown = 0;
        this.combatWalkTarget = null;
        this.lastEscapeRouteCheck = Long.MIN_VALUE;
        resetTacticalTimers();
        this.lastDebugState = "";
        this.lastDebugLogTime = Long.MIN_VALUE;
    }

    private void resetTacticalTimers() {
        this.seeTime = 0;
        this.blockedShotTicks = 0;
        this.clearShotTicks = 0;
        this.lostContactTicks = 0;
        this.holdTicks = 0;
        this.strafeCooldown = 0;
        this.strafeTicksRemaining = 0;
        this.strafeTicksElapsed = 0;
        this.strafeDirection = 0.0F;
        this.blockedStrafeAttempts = 0;
        this.failedCombatRoute = false;
    }

    private void setState(E entity, RangedCombatState state) {
        if (RangedCombatState.current(entity).orElse(null) != state) {
            entity.getBrain().setMemory(MemoryModuleTypeMCA.RANGED_COMBAT_STATE, state);
        }
        entity.setAggressive(state.permitsAiming(entity));
    }

    private void trackTarget(E entity, LivingEntity target) {
        entity.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        entity.getLookControl().setLookAt(target, LOOK_SPEED, LOOK_SPEED);
    }

    private void trackEscapeOrTarget(E entity, LivingEntity target) {
        if (this.combatWalkTarget == null) {
            trackTarget(entity, target);
            return;
        }

        PositionTracker escapeTarget = this.combatWalkTarget.getTarget();
        entity.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, escapeTarget);
        Vec3 position = escapeTarget.currentPosition();
        entity.getLookControl().setLookAt(position.x, position.y, position.z, LOOK_SPEED, LOOK_SPEED);
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

    private static int nextStrafeCooldown(VillagerEntityMCA entity) {
        return MIN_STRAFE_COOLDOWN + entity.getRandom().nextInt(MAX_STRAFE_COOLDOWN - MIN_STRAFE_COOLDOWN + 1);
    }

    private static String strafeCancelReason(boolean visible, boolean inRange, boolean closeThreat, boolean collided, boolean stalled) {
        if (!visible) {
            return "eye_los_lost";
        }
        if (!inRange) {
            return "out_of_range";
        }
        if (closeThreat) {
            return "close_threat";
        }
        if (collided) {
            return "physical_collision";
        }
        return stalled ? "stalled" : null;
    }

    private void logStateTransition(
            E entity,
            LivingEntity target,
            LivingEntity movementThreat,
            RangedCombatState previousState,
            RangedCombatState nextState,
            String reason,
            boolean targetChanged,
            double targetDistanceSquared,
            double threatDistanceSquared,
            boolean visible
    ) {
        MCA.LOGGER.info(
                "[MCA Archer Decision] entity={} previous={} next={} reason={} targetChanged={} target={} threat={} targetDistSqr={} threatDistSqr={} los={} seeTime={} blockedShotTicks={} clearShotTicks={}",
                entity.getStringUUID(),
                previousState,
                nextState,
                reason,
                targetChanged,
                target.getStringUUID(),
                movementThreat.getStringUUID(),
                String.format("%.2f", targetDistanceSquared),
                String.format("%.2f", threatDistanceSquared),
                visible,
                this.seeTime,
                this.blockedShotTicks,
                this.clearShotTicks
        );
    }

    private void logMovementIntent(E entity, String intent, String reason, WalkTarget walkTarget) {
        if (!MCA.platformHelper.isDevelopmentEnvironment()) {
            return;
        }

        Vec3 destination = walkTarget == null ? null : walkTarget.getTarget().currentPosition();
        MCA.LOGGER.info(
                "[MCA Archer Intent] entity={} movement={} intent={} reason={} destination={} walkRetryCooldown={} navDone={}",
                entity.getStringUUID(),
                RangedCombatState.current(entity).orElse(null),
                intent,
                reason,
                destination,
                this.walkTargetRetryCooldown,
                entity.getNavigation().isDone()
        );
    }

    private void logDebugState(
            ServerLevel level,
            E entity,
            LivingEntity target,
            LivingEntity movementThreat,
            boolean visible,
            double targetDistanceSquared,
            double threatDistanceSquared,
            double threatVerticalDistance
    ) {
        RangedCombatState state = RangedCombatState.current(entity).orElse(null);
        String stateKey = target.getUUID() + ":" + movementThreat.getUUID() + ":" + state + ":" + visible;
        long gameTime = level.getGameTime();
        if (stateKey.equals(this.lastDebugState) && gameTime - this.lastDebugLogTime < DEBUG_LOG_INTERVAL_TICKS) {
            return;
        }

        this.lastDebugState = stateKey;
        this.lastDebugLogTime = gameTime;
        WalkTarget walk = entity.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        Path path = entity.getNavigation().getPath();
        MCA.LOGGER.info(
                "[MCA Archer Movement] entity={} entityName=\"{}\" target={} targetName=\"{}\" threat={} threatName=\"{}\" movement={} targetDistSqr={} threatDistSqr={} threatVerticalDist={} los={} seeTime={} holdTicks={} strafeCooldown={} strafeTicksRemaining={} strafeTicksElapsed={} strafeDirection={} walkRetryCooldown={} hasCombatWalkTarget={} navDone={} horizontalCollision={} minorHorizontalCollision={} onGround={} yRot={} yHeadRot={} yBodyRot={} movementSpeed={} deltaMovement={} pos={} targetPos={} threatPos={} walkTracker={} walkDestination={} pathReachable={} health={}",
                entity.getStringUUID(),
                entity.getName().getString(),
                target.getStringUUID(),
                target.getName().getString(),
                movementThreat.getStringUUID(),
                movementThreat.getName().getString(),
                state,
                String.format("%.2f", targetDistanceSquared),
                String.format("%.2f", threatDistanceSquared),
                String.format("%.2f", threatVerticalDistance),
                visible,
                this.seeTime,
                this.holdTicks,
                this.strafeCooldown,
                this.strafeTicksRemaining,
                this.strafeTicksElapsed,
                this.strafeDirection,
                this.walkTargetRetryCooldown,
                this.combatWalkTarget != null,
                entity.getNavigation().isDone(),
                entity.horizontalCollision,
                entity.minorHorizontalCollision,
                entity.onGround(),
                String.format("%.2f", entity.getYRot()),
                String.format("%.2f", entity.yHeadRot),
                String.format("%.2f", entity.yBodyRot),
                String.format("%.4f", entity.getAttributeValue(Attributes.MOVEMENT_SPEED)),
                entity.getDeltaMovement(),
                entity.blockPosition(),
                target.blockPosition(),
                movementThreat.blockPosition(),
                walk == null ? "none" : walk.getTarget().getClass().getSimpleName(),
                walk == null ? null : walk.getTarget().currentPosition(),
                path == null ? null : path.canReach(),
                entity.getHealth()
        );
    }

    private static LivingEntity getAttackTarget(LivingEntity entity) {
        return entity.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    private record BaseStateDecision(RangedCombatState state, String reason) {
    }
}
