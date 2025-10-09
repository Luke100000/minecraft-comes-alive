package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.item.Items;

public class BowTask<E extends Mob & CrossbowAttackMob> extends Behavior<E> {
    private final int fireInterval;
    private final int squaredRange;
    private int lastShot;

    public BowTask(int fireInterval, int range) {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT));
        this.fireInterval = fireInterval;
        this.squaredRange = range * range;
    }

    private static LivingEntity getAttackTarget(LivingEntity entity) {
        return entity.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel serverWorld, E entity) {
        LivingEntity livingEntity = getAttackTarget(entity);
        return livingEntity != null && entity.isHolding(Items.BOW)
               && BehaviorUtils.canSee(entity, livingEntity)
               && BehaviorUtils.isWithinAttackRange(entity, livingEntity, 0);
    }

    @Override
    protected void tick(ServerLevel world, E entity, long time) {
        super.tick(world, entity, time);

        LivingEntity target = getAttackTarget(entity);
        double d = entity.distanceToSqr(target);

        //keep distance
        float backward = 0.0f;
        if (d > this.squaredRange * 1.25F) {
            backward = 0.5f;
        } else if (d < this.squaredRange * 0.75F) {
            backward = -0.5f;
        }

        //strafe
        float strafe = (float) (Math.cos(time / 20.0f) * 0.5);
        entity.getMoveControl().strafe(backward, strafe);
        entity.lookAt(target, 30.0F, 30.0F);

        //shoot
        if (entity.tickCount - lastShot > fireInterval) {
            entity.performRangedAttack(target, 1.0F);
            lastShot = entity.tickCount;
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel world, E entity, long time) {
        return checkExtraStartConditions(world, entity);
    }

    @Override
    protected void start(ServerLevel world, E entity, long time) {
        entity.setAggressive(true);
    }

    @Override
    protected void stop(ServerLevel world, E entity, long time) {
        super.stop(world, entity, time);
        entity.setAggressive(false);
    }
}
