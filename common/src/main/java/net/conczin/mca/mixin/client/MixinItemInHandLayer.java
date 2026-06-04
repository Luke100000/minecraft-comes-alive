package net.conczin.mca.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.item.RelationshipItem;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerItemInHandLayer.class)
public abstract class MixinItemInHandLayer {
   @Inject(
      method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void mca$renderRingAsAccessory(
      AvatarRenderState renderState,
      ItemStackRenderState itemStackRenderState,
      ItemStack stack,
      HumanoidArm arm,
      PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector,
      int light,
      CallbackInfo ci
   ) {
      if (!itemStackRenderState.isEmpty() && RelationshipItem.isRing(stack)) {
         ci.cancel();
      }
   }
}
