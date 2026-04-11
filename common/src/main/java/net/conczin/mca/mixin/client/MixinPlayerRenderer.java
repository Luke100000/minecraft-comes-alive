package net.conczin.mca.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.PlayerEntityExtendedModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.SkinLayers3dCompat;
import net.conczin.mca.client.render.PlayerRenderContext;
import net.conczin.mca.client.render.layer.PlayerRingLayer;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class MixinPlayerRenderer {
   @Unique
   private PlayerModel mca$villagerModel;
   @Unique
   private PlayerModel mca$vanillaModel;

   @Unique
   private PlayerModel mca$getModel() {
      return (PlayerModel)((MixinLivingEntityRenderer)this).mca$getModel();
   }

   @Unique
   private void mca$setModel(PlayerModel model) {
      ((MixinLivingEntityRenderer)this).mca$setModel(model);
   }

   @Unique
   private static Avatar mca$getAvatar(AvatarRenderState renderState) {
      if (Minecraft.getInstance().level == null) {
         return null;
      } else {
         return Minecraft.getInstance().level.getEntity(renderState.id) instanceof Avatar avatar ? avatar : null;
      }
   }

   @Unique
   private static PlayerEntityExtendedModel mca$createModel(MeshDefinition data) {
      return new PlayerEntityExtendedModel(LayerDefinition.create(data, 64, 64).bakeRoot());
   }

   @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V", at = @At("TAIL"))
   private void mca$injectInit(Context ctx, boolean slim, CallbackInfo ci) {
      if (MCAClient.isPlayerRendererAllowed()) {
         this.mca$villagerModel = mca$createModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.0F), slim));
         this.mca$vanillaModel = this.mca$getModel();
         SkinLayers3dCompat.setIgnored(this.mca$villagerModel, true);
         SkinLayers3dCompat.clearInjectedMeshes(this.mca$villagerModel);
         SkinLayers3dCompat.clearInjectedMeshes(this.mca$vanillaModel);
      }

      ((MixinLivingEntityRenderer)this).mca$addLayer(new PlayerRingLayer((RenderLayerParent<AvatarRenderState, PlayerModel>)this));
   }

   @Inject(
      method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
      at = @At("TAIL")
   )
   private void mca$injectExtractRenderState(Avatar avatar, AvatarRenderState renderState, float tickDelta, CallbackInfo ci) {
      PlayerRenderContext.setCurrentPlayerUuid(avatar.getUUID());
   }

   @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
   private void mca$injectScale(AvatarRenderState renderState, PoseStack matrices, CallbackInfo ci) {
      Avatar avatar = mca$getAvatar(renderState);
      if (avatar != null && this.mca$villagerModel != null && MCAClient.useGeneticsRenderer(avatar.getUUID())) {
         MCAClient.getPlayerData(avatar.getUUID()).ifPresent(villager -> {
            float height = villager.getRawVerticalScaleFactor();
            float width = villager.getRawHorizontalScaleFactor();
            matrices.scale(width, height, width);
            if (villager.getAgeState() == AgeState.BABY && !renderState.isPassenger) {
               matrices.translate(0.0F, 0.6F, 0.0F);
            }
         });
         this.mca$setModel(this.mca$villagerModel);
         SkinLayers3dCompat.clearInjectedMeshes(this.mca$villagerModel);
      } else if (this.mca$vanillaModel != null) {
         this.mca$setModel(this.mca$vanillaModel);
         SkinLayers3dCompat.clearInjectedMeshes(this.mca$vanillaModel);
      }
   }
}
