package net.conczin.mca.neoforge.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.render.MCAPlayerArmRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class MixinAvatarRenderer {
    @Inject(
            method = "renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;ZLnet/minecraft/client/player/AbstractClientPlayer;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mca$injectRenderRightHand(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            Identifier skinTexture,
            boolean hasSleeve,
            AbstractClientPlayer player,
            CallbackInfo ci
    ) {
        if (((MCAPlayerArmRenderer) (Object) this).mca$renderHand(player, poseStack, submitNodeCollector, lightCoords, true, hasSleeve)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;ZLnet/minecraft/client/player/AbstractClientPlayer;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mca$injectRenderLeftHand(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            Identifier skinTexture,
            boolean hasSleeve,
            AbstractClientPlayer player,
            CallbackInfo ci
    ) {
        if (((MCAPlayerArmRenderer) (Object) this).mca$renderHand(player, poseStack, submitNodeCollector, lightCoords, false, hasSleeve)) {
            ci.cancel();
        }
    }
}
