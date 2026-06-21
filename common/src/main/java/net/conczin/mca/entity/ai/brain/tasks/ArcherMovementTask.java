package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.ArcherMoveControl;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;

public class ArcherMovementTask<E extends PathfinderMob> extends Behavior<E> {
    private static final double SPEED_MODIFIER = 0.5;
    private static final double RETREAT_SPEED_MODIFIER = 0.45;
    private static final float STRAFE_SPEED = -0.5F;
    private static final float LOOK_SPEED = 30.0F;
    private static final int LOST_SIGHT_BEFORE_APPROACH = 10;
    private static final double RETREAT_ENTER_RANGE_FRACTION = 0.25;
    private static final double RETREAT_EXIT_RANGE_FRACTION = 0.36;

    private final double maximumRangeSquared;
    private LivingEntity lastTarget;
    private String movementMode = "idle";
    private int seeTime;
    private long lastDebugLogTime = Long.MIN_VALUE;
    private String lastDebugState = "";
    private boolean strafingClockwise;
    private boolean retreating;
    private boolean fleeing;
    private int strafingTime = -1;
    private int repathCooldown;

    public ArcherMovementTask(int maximumRange) {
        super(ImmutableMap.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT), 1200);
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
            this.strafingTime = -1;
            this.retreating = false;
            this.fleeing = false;
            getArcherMoveControl(entity).setFleeing(false);
            this.repathCooldown = 0;
            entity.getNavigation().stop();
        }

        boolean visible = entity.getSensing().hasLineOfSight(target);
        updateSeeTime(visible);

        double distanceSquared = entity.distanceToSqr(target);
        boolean isFleeing = shouldFlee(distanceSquared);
        getArcherMoveControl(entity).setFleeing(isFleeing);

        if (isFleeing) {
            flee(entity, target);
        } else {
            entity.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
            entity.getLookControl().setLookAt(target, LOOK_SPEED, LOOK_SPEED);
            if (distanceSquared > this.maximumRangeSquared || this.seeTime < -LOST_SIGHT_BEFORE_APPROACH) {
                approach(entity, target);
            } else if (visible) {
                if (shouldRetreat(distanceSquared)) {
                    retreat(entity, target);
                } else {
                    strafe(entity, target);
                }
            } else {
                hold(entity, target);
            }
        }

        if (MCA.platformHelper.isDevelopmentEnvironment()) {
            logDebugState(level, entity, target, visible, distanceSquared);
        }
    }

    @Override
    protected void stop(ServerLevel level, E entity, long gameTime) {
        resetState();
        getArcherMoveControl(entity).setFleeing(false);
        entity.getNavigation().stop();
    }

    private void resetState() {
        this.lastTarget = null;
        this.movementMode = "idle";
        this.seeTime = 0;
        this.lastDebugState = "";
        this.strafingClockwise = false;
        this.retreating = false;
        this.fleeing = false;
        this.strafingTime = -1;
        this.repathCooldown = 0;
    }

    private boolean shouldFlee(double distanceSquared) {
        this.fleeing = this.fleeing ? distanceSquared < 64.0 : distanceSquared < 25.0;
        return this.fleeing;
    }

    private void flee(E entity, LivingEntity target) {
        boolean changedMode = !"flee".equals(this.movementMode);
        this.movementMode = "flee";
        this.strafingTime = -1;
        this.retreating = false;
        if (changedMode) {
            this.repathCooldown = 0;
            entity.getNavigation().stop();
        }

        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        entity.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        if (--this.repathCooldown <= 0) {
            net.minecraft.world.phys.Vec3 pos = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(entity, 16, 7, target.position());
            if (pos != null) {
                entity.getNavigation().moveTo(pos.x, pos.y, pos.z, 0.65);
                this.repathCooldown = 15 + entity.getRandom().nextInt(10);
            } else {
                getArcherMoveControl(entity).retreatFrom(target, STRAFE_SPEED, 0.65);
                this.repathCooldown = 5;
            }
        }
    }

    private void approach(E entity, LivingEntity target) {
        boolean changedMode = !"approach".equals(this.movementMode);
        this.movementMode = "approach";
        this.strafingTime = -1;
        this.retreating = false;
        if (changedMode) {
            this.repathCooldown = 0;
            entity.getNavigation().stop();
        }

        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        if (--this.repathCooldown <= 0 || entity.getNavigation().isDone()) {
            entity.getNavigation().moveTo(target, SPEED_MODIFIER);
            this.repathCooldown = getNextRepathCooldown(entity);
        }
    }

    private void hold(E entity, LivingEntity target) {
        this.movementMode = "hold";
        this.strafingTime = -1;
        this.retreating = false;
        this.repathCooldown = 0;
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        entity.getNavigation().stop();
        getArcherMoveControl(entity).face(target);
    }

    private void retreat(E entity, LivingEntity target) {
        this.movementMode = "retreat";
        this.strafingTime = -1;
        this.repathCooldown = 0;
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        entity.getNavigation().stop();
        getArcherMoveControl(entity).retreatFrom(target, STRAFE_SPEED, RETREAT_SPEED_MODIFIER);
    }

    private void strafe(E entity, LivingEntity target) {
        this.movementMode = "strafe";
        this.retreating = false;
        this.repathCooldown = 0;
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        entity.getNavigation().stop();

        this.strafingTime++;
        if (this.strafingTime >= 20) {
            if (entity.getRandom().nextFloat() < 0.3F) {
                this.strafingClockwise = !this.strafingClockwise;
            }

            this.strafingTime = 0;
        }

        float targetYRot = getTargetYRot(entity, target);
        float yRot = entity.getMoveControl().rotlerp(entity.getYRot(), targetYRot, 30.0F);
        entity.setYRot(yRot);
        entity.yBodyRot = yRot;
        entity.yHeadRot = yRot;
        entity.getMoveControl().strafe(0.0F, this.strafingClockwise ? 0.5F : -0.5F);
    }

    private boolean shouldRetreat(double distanceSquared) {
        double enterDistance = this.maximumRangeSquared * RETREAT_ENTER_RANGE_FRACTION;
        double exitDistance = this.maximumRangeSquared * RETREAT_EXIT_RANGE_FRACTION;
        this.retreating = this.retreating ? distanceSquared < exitDistance : distanceSquared < enterDistance;
        return this.retreating;
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

    private void logDebugState(ServerLevel level, E entity, LivingEntity target, boolean visible, double distanceSquared) {
        String state = this.movementMode + ":" + visible + ":" + this.seeTime + ":" + this.strafingTime + ":" + this.retreating + ":" + this.strafingClockwise + ":" + entity.getNavigation().isDone() + ":" + entity.horizontalCollision + ":" + entity.onGround();
        long gameTime = level.getGameTime();
        if (state.equals(this.lastDebugState) && gameTime - this.lastDebugLogTime < 20) {
            return;
        }

        this.lastDebugState = state;
        this.lastDebugLogTime = gameTime;
        MCA.LOGGER.info(
                "[MCA Archer Movement] entity={} entityName=\"{}\" target={} targetName=\"{}\" mode={} movement={} distSqr={} los={} seeTime={} strafingTime={} retreating={} strafingClockwise={} navDone={} horizontalCollision={} minorHorizontalCollision={} onGround={} yRot={} targetYRot={} yHeadRot={} yBodyRot={} movementSpeed={} deltaMovement={} pos={} targetPos={}",
                entity.getStringUUID(),
                entity.getName().getString(),
                target.getStringUUID(),
                target.getName().getString(),
                this.strafingTime > -1 ? "STRAFE" : "NAVIGATE",
                this.movementMode,
                String.format("%.2f", distanceSquared),
                visible,
                this.seeTime,
                this.strafingTime,
                this.retreating,
                this.strafingClockwise,
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
                target.blockPosition()
        );
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
        return entity.isHolding(stack -> stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem);
    }

    private static ArcherMoveControl getArcherMoveControl(Mob entity) {
        if (entity.getMoveControl() instanceof ArcherMoveControl archerMoveControl) {
            return archerMoveControl;
        }

        throw new IllegalStateException(entity.getType() + " must use ArcherMoveControl for archer movement");
    }
}
