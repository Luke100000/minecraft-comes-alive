package net.mca.mixin.client;

import net.mca.Config;
import net.mca.MCAClient;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class MixinEntity {
    @Inject(
            method = "getActiveEyeHeight(Lnet/minecraft/entity/EntityPose;Lnet/minecraft/entity/EntityDimensions;)F",
            at = @At("RETURN"),
            cancellable = true
    )
    private void mca$getActiveEyeHeight(EntityPose pose, EntityDimensions dimensions, CallbackInfoReturnable<Float> cir) {
        if (pose == EntityPose.SLEEPING
                || !Config.getInstance().scaleEyeHeightWithPlayerHeight
                || Config.getServerConfig().scalePlayerHitboxWithSizeAndWidth) {
            return;
        }

        PlayerEntity player = (PlayerEntity) (Object) this;
        MCAClient.getGeneticsPlayerData(player.getUuid()).ifPresent(data ->
                cir.setReturnValue(cir.getReturnValueF() * data.getRawVerticalScaleFactor()));
    }
}
