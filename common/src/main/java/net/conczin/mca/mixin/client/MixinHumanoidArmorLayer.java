package net.conczin.mca.mixin.client;

import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.PlayerArmorExtendedModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.PlayerRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.UUID;

@Mixin(HumanoidArmorLayer.class)
public abstract class MixinHumanoidArmorLayer {
   @Unique
   private final HumanoidModel mca$leggingsModel = mca$createModel(0.5F);
   @Unique
   private final HumanoidModel mca$bodyModel = mca$createModel(1.0F);

   @Shadow
   protected abstract boolean usesInnerModel(EquipmentSlot var1);

   @Unique
   private static HumanoidModel mca$createModel(float dilation) {
      return new PlayerArmorExtendedModel(LayerDefinition.create(VillagerEntityModelMCA.armorData(new CubeDeformation(dilation)), 64, 32).bakeRoot());
   }

   @Unique
   private static Optional<UUID> mca$getPlayerUuid(HumanoidRenderState renderState) {
      if (renderState instanceof AvatarRenderState avatarRenderState) {
         if (Minecraft.getInstance().level == null) {
            return Optional.empty();
         }

         Entity entity = Minecraft.getInstance().level.getEntity(avatarRenderState.id);
         return entity instanceof Avatar avatar ? Optional.of(avatar.getUUID()) : Optional.empty();
      }

      return PlayerRenderContext.currentPlayerUuid();
   }

   @Inject(
      method = "getArmorModel(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/client/model/HumanoidModel;",
      at = @At("HEAD"),
      cancellable = true
   )
   private void mca$injectGetArmorModel(HumanoidRenderState renderState, EquipmentSlot slot, CallbackInfoReturnable<HumanoidModel> cir) {
      mca$getPlayerUuid(renderState)
         .filter(MCAClient::useGeneticsRenderer)
         .ifPresent(uuid -> cir.setReturnValue(this.usesInnerModel(slot) ? this.mca$leggingsModel : this.mca$bodyModel));
   }

   @Inject(
      method = "getArmorModel(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/model/HumanoidModel;)Lnet/minecraft/client/model/Model;",
      at = @At("RETURN"),
      cancellable = true,
      require = 0
   )
   private void mca$injectGetArmorModelFallback(HumanoidRenderState renderState, EquipmentSlot slot, ItemStack stack, HumanoidModel armorModel, CallbackInfoReturnable<Model> cir) {
      if (cir.getReturnValue() == null && armorModel != null) {
         cir.setReturnValue(armorModel);
      }
   }
}
