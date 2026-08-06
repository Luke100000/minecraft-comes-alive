package net.conczin.mca.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.PlayerArmorExtendedModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HumanoidArmorLayer.class)
public abstract class MixinHumanoidArmorLayer<T extends LivingEntity, A extends HumanoidModel<T>> {
    @Unique
    protected final A mca$leggingsModel = mca$createModel(0.5F);
    @Unique
    protected final A mca$bodyModel = mca$createModel(1.0F);
    @Shadow
    protected abstract boolean usesInnerModel(EquipmentSlot slot);

    @Unique
    @SuppressWarnings("unchecked")
    private A mca$createModel(float dilation) {
        return (A) new PlayerArmorExtendedModel<T>(LayerDefinition.create(VillagerEntityModelMCA.armorData(new CubeDeformation(dilation)), 64, 32).bakeRoot());
    }

    @WrapOperation(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;getArmorModel(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/client/model/HumanoidModel;"
            ),
            expect = 4
    )
    private A mca$selectArmorModel(
            HumanoidArmorLayer<?, ?, ?> layer,
            EquipmentSlot slot,
            Operation<A> original,
            PoseStack matrixStack,
            MultiBufferSource vertexConsumerProvider,
            int light,
            T livingEntity,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        if (livingEntity instanceof Player && MCAClient.useGeneticsRenderer(livingEntity.getUUID())) {
            A model = this.usesInnerModel(slot) ? mca$leggingsModel : mca$bodyModel;
            if (model != null) {
                return model;
            }
        }
        return original.call(layer, slot);
    }
}
