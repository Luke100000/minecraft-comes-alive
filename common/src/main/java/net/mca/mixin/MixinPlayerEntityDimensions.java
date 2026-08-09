package net.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.mca.entity.PlayerDimensions;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 1.20.1 equivalent of the 1.21.1 LivingEntity dimensions hook. Keeping the
 * injection on PlayerEntity avoids changing unrelated living entity dimensions.
 */
@Mixin(PlayerEntity.class)
abstract class MixinPlayerEntityDimensions {
    @ModifyReturnValue(method = "getDimensions", at = @At("RETURN"))
    private EntityDimensions mca$scalePlayerDimensions(EntityDimensions original, EntityPose pose) {
        if (pose == EntityPose.SLEEPING) {
            return original;
        }

        PlayerEntity player = (PlayerEntity) (Object) this;
        return PlayerDimensions.getScale(player)
                .map(scale -> {
                    EntityDimensions scaled = original.scaled(scale.width(), scale.height());
                    PlayerDimensions.debugAppliedScale(player, original, scaled, scale);
                    return scaled;
                })
                .orElse(original);
    }

    @ModifyReturnValue(
            method = "getActiveEyeHeight(Lnet/minecraft/entity/EntityPose;Lnet/minecraft/entity/EntityDimensions;)F",
            at = @At("RETURN")
    )
    private float mca$scalePlayerEyeHeightWithHitbox(float original, EntityPose pose, EntityDimensions dimensions) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player.getPose() == EntityPose.SLEEPING || pose == EntityPose.SLEEPING) {
            return original;
        }

        return PlayerDimensions.getScale(player)
                .map(scale -> original * scale.height())
                .orElse(original);
    }

}
