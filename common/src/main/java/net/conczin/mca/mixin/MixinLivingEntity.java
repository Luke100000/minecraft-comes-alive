package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.conczin.mca.entity.PlayerDimensions;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class MixinLivingEntity {
    @ModifyReturnValue(method = "getDimensions", at = @At("RETURN"))
    private EntityDimensions mca$scalePlayerDimensions(EntityDimensions original, Pose pose) {
        if (pose == Pose.SLEEPING || !((Object) this instanceof Player player)) {
            return original;
        }

        return PlayerDimensions.getScale(player)
                .map(scale -> {
                    EntityDimensions scaled = original.scale(scale.width(), scale.height());
                    PlayerDimensions.debugAppliedScale(player, original, scaled, scale);
                    return scaled;
                })
                .orElse(original);
    }

    @ModifyReturnValue(method = "isImmobile()Z", at = @At("RETURN"))
    private boolean mca$allowMcaControlledMovement(boolean original) {
        if ((Object) this instanceof Mob mob && mob.getControllingPassenger() instanceof VillagerEntityMCA) {
            return false;
        }
        return original;
    }
}
