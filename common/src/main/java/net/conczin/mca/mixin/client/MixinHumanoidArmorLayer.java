package net.conczin.mca.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.model.PlayerArmorExtendedModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HumanoidArmorLayer.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class MixinHumanoidArmorLayer {
    @Shadow
    @Final
    private EquipmentLayerRenderer equipmentRenderer;

    @Shadow
    protected abstract void setPartVisibility(HumanoidModel model, EquipmentSlot slot);

    @Unique
    private HumanoidModel<?> mca$leggingsModel;
    @Unique
    private HumanoidModel<?> mca$bodyModel;

    @Unique
    private HumanoidModel<?> mca$createModel(float dilation) {
        return new PlayerArmorExtendedModel<>(LayerDefinition.create(VillagerEntityModelMCA.armorData(new CubeDeformation(dilation)), 64, 32).bakeRoot());
    }

    @Unique
    private HumanoidModel<?> mca$getFallbackModel(EquipmentSlot slot) {
        if (slot == EquipmentSlot.LEGS) {
            if (mca$leggingsModel == null) {
                mca$leggingsModel = mca$createModel(0.5F);
            }
            return mca$leggingsModel;
        }
        if (mca$bodyModel == null) {
            mca$bodyModel = mca$createModel(1.0F);
        }
        return mca$bodyModel;
    }

    @Inject(method = "getArmorModel(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/client/model/HumanoidModel;", at = @At("HEAD"), cancellable = true)
    private void mca$injectGetArmorModel(HumanoidRenderState state, EquipmentSlot slot, CallbackInfoReturnable<HumanoidModel> cir) {
        if (state instanceof VillagerStateHolder holder && holder.mca$isGeneticsRendererActive()) {
            HumanoidModel<?> model = mca$getFallbackModel(slot);
            cir.setReturnValue((HumanoidModel) model);
        }
    }

    @Inject(method = "getArmorModel(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/client/model/HumanoidModel;", at = @At("RETURN"), cancellable = true)
    private void mca$injectGetArmorModelFallback(HumanoidRenderState state, EquipmentSlot slot, CallbackInfoReturnable<HumanoidModel> cir) {
        if (cir.getReturnValue() == null) {
            HumanoidModel<?> model = mca$getFallbackModel(slot);
            cir.setReturnValue((HumanoidModel) model);
        }
    }

    @ModifyVariable(
            method = "renderArmorPiece",
            at = @At("HEAD"),
            argsOnly = true
    )
    private HumanoidModel mca$replaceNullArmorModel(
            HumanoidModel model,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            ItemStack armorItem,
            EquipmentSlot slot,
            int packedLight,
            HumanoidModel originalModel
    ) {
        return model == null ? (HumanoidModel) mca$getFallbackModel(slot) : model;
    }

    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void mca$renderNullArmorModel(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            ItemStack armorItem,
            EquipmentSlot slot,
            int packedLight,
            HumanoidModel model,
            CallbackInfo ci
    ) {
        if (model != null) {
            return;
        }

        Equippable equippable = armorItem.get(DataComponents.EQUIPPABLE);
        if (equippable == null || !HumanoidArmorLayer.shouldRender(armorItem, slot)) {
            return;
        }

        HumanoidModel fallback = (HumanoidModel) mca$getFallbackModel(slot);
        if (fallback == null) {
            ci.cancel();
            return;
        }
        ((HumanoidModel) ((HumanoidArmorLayer) (Object) this).getParentModel()).copyPropertiesTo(fallback);
        this.setPartVisibility(fallback, slot);
        EquipmentClientInfo.LayerType layerType = slot == EquipmentSlot.LEGS
                ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS
                : EquipmentClientInfo.LayerType.HUMANOID;
        this.equipmentRenderer.renderLayers(layerType, equippable.assetId().orElseThrow(), fallback, armorItem, poseStack, bufferSource, packedLight);
        ci.cancel();
    }
}
