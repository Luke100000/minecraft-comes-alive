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
    @ModifyReturnValue(method = "getEyeHeight()F", at = @At("RETURN"))
    private float mca$scalePlayerEyeHeight(float original) {
        if (!((Object) this instanceof PlayerEntity player)
                || player.getPose() == EntityPose.SLEEPING
                || !Config.getInstance().scaleEyeHeightWithPlayerHeight
                || Config.getServerConfig().scalePlayerHitboxWithSizeAndWidth) {
            return original;
        }

        return MCAClient.getGeneticsPlayerData(player.getUuid())
                .map(data -> original * data.getRawVerticalScaleFactor())
                .orElse(original);
    }
}
