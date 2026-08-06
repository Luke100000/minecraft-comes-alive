package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.ai.RangedWeaponHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.CrossbowAttack;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;

public final class ExtendedCrossbowAttackTask<E extends Mob & CrossbowAttackMob, T extends LivingEntity> extends CrossbowAttack<E, T> {
    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, E entity) {
        LivingEntity target = getAttackTarget(entity);
        InteractionHand hand = RangedWeaponHelper.getCrossbowHoldingHand(entity);
        return RangedWeaponHelper.isValidAttackTarget(entity, target)
               && hand != null
               && BehaviorUtils.canSee(entity, target)
               && entity.distanceToSqr(target) <= RangedWeaponHelper.getAttackRangeSquared(entity, hand);
    }

    @Override
    public void crossbowAttack(E entity, LivingEntity target) {
        if (this.crossbowState == CrossbowState.UNCHARGED) {
            InteractionHand hand = RangedWeaponHelper.getCrossbowHoldingHand(entity);
            if (hand == null) {
                return;
            }
            entity.startUsingItem(hand);
            this.crossbowState = CrossbowState.CHARGING;
            entity.setChargingCrossbow(true);
            return;
        }

        super.crossbowAttack(entity, target);
    }

    @Override
    protected void stop(ServerLevel level, E entity, long gameTime) {
        ItemStack crossbowStack = entity.getUseItem();
        if (!(crossbowStack.getItem() instanceof CrossbowItem)) {
            InteractionHand hand = RangedWeaponHelper.getCrossbowHoldingHand(entity);
            crossbowStack = hand == null ? ItemStack.EMPTY : entity.getItemInHand(hand);
        }

        super.stop(level, entity, gameTime);
        entity.setChargingCrossbow(false);
        if (crossbowStack.getItem() instanceof CrossbowItem) {
            crossbowStack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        }
    }

    private static LivingEntity getAttackTarget(LivingEntity entity) {
        return entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }
}
