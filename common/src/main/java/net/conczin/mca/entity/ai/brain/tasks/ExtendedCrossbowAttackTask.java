package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.ai.RangedWeaponHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.CrossbowAttack;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ExtendedCrossbowAttackTask<E extends Mob & CrossbowAttackMob, T extends LivingEntity> extends CrossbowAttack<E, T> {
    private static final int AIM_TICKS = 10;

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, E entity) {
        LivingEntity target = getAttackTarget(entity);
        InteractionHand hand = RangedWeaponHelper.getCrossbowHoldingHand(entity);
        return !shouldLowerWeapon(entity)
               && RangedWeaponHelper.isValidAttackTarget(entity, target)
               && hand != null
               && BehaviorUtils.canSee(entity, target)
               && entity.distanceToSqr(target) <= RangedWeaponHelper.getAttackRangeSquared(entity, hand);
    }

    @Override
    protected void start(ServerLevel level, E entity, long gameTime) {
        super.start(level, entity, gameTime);
        reconcileCrossbowState(entity);
    }

    @Override
    public void crossbowAttack(E entity, LivingEntity target) {
        if (shouldLowerWeapon(entity)) {
            if (entity.isUsingItem()) {
                entity.stopUsingItem();
            }
            entity.setChargingCrossbow(false);
            this.crossbowState = CrossbowState.UNCHARGED;
            this.attackDelay = 0;
            return;
        }

        InteractionHand hand = reconcileCrossbowState(entity);
        if (hand == null) {
            return;
        }

        if (this.crossbowState == CrossbowState.UNCHARGED) {
            entity.startUsingItem(hand);
            this.crossbowState = CrossbowState.CHARGING;
            entity.setChargingCrossbow(true);
            return;
        }

        if (this.crossbowState == CrossbowState.READY_TO_ATTACK
                && entity instanceof PathfinderMob pathfinder
                && !RangedWeaponHelper.canShootCrossbowFrom(pathfinder, entity.position(), target)) {
            return;
        }

        CrossbowState previousState = this.crossbowState;
        super.crossbowAttack(entity, target);
        if (previousState == CrossbowState.CHARGING && this.crossbowState == CrossbowState.CHARGED) {
            this.attackDelay = AIM_TICKS;
        }
    }

    @Override
    protected void stop(ServerLevel level, E entity, long gameTime) {
        super.stop(level, entity, gameTime);
        entity.setChargingCrossbow(false);
        this.crossbowState = CrossbowState.UNCHARGED;
        this.attackDelay = 0;
    }

    @Nullable
    private InteractionHand reconcileCrossbowState(E entity) {
        InteractionHand hand = RangedWeaponHelper.getCrossbowHoldingHand(entity);
        if (hand == null) {
            if (entity.isUsingItem()) {
                entity.stopUsingItem();
            }
            entity.setChargingCrossbow(false);
            this.crossbowState = CrossbowState.UNCHARGED;
            this.attackDelay = 0;
            return null;
        }

        ItemStack weapon = entity.getItemInHand(hand);
        if (CrossbowItem.isCharged(weapon)) {
            if (entity.isUsingItem()) {
                entity.stopUsingItem();
            }
            entity.setChargingCrossbow(false);
            if (this.crossbowState == CrossbowState.UNCHARGED || this.crossbowState == CrossbowState.CHARGING) {
                this.crossbowState = CrossbowState.CHARGED;
                this.attackDelay = AIM_TICKS;
            } else if (this.crossbowState == CrossbowState.CHARGED && this.attackDelay <= 0) {
                this.crossbowState = CrossbowState.READY_TO_ATTACK;
            }
        } else if (this.crossbowState == CrossbowState.CHARGED || this.crossbowState == CrossbowState.READY_TO_ATTACK) {
            entity.setChargingCrossbow(false);
            this.crossbowState = CrossbowState.UNCHARGED;
            this.attackDelay = 0;
        } else if (this.crossbowState == CrossbowState.CHARGING
                && (!entity.isUsingItem() || entity.getUseItem() != weapon)) {
            entity.setChargingCrossbow(false);
            this.crossbowState = CrossbowState.UNCHARGED;
            this.attackDelay = 0;
        }
        return hand;
    }

    private static LivingEntity getAttackTarget(LivingEntity entity) {
        return entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    private static boolean shouldLowerWeapon(Mob entity) {
        RangedCombatState state = RangedCombatState.current(entity).orElse(RangedCombatState.HOLD);
        return state.suppressesRangedAttack() || !state.permitsAiming(entity);
    }
}
