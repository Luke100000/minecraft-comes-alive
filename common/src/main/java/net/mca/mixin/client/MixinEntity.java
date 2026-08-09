package net.mca.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.mca.Config;
import net.mca.MCAClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @ModifyReturnValue(method = "getStandingEyeHeight()F", at = @At("RETURN"))
    private float mca$scalePlayerStandingEyeHeight(float original) {
        return original * mca$getPlayerEyeHeightScale();
    }

    @ModifyReturnValue(method = "getEyeY()D", at = @At("RETURN"))
    private double mca$scalePlayerEyeY(double original) {
        float scale = mca$getPlayerEyeHeightScale();
        if (scale == 1.0F) {
            return original;
        }

        PlayerEntity player = (PlayerEntity) (Object) this;
        double baseY = player.getY();
        return baseY + (original - baseY) * scale;
    }

    private float mca$getPlayerEyeHeightScale() {
        if (!((Object) this instanceof PlayerEntity player)
                || player.getPose() == EntityPose.SLEEPING
                || !Config.getInstance().scaleEyeHeightWithPlayerHeight
                || Config.getServerConfig().scalePlayerHitboxWithSizeAndWidth) {
            return 1.0F;
        }

        return MCAClient.getGeneticsPlayerData(player.getUuid())
                .map(data -> data.getRawVerticalScaleFactor())
                .orElse(1.0F);
    }
}
