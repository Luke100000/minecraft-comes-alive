package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

public class BowTask<E extends Mob & CrossbowAttackMob> extends Behavior<E> {
    private static final float CLOSE_RANGE_ENTER = 0.22F;
    private static final float CLOSE_RANGE_EXIT = 0.33F;
    private static final float STRAFE_SPEED = 0.2F;
    private static final double PATH_SPEED_MODIFIER = 0.5;

    private final int fireInterval;
    private final int squaredRange;
    private int attackTime = -1;
    private int seeTime;
    private boolean strafingClockwise;
    private boolean strafingBackwards;
    private int strafingTime = -1;
    private long lastDebugLogTime = Long.MIN_VALUE;
    private String lastDebugState = "";

    public BowTask(int fireInterval, int range) {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT), 1200);
        this.fireInterval = fireInterval;
        this.squaredRange = range * range;
    }

    private static LivingEntity getAttackTarget(LivingEntity entity) {
        return entity.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    private static boolean hasValidTarget(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved();
    }

    private static boolean isHoldingBow(Mob entity) {
        return entity.isHolding(stack -> isBowItem(stack.getItem()));
    }

    private static boolean isBowItem(Item item) {
        return item instanceof BowItem;
    }

    private static InteractionHand getBowHoldingHand(Mob entity) {
        return isBowItem(entity.getMainHandItem().getItem()) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel serverWorld, E entity) {
        LivingEntity livingEntity = getAttackTarget(entity);
        return hasValidTarget(livingEntity) && isHoldingBow(entity);
    }

    @Override
    protected void tick(ServerLevel world, E entity, long time) {
        super.tick(world, entity, time);

        LivingEntity target = getAttackTarget(entity);
        if (!hasValidTarget(target)) {
            stopMovement(entity);
            if (entity.isUsingItem()) {
                entity.stopUsingItem();
            }
            return;
        }

        double d = entity.distanceToSqr(target.getX(), target.getY(), target.getZ());

        boolean hasLineOfSight = entity.getSensing().hasLineOfSight(target);
        boolean hadLineOfSight = this.seeTime > 0;
        if (hasLineOfSight != hadLineOfSight) {
            this.seeTime = 0;
        }
        if (hasLineOfSight) {
            this.seeTime++;
        } else {
            this.seeTime--;
        }

        float forward = 0.0F;
        float sideways = 0.0F;
        boolean pathingAway = false;
        boolean strafeWalkable = true;
        boolean wantsBackAway = d < this.squaredRange * CLOSE_RANGE_ENTER || this.strafingBackwards && d < this.squaredRange * CLOSE_RANGE_EXIT;

        if (d <= this.squaredRange && hasLineOfSight) {
            if (wantsBackAway && this.seeTime >= 20) {
                pathingAway = tryPathAwayFromTarget(entity, target);
                this.strafingTime = pathingAway ? -1 : this.strafingTime + 1;
            } else {
                entity.getNavigation().stop();
                if (this.seeTime >= 20) {
                    this.strafingTime++;
                } else {
                    this.strafingTime = -1;
                }
            }
        } else {
            entity.getNavigation().moveTo(target, PATH_SPEED_MODIFIER);
            this.strafingTime = -1;
        }

        entity.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        if (this.strafingTime >= 20) {
            if (entity.getRandom().nextFloat() < 0.3F) {
                this.strafingClockwise = !this.strafingClockwise;
            }
            this.strafingTime = 0;
        }

        if (this.strafingTime > -1) {
            if (wantsBackAway) {
                this.strafingBackwards = true;
                forward = -STRAFE_SPEED;
            } else {
                this.strafingBackwards = false;
                forward = 0.0F;
            }
            sideways = this.strafingClockwise ? STRAFE_SPEED : -STRAFE_SPEED;
            strafeWalkable = isStrafeWalkable(entity, forward, sideways);
            if (strafeWalkable) {
                entity.getMoveControl().strafe(forward, sideways);
            }
            if (entity.getControlledVehicle() instanceof Mob vehicle) {
                vehicle.lookAt(target, 30.0F, 30.0F);
            }
            entity.lookAt(target, 30.0F, 30.0F);
        } else {
            if (pathingAway) {
                entity.lookAt(target, 30.0F, 30.0F);
            }
            entity.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }

        if (MCA.platformHelper.isDevelopmentEnvironment()) {
            logDebugState(world, entity, target, d, hasLineOfSight, forward, sideways, pathingAway, strafeWalkable);
        }

        if (entity.isUsingItem()) {
            if (!hasLineOfSight && this.seeTime < -60) {
                entity.stopUsingItem();
            } else if (hasLineOfSight) {
                int pullTime = entity.getTicksUsingItem();
                if (pullTime >= 20) {
                    entity.stopUsingItem();
                    entity.performRangedAttack(target, BowItem.getPowerForTime(pullTime));
                    this.attackTime = this.fireInterval;
                }
            }
        } else if (--this.attackTime <= 0 && this.seeTime >= -60) {
            entity.startUsingItem(getBowHoldingHand(entity));
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel world, E entity, long time) {
        LivingEntity livingEntity = getAttackTarget(entity);
        return hasValidTarget(livingEntity) && isHoldingBow(entity);
    }

    @Override
    protected void start(ServerLevel world, E entity, long time) {
        entity.setAggressive(true);
    }

    @Override
    protected void stop(ServerLevel world, E entity, long time) {
        super.stop(world, entity, time);
        entity.setAggressive(false);
        this.seeTime = 0;
        this.attackTime = -1;
        this.strafingTime = -1;
        this.strafingBackwards = false;
        this.lastDebugState = "";
        stopMovement(entity);
        if (entity.isUsingItem()) {
            entity.stopUsingItem();
        }
    }

    private void logDebugState(ServerLevel world, E entity, LivingEntity target, double distanceSquared, boolean hasLineOfSight, float forward, float sideways, boolean pathingAway, boolean strafeWalkable) {
        String mode = this.strafingTime > -1 ? "strafe" : "path";
        String state = mode + ':' + hasLineOfSight + ':' + this.strafingBackwards + ':' + this.strafingClockwise + ':' + forward + ':' + sideways + ':' + pathingAway + ':' + strafeWalkable + ':' + entity.horizontalCollision + ':' + entity.onGround() + ':' + entity.getNavigation().isDone();
        long gameTime = world.getGameTime();
        if (state.equals(this.lastDebugState) && gameTime - this.lastDebugLogTime < 20) {
            return;
        }

        Vec3 toTarget = target.position().subtract(entity.position());
        Vec3 movement = entity.getDeltaMovement();
        double horizontalDistance = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        double movementTowardTarget = horizontalDistance <= 1.0E-5 ? 0.0 : (movement.x * toTarget.x + movement.z * toTarget.z) / horizontalDistance;

        this.lastDebugState = state;
        this.lastDebugLogTime = gameTime;
        MCA.LOGGER.info(
                "[MCA Archer BowTask] entity={} target={} mode={} distSqr={} rangeSqr={} los={} seeTime={} navDone={} strafingTime={} backwards={} clockwise={} forward={} sideways={} strafeWalkable={} pathingAway={} horizontalCollision={} minorHorizontalCollision={} onGround={} movementSpeed={} movementTowardTarget={} pos={} targetPos={} mainHand={} offHand={} usingItem={} attackTime={}",
                entity.getStringUUID(),
                target.getStringUUID(),
                mode,
                String.format("%.2f", distanceSquared),
                this.squaredRange,
                hasLineOfSight,
                this.seeTime,
                entity.getNavigation().isDone(),
                this.strafingTime,
                this.strafingBackwards,
                this.strafingClockwise,
                forward,
                sideways,
                strafeWalkable,
                pathingAway,
                entity.horizontalCollision,
                entity.minorHorizontalCollision,
                entity.onGround(),
                String.format("%.4f", entity.getAttributeValue(Attributes.MOVEMENT_SPEED)),
                String.format("%.4f", movementTowardTarget),
                entity.blockPosition(),
                target.blockPosition(),
                entity.getMainHandItem(),
                entity.getOffhandItem(),
                entity.isUsingItem(),
                this.attackTime
        );
    }

    private static boolean tryPathAwayFromTarget(Mob entity, LivingEntity target) {
        if (!(entity instanceof PathfinderMob pathfinder)) {
            return false;
        }

        if (!entity.getNavigation().isDone()) {
            return true;
        }

        Vec3 awayPos = LandRandomPos.getPosAway(pathfinder, 4, 2, target.position());
        return awayPos != null && entity.getNavigation().moveTo(awayPos.x, awayPos.y, awayPos.z, PATH_SPEED_MODIFIER);
    }

    private static boolean isStrafeWalkable(Mob entity, float forward, float sideways) {
        if (forward == 0.0F && sideways == 0.0F) {
            return true;
        }

        float speed = (float)entity.getAttributeValue(Attributes.MOVEMENT_SPEED);
        float speedModified = 0.25F * speed;
        float strafeForward = forward;
        float strafeSideways = sideways;
        float distance = Mth.sqrt(strafeForward * strafeForward + strafeSideways * strafeSideways);
        if (distance < 1.0F) {
            distance = 1.0F;
        }

        distance = speedModified / distance;
        strafeForward *= distance;
        strafeSideways *= distance;
        float sin = Mth.sin(entity.getYRot() * (float) (Math.PI / 180.0));
        float cos = Mth.cos(entity.getYRot() * (float) (Math.PI / 180.0));
        float dx = strafeForward * cos - strafeSideways * sin;
        float dz = strafeSideways * cos + strafeForward * sin;

        PathNavigation pathNavigation = entity.getNavigation();
        NodeEvaluator nodeEvaluator = pathNavigation == null ? null : pathNavigation.getNodeEvaluator();
        return nodeEvaluator == null
                || nodeEvaluator.getPathType(entity, BlockPos.containing(entity.getX() + dx, entity.getBlockY(), entity.getZ() + dz)) == PathType.WALKABLE;
    }

    private static void stopMovement(Mob entity) {
        entity.getNavigation().stop();
        entity.getMoveControl().setWait();
        entity.setXxa(0.0F);
        entity.setYya(0.0F);
        entity.setZza(0.0F);
        Vec3 movement = entity.getDeltaMovement();
        entity.setDeltaMovement(0.0, movement.y, 0.0);
    }
}
