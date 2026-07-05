package net.conczin.mca.mixin;

import net.conczin.mca.entity.PlayerDimensions;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class MixinLivingEntity {
    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void mca$scalePlayerDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> info) {
        if (pose == Pose.SLEEPING || !((Object) this instanceof Player player)) {
            return;
        }

        PlayerDimensions.getScale(player).ifPresent(scale -> {
            EntityDimensions original = info.getReturnValue();
            EntityDimensions scaled = original.scale(scale.width(), scale.height());
            PlayerDimensions.debugAppliedScale(player, original, scaled, scale);
            info.setReturnValue(scaled);
        });
    }

    @Inject(method = "isImmobile()Z", at = @At("HEAD"), cancellable = true)
    private void mca$onIsImmobile(CallbackInfoReturnable<Boolean> info) {
        if ((Object) this instanceof Mob mob && mob.getControllingPassenger() instanceof VillagerEntityMCA) {
            info.setReturnValue(false);
        }
    }
}
