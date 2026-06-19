package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.ArcherMoveControl;
import net.conczin.mca.entity.ai.brain.sensor.GuardEnemiesSensor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.item.BowItem;

import java.util.Comparator;
import java.util.Optional;

public class ArcherMovementTask<E extends PathfinderMob> extends Behavior<E> {
    private static final double SPEED_MODIFIER = 0.5;
    private static final double RETREAT_SPEED_MODIFIER = 0.25;
    private static final float STRAFE_SPEED = -0.5F;
    private static final float LOOK_SPEED = 30.0F;
    private static final int LOST_SIGHT_BEFORE_APPROACH = 10;

    private final double maximumRangeSquared;
    private final double retreatDistanceSquared;
    private Mode mode;
    private LivingEntity lastTarget;
    private LivingEntity retreatTarget;
    private String movementMode = "idle";
    private int seeTime;
    private int repathCooldown;
    private long lastDebugLogTime = Long.MIN_VALUE;
    private String lastDebugState = "";

    public ArcherMovementTask(int maximumRange, int retreatDistance) {
        super(ImmutableMap.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT), 1200);
        this.maximumRangeSquared = maximumRange * maximumRange;
        this.retreatDistanceSquared = retreatDistance * retreatDistance;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, E entity) {
        return hasValidTarget(getAttackTarget(entity)) && isHoldingBow(entity);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return hasValidTarget(getAttackTarget(entity)) && isHoldingBow(entity);
    }

    @Override
    protected void start(ServerLevel level, E entity, long gameTime) {
        this.mode = null;
        this.lastTarget = null;
        this.retreatTarget = null;
        this.movementMode = "idle";
        this.seeTime = 0;
        this.repathCooldown = 0;
        this.lastDebugState = "";
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        entity.getNavigation().stop();
    }

    @Override
    protected void tick(ServerLevel level, E entity, long gameTime) {
        LivingEntity target = getAttackTarget(entity);
        if (!hasValidTarget(target)) {
            return;
        }

        if (target != this.lastTarget) {
            this.lastTarget = target;
            this.mode = null;
            this.seeTime = 0;
            this.repathCooldown = 0;
            entity.getNavigation().stop();
        }

        boolean visible = entity.getSensing().hasLineOfSight(target);
        updateSeeTime(visible);

        double distanceSquared = entity.distanceToSqr(target);
        LivingEntity nextRetreatTarget = findRetreatTarget(entity);
        Mode nextMode = selectMode(entity, distanceSquared, nextRetreatTarget);
        if (nextMode != this.mode) {
            entity.getNavigation().stop();
            this.repathCooldown = 0;
            this.mode = nextMode;
        }
        this.retreatTarget = nextMode == Mode.RETREAT ? nextRetreatTarget : null;

        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        LivingEntity lookTarget = this.retreatTarget == null ? target : this.retreatTarget;
        entity.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(lookTarget, true));

        switch (this.mode) {
            case APPROACH -> {
                entity.getLookControl().setLookAt(target, LOOK_SPEED, LOOK_SPEED);
                approach(entity, target);
            }
            case HOLD -> hold(entity, target);
            case RETREAT -> retreat(entity, this.retreatTarget == null ? target : this.retreatTarget);
        }

        if (MCA.platformHelper.isDevelopmentEnvironment()) {
            logDebugState(level, entity, target, this.retreatTarget, visible, distanceSquared);
        }
    }

    @Override
    protected void stop(ServerLevel level, E entity, long gameTime) {
        this.mode = null;
        this.lastTarget = null;
        this.retreatTarget = null;
        this.movementMode = "idle";
        this.seeTime = 0;
        this.repathCooldown = 0;
        this.lastDebugState = "";
        entity.getNavigation().stop();
    }

    private Mode selectMode(E entity, double distanceSquared, LivingEntity closeThreat) {
        if (closeThreat != null || this.mode == Mode.RETREAT && !entity.onGround()) {
            return Mode.RETREAT;
        }

        if (distanceSquared > this.maximumRangeSquared || this.seeTime < -LOST_SIGHT_BEFORE_APPROACH) {
            return Mode.APPROACH;
        }

        return Mode.HOLD;
    }

    private void approach(E entity, LivingEntity target) {
        this.movementMode = "approach";
        if (--this.repathCooldown > 0 && !entity.getNavigation().isDone()) {
            return;
        }

        entity.getNavigation().moveTo(target, SPEED_MODIFIER);
        this.repathCooldown = 10 + entity.getRandom().nextInt(10);
    }

    private void hold(E entity, LivingEntity target) {
        this.movementMode = "hold";
        entity.getNavigation().stop();
        getArcherMoveControl(entity).face(target);
        entity.getLookControl().setLookAt(target, LOOK_SPEED, LOOK_SPEED);
    }

    private void retreat(E entity, LivingEntity target) {
        this.movementMode = "back_strafe";
        entity.getNavigation().stop();
        getArcherMoveControl(entity).retreatFrom(target, STRAFE_SPEED, RETREAT_SPEED_MODIFIER);
        entity.getLookControl().setLookAt(target, LOOK_SPEED, LOOK_SPEED);
        this.repathCooldown = 0;
    }

    private LivingEntity findRetreatTarget(E entity) {
        Optional<NearestVisibleLivingEntities> visibleEntities = entity.getBrain().getMemoryInternal(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
        return visibleEntities.flatMap(entities -> entities.find(candidate -> isCloseThreat(entity, candidate, this.retreatDistanceSquared))
                        .min(Comparator.comparingDouble(entity::distanceToSqr)))
                .orElse(null);
    }

    private static boolean isCloseThreat(Mob entity, LivingEntity candidate, double maximumDistanceSquared) {
        return candidate != entity
                && hasValidTarget(candidate)
                && candidate.level() == entity.level()
                && entity.canAttack(candidate)
                && candidate.distanceToSqr(entity) <= maximumDistanceSquared
                && GuardEnemiesSensor.isGuardEnemy(candidate, entity);
    }

    private static ArcherMoveControl getArcherMoveControl(Mob entity) {
        if (entity.getMoveControl() instanceof ArcherMoveControl archerMoveControl) {
            return archerMoveControl;
        }

        throw new IllegalStateException(entity.getType() + " must use ArcherMoveControl for archer movement");
    }

    private void updateSeeTime(boolean visible) {
        if (visible) {
            this.seeTime = Math.max(0, this.seeTime) + 1;
        } else {
            this.seeTime = Math.min(0, this.seeTime) - 1;
        }
    }

    private void logDebugState(ServerLevel level, E entity, LivingEntity target, LivingEntity closeThreat, boolean visible, double distanceSquared) {
        String state = this.mode + ":" + this.movementMode + ":" + visible + ":" + this.seeTime + ":" + this.repathCooldown + ":" + entity.getNavigation().isDone() + ":" + entity.horizontalCollision + ":" + entity.onGround() + ":" + getDebugName(closeThreat);
        long gameTime = level.getGameTime();
        if (state.equals(this.lastDebugState) && gameTime - this.lastDebugLogTime < 20) {
            return;
        }

        this.lastDebugState = state;
        this.lastDebugLogTime = gameTime;
        MCA.LOGGER.info(
                "[MCA Archer Movement] entity={} entityName=\"{}\" target={} targetName=\"{}\" closeThreat={} closeThreatName=\"{}\" mode={} movement={} distSqr={} closeThreatDistSqr={} los={} seeTime={} navDone={} repathCooldown={} horizontalCollision={} minorHorizontalCollision={} onGround={} yRot={} targetYRot={} closeThreatYRot={} yHeadRot={} yBodyRot={} movementSpeed={} deltaMovement={} pos={} targetPos={} closeThreatPos={}",
                entity.getStringUUID(),
                entity.getName().getString(),
                target.getStringUUID(),
                target.getName().getString(),
                closeThreat == null ? "none" : closeThreat.getStringUUID(),
                getDebugName(closeThreat),
                this.mode,
                this.movementMode,
                String.format("%.2f", distanceSquared),
                closeThreat == null ? "none" : String.format("%.2f", entity.distanceToSqr(closeThreat)),
                visible,
                this.seeTime,
                entity.getNavigation().isDone(),
                this.repathCooldown,
                entity.horizontalCollision,
                entity.minorHorizontalCollision,
                entity.onGround(),
                String.format("%.2f", entity.getYRot()),
                String.format("%.2f", getTargetYRot(entity, target)),
                closeThreat == null ? "none" : String.format("%.2f", getTargetYRot(entity, closeThreat)),
                String.format("%.2f", entity.yHeadRot),
                String.format("%.2f", entity.yBodyRot),
                String.format("%.4f", entity.getAttributeValue(Attributes.MOVEMENT_SPEED)),
                entity.getDeltaMovement(),
                entity.blockPosition(),
                target.blockPosition(),
                closeThreat == null ? "none" : closeThreat.blockPosition()
        );
    }

    private static String getDebugName(LivingEntity entity) {
        return entity == null ? "none" : entity.getName().getString();
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

    private static boolean isHoldingBow(Mob entity) {
        return entity.isHolding(stack -> stack.getItem() instanceof BowItem);
    }

    private enum Mode {
        APPROACH,
        HOLD,
        RETREAT
    }
}
