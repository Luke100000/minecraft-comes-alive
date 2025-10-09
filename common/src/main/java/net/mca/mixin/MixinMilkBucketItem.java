package net.mca.mixin;

import net.mca.client.model.CommonVillagerModel;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.Traits;
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
    public void mca$injectFinishUsingItem(ItemStack stack, Level world, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
        VillagerLike<?> villagerLike = world.isClientSide ? CommonVillagerModel.getVillager(user) : VillagerLike.toVillager(user);
        if (villagerLike != null) {
            if (villagerLike.getTraits().hasTrait(Traits.LACTOSE_INTOLERANCE)) {
                user.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
            }
        }
    }
}
