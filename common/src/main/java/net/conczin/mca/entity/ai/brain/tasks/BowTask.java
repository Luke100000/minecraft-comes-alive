package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;

import java.util.function.Predicate;

public class BowTask<E extends Mob & CrossbowAttackMob> extends Behavior<E> {
    private static final Predicate<Item> BOW_ITEM = item -> item instanceof BowItem;

    private final int fireInterval;
    private final int squaredRange;
    private int attackTime = -1;
    private int seeTime;
    private boolean strafingClockwise;
    private boolean strafingBackwards;
    private int strafingTime = -1;

    public BowTask(int fireInterval, int range) {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT), 1200);
        this.fireInterval = fireInterval;
        this.squaredRange = range * range;
    }

    private static LivingEntity getAttackTarget(LivingEntity entity) {
        return entity.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    private static boolean isHoldingBow(Mob entity) {
        return entity.isHolding(stack -> BOW_ITEM.test(stack.getItem()));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel serverWorld, E entity) {
        LivingEntity livingEntity = getAttackTarget(entity);
        return livingEntity != null && isHoldingBow(entity);
    }

    @Override
    protected void tick(ServerLevel world, E entity, long time) {
        super.tick(world, entity, time);

        LivingEntity target = getAttackTarget(entity);
        if (target == null) {
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

        if (d <= this.squaredRange && this.seeTime >= 20) {
            entity.getNavigation().stop();
            this.strafingTime++;
        } else {
            entity.getNavigation().moveTo(target, 1.0);
            this.strafingTime = -1;
        }

        if (this.strafingTime >= 20) {
            if (entity.getRandom().nextFloat() < 0.3F) {
                this.strafingClockwise = !this.strafingClockwise;
            }
            if (entity.getRandom().nextFloat() < 0.3F) {
                this.strafingBackwards = !this.strafingBackwards;
            }
            this.strafingTime = 0;
        }

        if (this.strafingTime > -1) {
            if (d > this.squaredRange * 0.75F) {
                this.strafingBackwards = false;
            } else if (d < this.squaredRange * 0.25F) {
                this.strafingBackwards = true;
            }
            entity.getMoveControl().strafe(this.strafingBackwards ? -0.5F : 0.5F, this.strafingClockwise ? 0.5F : -0.5F);
            if (entity.getControlledVehicle() instanceof Mob vehicle) {
                vehicle.lookAt(target, 30.0F, 30.0F);
            }
            entity.lookAt(target, 30.0F, 30.0F);
        } else {
            entity.getLookControl().setLookAt(target, 30.0F, 30.0F);
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
            entity.startUsingItem(ProjectileUtil.getWeaponHoldingHand(entity, BOW_ITEM));
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel world, E entity, long time) {
        LivingEntity livingEntity = getAttackTarget(entity);
        return (livingEntity != null && livingEntity.isAlive() || !entity.getNavigation().isDone()) && isHoldingBow(entity);
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
        if (entity.isUsingItem()) {
            entity.stopUsingItem();
        }
    }
}
