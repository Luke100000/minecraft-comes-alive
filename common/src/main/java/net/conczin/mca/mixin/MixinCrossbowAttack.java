package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedCrossbowAttackTask;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.CrossbowAttack;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CrossbowAttack.class)
abstract class MixinCrossbowAttack {
    @ModifyExpressionValue(
            method = "crossbowAttack(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/CrossbowItem;getChargeDuration(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)I"
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

        return stack.getUseDuration(entity) - 3;
    }
}
