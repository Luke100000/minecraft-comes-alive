package net.mca.entity.ai.brain.tasks;

import net.mca.entity.ai.RangedWeaponHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.CrossbowAttack;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.CrossbowAttackMob;

public final class ExtendedCrossbowAttackTask<E extends Mob & CrossbowAttackMob, T extends LivingEntity> extends CrossbowAttack<E, T> {

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, E entity) {
        LivingEntity target = getAttackTarget(entity);
        InteractionHand hand = RangedWeaponHelper.getCrossbowHoldingHand(entity);
        return RangedWeaponHelper.isValidAttackTarget(entity, target)
                && hand != null
                && BehaviorUtils.canSee(entity, target)
                && entity.distanceToSqr(target) <= RangedWeaponHelper.getAttackRangeSquared(entity, hand);
    }

    @Override
    protected void stop(ServerLevel world, E entity, long time) {
        super.stop(world, entity, time);
        entity.setChargingCrossbow(false);
    }

    @Override
    public void crossbowAttack(E entity, LivingEntity target) {
        if (crossbowState == CrossbowState.UNCHARGED) {
            InteractionHand hand = RangedWeaponHelper.getCrossbowHoldingHand(entity);
            if (hand == null) {
                return;
            }
            entity.startUsingItem(hand);
            crossbowState = CrossbowState.CHARGING;
            entity.setChargingCrossbow(true);
            return;
        }

        super.crossbowAttack(entity, target);
    }

    private static LivingEntity getAttackTarget(LivingEntity entity) {
        return entity.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }
}
