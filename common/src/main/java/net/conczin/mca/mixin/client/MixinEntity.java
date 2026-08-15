package net.conczin.mca.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.conczin.mca.Config;
import net.conczin.mca.MCAClient;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @ModifyReturnValue(method = "getEyeHeight()F", at = @At("RETURN"))
    private float mca$scalePlayerStandingEyeHeight(float original) {
        return original * mca$getPlayerEyeHeightScale();
    }

    @ModifyReturnValue(method = "getEyeY()D", at = @At("RETURN"))
    private double mca$scalePlayerEyeY(double original) {
        float scale = mca$getPlayerEyeHeightScale();
        if (scale == 1.0F) {
            return original;
        }

        Player player = (Player) (Object) this;
        double baseY = player.getY();
        return baseY + (original - baseY) * scale;
    }

    private float mca$getPlayerEyeHeightScale() {
        if (!((Object) this instanceof Player player)
                || player.getPose() == Pose.SLEEPING
                || !Config.getInstance().scaleEyeHeightWithPlayerHeight
                || Config.getServerConfig().scalePlayerHitboxWithSizeAndWidth) {
            return 1.0F;
        }

        return MCAClient.getGeneticsPlayerData(player.getUUID())
                .map(data -> data.getRawVerticalScaleFactor())
                .orElse(1.0F);
    }
}
