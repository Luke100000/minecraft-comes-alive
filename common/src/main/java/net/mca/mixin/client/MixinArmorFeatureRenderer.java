package net.mca.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.mca.MCAClient;
import net.mca.client.model.PlayerArmorExtendedModel;
import net.mca.client.model.VillagerEntityModelMCA;
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
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HumanoidArmorLayer.class)
public abstract class MixinArmorFeatureRenderer<T extends LivingEntity, A extends HumanoidModel<T>> {
    @Shadow
    protected abstract boolean usesInnerModel(EquipmentSlot slot);

    protected final A mca$leggingsModel = createModel(0.5F);
    protected final A mca$bodyModel = createModel(1.0F);

    @SuppressWarnings("unchecked")
    private A createModel(float dilation) {
        return (A)new PlayerArmorExtendedModel<T>(LayerDefinition.create(VillagerEntityModelMCA.armorData(new CubeDeformation(dilation)), 64, 32).bakeRoot());
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
            float limbAngle,
            float limbDistance,
            float tickDelta,
            float animationProgress,
            float headYaw,
            float headPitch
    ) {
        if (livingEntity instanceof Player && MCAClient.useGeneticsRenderer(livingEntity.getUUID())) {
            return this.usesInnerModel(slot) ? mca$leggingsModel : mca$bodyModel;
        }
        return original.call(layer, slot);
    }
}
