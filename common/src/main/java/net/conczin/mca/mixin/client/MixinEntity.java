package net.conczin.mca.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.conczin.mca.Config;
import net.conczin.mca.MCAClient;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @Shadow
    public abstract UUID getUUID();

    @ModifyReturnValue(method = "getEyeHeight()F", at = @At("RETURN"))
    private float mca$scalePlayerEyeHeight(float original) {
        if (!((Object) this instanceof Player player)
                || player.getPose() == Pose.SLEEPING
                || !Config.getInstance().scaleEyeHeightWithPlayerHeight
                || Config.getServerConfig().scalePlayerHitboxWithSizeAndWidth) {
            return original;
        }

        return MCAClient.getGeneticsPlayerData(player.getUUID())
                .map(villager -> original * villager.getRawVerticalScaleFactor())
                .orElse(original);
    }
}
