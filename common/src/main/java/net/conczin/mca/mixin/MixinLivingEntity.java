package net.conczin.mca.mixin;

import net.conczin.mca.entity.PlayerDimensions;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
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
        //noinspection ConstantValue
        if (pose == Pose.SLEEPING || !((Object) this instanceof Player player)) {
            return;
        }

        PlayerDimensions.getScale(player).ifPresent(scale ->
                info.setReturnValue(info.getReturnValue().scale(scale.width(), scale.height()))
        );
    }
}
