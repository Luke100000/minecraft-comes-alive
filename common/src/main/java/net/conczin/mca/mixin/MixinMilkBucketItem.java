package net.conczin.mca.mixin;

import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MilkBucketItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MilkBucketItem.class)
public class MixinMilkBucketItem {
    @Inject(method = "finishUsingItem", at = @At("RETURN"))
    public void onFinishedUsing(ItemStack stack, Level world, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
        VillagerLike<?> villagerLike = world.isClientSide ? CommonVillagerModel.getVillager(user) : VillagerLike.toVillager(user);
        if (villagerLike != null) {
            if (villagerLike.getTraits().hasTrait(Traits.LACTOSE_INTOLERANCE)) {
                user.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
            }
        }
    }
}
