package net.mca.mixin;

import net.mca.entity.PlayerDimensions;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.20.1 equivalent of the 1.21.1 LivingEntity dimensions hook. Keeping the
 * injection on PlayerEntity avoids changing unrelated living entity dimensions.
 */
@Mixin(PlayerEntity.class)
abstract class MixinPlayerEntityDimensions {
    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void mca$getDimensions(EntityPose pose, CallbackInfoReturnable<EntityDimensions> info) {
        if (pose == EntityPose.SLEEPING) {
            return;
        }

        PlayerEntity player = (PlayerEntity) (Object) this;
        PlayerDimensions.getScale(player).ifPresent(scale -> {
            EntityDimensions original = info.getReturnValue();
            EntityDimensions scaled = original.scaled(scale.width(), scale.height());
            PlayerDimensions.debugAppliedScale(player, original, scaled, scale);
            info.setReturnValue(scaled);
        });
    }
}
