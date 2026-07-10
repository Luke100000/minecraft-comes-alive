package net.conczin.mca.fabric.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.ducks.client.PlayerRendererMCA;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class MixinPlayerRenderer {
    @Inject(
            method = "renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mca$injectRenderRightHand(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            Identifier skinTexture,
            boolean hasSleeve,
            CallbackInfo ci
    ) {
        if (((PlayerRendererMCA) this).mca$renderHand(Minecraft.getInstance().player, poseStack, submitNodeCollector, lightCoords, true, hasSleeve)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mca$injectRenderLeftHand(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            Identifier skinTexture,
            boolean hasSleeve,
            CallbackInfo ci
    ) {
        if (((PlayerRendererMCA) this).mca$renderHand(Minecraft.getInstance().player, poseStack, submitNodeCollector, lightCoords, false, hasSleeve)) {
            ci.cancel();
        }
    }
}
