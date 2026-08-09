package net.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.mca.entity.ai.RangedWeaponHelper;
import net.mca.entity.ai.brain.tasks.ExtendedCrossbowAttackTask;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.task.CrossbowAttackTask;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CrossbowAttackTask.class)
abstract class MixinCrossbowAttackTask {
    @ModifyExpressionValue(
            method = "tickState(Lnet/minecraft/entity/mob/MobEntity;Lnet/minecraft/entity/LivingEntity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/CrossbowItem;getPullTime(Lnet/minecraft/item/ItemStack;)I"
            )
    )
    private int mca$useItemDefinedChargeDuration(int original, MobEntity entity, LivingEntity ignoredTarget) {
        if (!((Object) this instanceof ExtendedCrossbowAttackTask<?, ?>)) {
            return original;
        }

        ItemStack stack = entity.getActiveItem();
        if (stack.isOf(Items.CROSSBOW) || !(stack.getItem() instanceof CrossbowItem)) {
            return original;
        }

        return stack.getMaxUseTime() - 3;
    }

    /**
     * 1.20.1 stores a separate boolean Charged flag and clears it after firing.
     * Fabric/Quilt locate that stack using the exact vanilla crossbow item,
     * while 1.21.1 no longer has this cleanup at all. Redirect only the stack
     * selected for the old 1.20.1 cleanup so the extended task keeps the
     * 1.21.1 state-machine shape without owning READY_TO_ATTACK itself.
     */
    @ModifyExpressionValue(
            method = "tickState(Lnet/minecraft/entity/mob/MobEntity;Lnet/minecraft/entity/LivingEntity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/mob/MobEntity;getStackInHand(Lnet/minecraft/util/Hand;)Lnet/minecraft/item/ItemStack;"
            )
    )
    private ItemStack mca$useSelectedCrossbowForLegacyChargeReset(
            ItemStack original,
            MobEntity entity,
            LivingEntity ignoredTarget
    ) {
        if (!((Object) this instanceof ExtendedCrossbowAttackTask<?, ?>)) {
            return original;
        }

        Hand hand = RangedWeaponHelper.getCrossbowHoldingHand(entity);
        return hand == null ? original : entity.getStackInHand(hand);
    }
}
