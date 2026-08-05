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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;

public final class ExtendedCrossbowAttackTask<E extends Mob & CrossbowAttackMob, T extends LivingEntity> extends CrossbowAttack<E, T> {
    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, E entity) {
        LivingEntity target = getAttackTarget(entity);
        if (!hasValidTarget(entity, target)) {
            return false;
        }
        InteractionHand hand = RangedWeaponHelper.getCrossbowHoldingHand(entity);
        if (hand != null && entity.getItemInHand(hand).is(Items.CROSSBOW)) {
            return super.checkExtraStartConditions(level, entity);
        }

        return hand != null
               && BehaviorUtils.canSee(entity, target)
               && entity.distanceToSqr(target) <= RangedWeaponHelper.getAttackRangeSquared(entity, hand, Double.MAX_VALUE);
    }

    @Override
    public void crossbowAttack(E entity, LivingEntity target) {
        InteractionHand hand = RangedWeaponHelper.getCrossbowHoldingHand(entity);
        if (hand != null && entity.getItemInHand(hand).is(Items.CROSSBOW)) {
            super.crossbowAttack(entity, target);
            return;
        }

        if (hand != null && this.crossbowState == CrossbowState.UNCHARGED) {
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

    private static boolean hasValidTarget(Mob entity, LivingEntity target) {
        return target != null
               && target.isAlive()
               && !target.isRemoved()
               && target.level() == entity.level()
               && entity.canAttack(target);
    }
}
