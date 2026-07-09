package net.mca.mixin;

import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows any mob controlled by an MCA villager passenger to remain mobile.
 */
@Mixin(LivingEntity.class)
abstract class MixinLivingEntity {
    @Inject(method = "isImmobile()Z", at = @At("HEAD"), cancellable = true)
    private void mca$onIsImmobile(CallbackInfoReturnable<Boolean> info) {
        if ((Object) this instanceof MobEntity mob && mob.getControllingPassenger() instanceof VillagerEntityMCA) {
            info.setReturnValue(false);
        }
    }
}
