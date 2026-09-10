package net.conczin.mca.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.conczin.mca.MCAClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {
    @ModifyExpressionValue(
            method = "getRenderer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/PlayerSkin;model()Lnet/minecraft/client/resources/PlayerSkin$Model;"
            )
    )
    private PlayerSkin.Model mca$useWideRenderer(
            PlayerSkin.Model original,
            Entity entity
    ) {
        if (entity instanceof AbstractClientPlayer player && MCAClient.useGeneticsRenderer(player.getUUID())) {
            return PlayerSkin.Model.WIDE;
        }
        return original;
    }
}
