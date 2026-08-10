package net.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.mca.entity.ai.RangedWeaponHelper;
import net.mca.entity.ai.brain.tasks.ExtendedCrossbowAttackTask;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.CrossbowAttack;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CrossbowAttack.class)
abstract class MixinCrossbowAttackTask {
    @ModifyExpressionValue(
            method = "crossbowAttack(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/CrossbowItem;getChargeDuration(Lnet/minecraft/world/item/ItemStack;)I"
            )
    )
    private int mca$useItemDefinedChargeDuration(int original, Mob entity, LivingEntity ignoredTarget) {
        if (!((Object) this instanceof ExtendedCrossbowAttackTask<?, ?>)) {
            return original;
        }

        ItemStack stack = entity.getUseItem();
        if (stack.is(Items.CROSSBOW) || !(stack.getItem() instanceof CrossbowItem)) {
            return original;
        }

        return stack.getUseDuration() - 3;
    }

    /**
     * 1.20.1 stores a separate boolean Charged flag and clears it after firing.
     * Fabric locates that stack using the exact vanilla crossbow item,
     * while 1.21.1 no longer has this cleanup at all. Redirect only the stack
     * selected for the old 1.20.1 cleanup so the extended task keeps the
     * 1.21.1 state-machine shape without owning READY_TO_ATTACK itself.
     */
    @ModifyExpressionValue(
            method = "crossbowAttack(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Mob;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack mca$useSelectedCrossbowForLegacyChargeReset(
            ItemStack original,
            Mob entity,
            LivingEntity ignoredTarget
    ) {
        if (!((Object) this instanceof ExtendedCrossbowAttackTask<?, ?>)) {
            return original;
        }

        InteractionHand hand = RangedWeaponHelper.getCrossbowHoldingHand(entity);
        return hand == null ? original : entity.getItemInHand(hand);
    }
}
