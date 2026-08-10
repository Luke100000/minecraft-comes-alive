package net.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Allows any mob controlled by an MCA villager passenger to remain mobile.
 */
@Mixin(LivingEntity.class)
abstract class MixinLivingEntity {
    @ModifyReturnValue(method = "isImmobile()Z", at = @At("RETURN"))
    private boolean mca$allowMcaControlledMovement(boolean original) {
        if ((Object) this instanceof Mob mob && mob.getControllingPassenger() instanceof VillagerEntityMCA) {
            return false;
        }
        return original;
    }
}
