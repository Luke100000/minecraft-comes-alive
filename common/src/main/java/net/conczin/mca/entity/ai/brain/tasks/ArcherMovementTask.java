package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MCAMoveControl;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.RangedWeaponHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public class ArcherMovementTask<E extends VillagerEntityMCA> extends Behavior<E> {
    private static final double SPEED_MODIFIER = 0.5D;
    private static final double EMERGENCY_SPEED_MODIFIER = 1.2D;
    private static final float LOOK_SPEED = 30.0F;
    private static final int LOST_SIGHT_BEFORE_REPOSITION = 10;
    private static final int DEBUG_LOG_INTERVAL_TICKS = 20;

    static final double EMERGENCY_ENTER_DISTANCE_SQUARED = 12.25D;
    private static final double EMERGENCY_EXIT_DISTANCE_SQUARED = 25.0D;
    static final double KITE_ENTER_DISTANCE_SQUARED = 36.0D;
    private static final double KITE_EXIT_DISTANCE_SQUARED = 81.0D;
    static final double CLOSE_RANGE_VERTICAL_THREAT_DISTANCE = 2.5D;
    private static final double KITE_SAFE_DISTANCE = 9.0D;
    private static final double EMERGENCY_SAFE_DISTANCE = 6.0D;
    private static final double APPROACH_EXIT_RANGE_BUFFER = 1.0D;

    private static final int MIN_HOLD_TICKS_BEFORE_STRAFE = 10;
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
            int seeTime
    ) {
        return selectBaseDecision(
                currentState,
                targetDistanceSquared,
                threatDistanceSquared,
                threatVerticalDistance,
                attackRangeSquared,
                seeTime
        ).state();
    }

    private static BaseStateDecision selectBaseDecision(
            RangedCombatState currentState,
            double targetDistanceSquared,
            double threatDistanceSquared,
            double threatVerticalDistance,
            double attackRangeSquared,
            int seeTime
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
            return new BaseStateDecision(RangedCombatState.REPOSITION, "lost_los");
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
    private int walkTargetRetryCooldown;
    private WalkTarget combatWalkTarget;

    private int holdTicks;
    private int strafeCooldown;
    private int strafeTicksRemaining;
    private int strafeTicksElapsed;
    private float strafeDirection;

    private long lastDebugLogTime = Long.MIN_VALUE;
    private String lastDebugState = "";

    public ArcherMovementTask(int maximumRange) {
        super(ImmutableMap.of(
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.REGISTERED
        ), 1200);
        this.maximumRangeSquared = maximumRange * maximumRange;
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
        observeWalkTargetLifecycle(entity);

        boolean targetChanged = target != this.lastTarget;
        if (targetChanged) {
            clearCombatWalkTarget(entity);
            resetTacticalTimers();
            entity.getBrain().eraseMemory(MemoryModuleTypeMCA.RANGED_COMBAT_STATE);
            this.lastTarget = target;
        }

        boolean visible = entity.getSensing().hasLineOfSight(target);
        updateSeeTime(visible);

        RangedCombatPositioning.ThreatContext threatContext =
                RangedCombatPositioning.collectThreatContext(entity, target);
        LivingEntity movementThreat = threatContext.nearestEscapeThreat();
        if (movementThreat == null) {
            movementThreat = target;
        }
        double targetDistanceSquared = entity.distanceToSqr(target);
        double threatDistanceSquared = entity.distanceToSqr(movementThreat);
        double threatVerticalDistance = Math.abs(entity.getY() - movementThreat.getY());
        double attackRangeSquared = RangedWeaponHelper.getAttackRangeSquared(entity, this.maximumRangeSquared);

        RangedCombatState currentState = RangedCombatState.current(entity).orElse(RangedCombatState.HOLD);
        RangedCombatState hysteresisState = currentState == RangedCombatState.STRAFE
                ? RangedCombatState.HOLD
                : currentState;
        BaseStateDecision baseDecision = selectBaseDecision(
                hysteresisState,
                targetDistanceSquared,
                threatDistanceSquared,
                threatVerticalDistance,
                attackRangeSquared,
                this.seeTime
        );
        RangedCombatState baseState = baseDecision.state();

        boolean closeThreat = threatVerticalDistance <= CLOSE_RANGE_VERTICAL_THREAT_DISTANCE
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
                case REPOSITION -> {
                    trackTarget(entity, target);
                    publishReposition(
                            entity,
                            target,
                            threatContext.hazards(),
                            attackRangeSquared
                    );
                }
                case KITE -> {
                    trackTarget(entity, target);
                    publishKiteAway(entity, threatContext);
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
                    threatContext.hazards()
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
            List<? extends LivingEntity> hazards
    ) {
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

        setState(entity, RangedCombatState.HOLD);
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
        trackTarget(entity, target);
        entity.lookAt(target, LOOK_SPEED, LOOK_SPEED);
        float direction = RangedCombatPositioning.findBestStrafeDirection(entity, hazards);
        if (direction == 0.0F) {
            this.strafeCooldown = nextStrafeCooldown(entity);
            logMovementIntent(entity, "strafe_skip", "no_safe_side", null);
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
        entity.lookAt(target, LOOK_SPEED, LOOK_SPEED);

        boolean sideBlocked = !RangedCombatPositioning.isImmediateStrafeStepWalkable(
                entity,
                this.strafeDirection,
                hazards
        );
        boolean collided = entity.horizontalCollision || entity.minorHorizontalCollision || sideBlocked;
        boolean stalled = this.strafeTicksElapsed > 3
                && entity.onGround()
                && entity.getDeltaMovement().horizontalDistanceSqr() < STUCK_HORIZONTAL_SPEED_SQUARED;
        String cancelReason = strafeCancelReason(visible, inRange, closeThreat, collided, stalled);
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
            this.strafeCooldown = nextStrafeCooldown(entity);
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
            List<? extends LivingEntity> hazards,
            double attackRangeSquared
    ) {
        if (!canPublishCombatWalkTarget(entity)) {
            return;
        }

        Optional<Vec3> firingPosition = RangedCombatPositioning.findFiringPosition(
                entity,
                target,
                hazards,
                attackRangeSquared
        );
        if (firingPosition.isEmpty()) {
            List<? extends LivingEntity> secondaryHazards = hazards.stream()
                    .filter(hazard -> hazard != target)
                    .toList();
            publishApproachTarget(
                    entity,
                    target,
                    secondaryHazards,
                    "reposition_approach_fallback",
                    "reposition_blocked_by_hazard"
            );
            return;
        }

        WalkTarget walkTarget = new WalkTarget(firingPosition.orElseThrow(), (float)SPEED_MODIFIER, 0);
        setCombatWalkTarget(entity, walkTarget);
        logMovementIntent(entity, "walk", "reposition_firing_position", walkTarget);
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
            RangedCombatPositioning.ThreatContext threatContext
    ) {
        if (!canPublishCombatWalkTarget(entity)) {
            return;
        }
        publishGroupAway(
                entity,
                RangedCombatPositioning.findGroupEscapeTarget(
                        entity,
                        threatContext.escapeThreats(),
                        threatContext.hazards(),
                        KITE_SAFE_DISTANCE
                ),
                SPEED_MODIFIER
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
                EMERGENCY_SPEED_MODIFIER
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
        scheduleWalkTargetRetry(entity);
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
        this.walkTargetRetryCooldown = 0;
    }

    private void observeWalkTargetLifecycle(E entity) {
        if (this.combatWalkTarget == null) {
            return;
        }

        WalkTarget current = entity.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (current != this.combatWalkTarget) {
            this.combatWalkTarget = null;
        }
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
        clearCombatWalkTarget(entity);
        entity.getBrain().eraseMemory(MemoryModuleTypeMCA.RANGED_COMBAT_STATE);
        this.strafeDirection = 0.0F;
        this.strafeTicksElapsed = 0;
        this.strafeTicksRemaining = 0;
    }

    private void resetLocalState() {
        this.lastTarget = null;
        this.walkTargetRetryCooldown = 0;
        this.combatWalkTarget = null;
        resetTacticalTimers();
        this.lastDebugState = "";
        this.lastDebugLogTime = Long.MIN_VALUE;
    }

    private void resetTacticalTimers() {
        this.seeTime = 0;
        this.holdTicks = 0;
        this.strafeCooldown = 0;
        this.strafeTicksRemaining = 0;
        this.strafeTicksElapsed = 0;
        this.strafeDirection = 0.0F;
    }

    private void setState(E entity, RangedCombatState state) {
        if (RangedCombatState.current(entity).orElse(null) != state) {
            entity.getBrain().setMemory(MemoryModuleTypeMCA.RANGED_COMBAT_STATE, state);
        }
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
            return "lost_los";
        }
        if (!inRange) {
            return "out_of_range";
        }
        if (closeThreat) {
            return "close_threat";
        }
        if (collided) {
            return "collision";
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
                "[MCA Archer Decision] entity={} previous={} next={} reason={} targetChanged={} target={} threat={} targetDistSqr={} threatDistSqr={} los={} seeTime={}",
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
                this.seeTime
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
        MCA.LOGGER.info(
                "[MCA Archer Movement] entity={} entityName=\"{}\" target={} targetName=\"{}\" threat={} threatName=\"{}\" movement={} targetDistSqr={} threatDistSqr={} threatVerticalDist={} los={} seeTime={} holdTicks={} strafeCooldown={} strafeTicksRemaining={} strafeTicksElapsed={} strafeDirection={} walkRetryCooldown={} hasCombatWalkTarget={} navDone={} horizontalCollision={} minorHorizontalCollision={} onGround={} yRot={} yHeadRot={} yBodyRot={} movementSpeed={} deltaMovement={} pos={} targetPos={} threatPos={}",
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
                movementThreat.blockPosition()
        );
    }

    private static LivingEntity getAttackTarget(LivingEntity entity) {
        return entity.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    private record BaseStateDecision(RangedCombatState state, String reason) {
    }
}
