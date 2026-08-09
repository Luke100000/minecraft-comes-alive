package net.mca.entity.ai.brain.tasks;

import net.mca.entity.ai.RangedWeaponHelper;
import net.minecraft.entity.CrossbowUser;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.CrossbowAttackTask;
import net.minecraft.entity.ai.brain.task.LookTargetUtil;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;

public final class ExtendedCrossbowAttackTask<E extends MobEntity & CrossbowUser, T extends LivingEntity> extends CrossbowAttackTask<E, T> {

    @Override
    protected boolean shouldRun(ServerWorld world, E entity) {
        LivingEntity target = getAttackTarget(entity);
        Hand hand = RangedWeaponHelper.getCrossbowHoldingHand(entity);
        return RangedWeaponHelper.isValidAttackTarget(entity, target)
                && hand != null
                && LookTargetUtil.isVisibleInMemory(entity, target)
                && entity.squaredDistanceTo(target) <= RangedWeaponHelper.getAttackRangeSquared(entity, hand);
    }

    @Override
    protected void finishRunning(ServerWorld world, E entity, long time) {
        super.finishRunning(world, entity, time);
        entity.setCharging(false);
    }

    @Override
    protected void tickState(E entity, LivingEntity target) {
        if (state == CrossbowState.UNCHARGED) {
            Hand hand = RangedWeaponHelper.getCrossbowHoldingHand(entity);
            if (hand == null) {
                return;
            }
            entity.setCurrentHand(hand);
            state = CrossbowState.CHARGING;
            entity.setCharging(true);
            return;
        }

        super.tickState(entity, target);
    }

    private static LivingEntity getAttackTarget(LivingEntity entity) {
        return entity.getBrain().getOptionalMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }
}
