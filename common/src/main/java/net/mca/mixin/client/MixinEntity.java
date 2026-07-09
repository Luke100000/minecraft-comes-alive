package net.mca.mixin.client;

import net.mca.Config;
import net.mca.MCAClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @Shadow
    public abstract UUID getUuid();

    @Inject(method = "getEyeHeight(Lnet/minecraft/entity/EntityPose;)F", at = @At("RETURN"), cancellable = true)
    private void mca$getEyeHeight(EntityPose pose, CallbackInfoReturnable<Float> cir) {
        if ((Object)this instanceof PlayerEntity
                && pose != EntityPose.SLEEPING
                && Config.getInstance().scaleEyeHeightWithPlayerHeight
                && !Config.getServerConfig().scalePlayerHitboxWithSizeAndWidth) {
            MCAClient.getPlayerData(getUuid())
                    .ifPresent(data -> cir.setReturnValue(cir.getReturnValueF() * data.getVerticalScaleFactor()));
        }
    }
}
