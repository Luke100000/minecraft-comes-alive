package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.conczin.mca.entity.PlayerDimensions;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class MixinLivingEntity {
    @ModifyReturnValue(method = "handleRelativeFrictionAndCalculateMovement", at = @At("RETURN"))
    private Vec3 mca$preserveControlledClimbVelocity(Vec3 original, Vec3 input, float friction) {
        if ((Object) this instanceof VillagerEntityMCA villager
                && villager.getNavigation() instanceof MCAGroundPathNavigation navigation) {
            double controlledY = navigation.getControlledClimbableVelocity();
            if (!Double.isNaN(controlledY)) {
                return new Vec3(original.x(), controlledY, original.z());
            }
        }
        return original;
    }

    @ModifyReturnValue(method = "getDimensions", at = @At("RETURN"))
    private EntityDimensions mca$scalePlayerDimensions(EntityDimensions original, Pose pose) {
        if (pose == Pose.SLEEPING || !((Object) this instanceof Player player)) {
            return original;
        }

        return PlayerDimensions.getScale(player)
                .map(scale -> {
                    EntityDimensions scaled = original.scale(scale.width(), scale.height());
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
