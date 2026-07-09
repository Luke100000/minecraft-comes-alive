package net.conczin.mca.mixin.client;

import net.conczin.mca.Config;
import net.conczin.mca.MCAClient;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @Shadow
    public abstract UUID getUUID();

    @Inject(method = "getEyeHeight()F", at = @At("RETURN"), cancellable = true)
    private void onGetEyeHeight(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof Player player
                && player.getPose() != Pose.SLEEPING
                && Config.getInstance().scaleEyeHeightWithPlayerHeight
                && !Config.getServerConfig().scalePlayerHitboxWithSizeAndWidth) {
            MCAClient.getPlayerData(getUUID())
                    .ifPresent(villager -> cir.setReturnValue(cir.getReturnValueF() * villager.getVerticalScaleFactor()));
        }
    }
}
