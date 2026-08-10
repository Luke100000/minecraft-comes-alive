package net.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.mca.entity.PlayerDimensions;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 1.20.1 equivalent of the 1.21.1 LivingEntity dimensions hook. Keeping the
 * injection on PlayerEntity avoids changing unrelated living entity dimensions.
 */
@Mixin(Player.class)
abstract class MixinPlayerEntityDimensions {
    @ModifyReturnValue(method = "getDimensions", at = @At("RETURN"))
    private EntityDimensions mca$scalePlayerDimensions(EntityDimensions original, Pose pose) {
        if (pose == Pose.SLEEPING) {
            return original;
        }

        Player player = (Player) (Object) this;
        return PlayerDimensions.getScale(player)
                .map(scale -> {
                    EntityDimensions scaled = original.scale(scale.width(), scale.height());
                    PlayerDimensions.debugAppliedScale(player, original, scaled, scale);
                    return scaled;
                })
                .orElse(original);
    }

    @ModifyReturnValue(
            method = "getStandingEyeHeight(Lnet/minecraft/world/entity/Pose;Lnet/minecraft/world/entity/EntityDimensions;)F",
            at = @At("RETURN")
    )
    private float mca$scalePlayerEyeHeightWithHitbox(float original, Pose pose, EntityDimensions dimensions) {
        Player player = (Player) (Object) this;
        if (player.getPose() == Pose.SLEEPING || pose == Pose.SLEEPING) {
            return original;
        }

        return PlayerDimensions.getScale(player)
                .map(scale -> original * scale.height())
                .orElse(original);
    }

}
