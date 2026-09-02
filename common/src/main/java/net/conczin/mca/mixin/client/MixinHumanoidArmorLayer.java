package net.conczin.mca.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.MCAModelLayers;
import net.conczin.mca.client.model.MCAArmorModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HumanoidArmorLayer.class)
public abstract class MixinHumanoidArmorLayer<T extends LivingEntity, A extends HumanoidModel<T>> {
    @Unique
    @Nullable
    private A mca$leggingsModel;
    @Unique
    @Nullable
    private A mca$bodyModel;
    @Shadow
    protected abstract boolean usesInnerModel(EquipmentSlot slot);

    @Unique
    @SuppressWarnings("unchecked")
    private A mca$getModel(boolean inner) {
        A current = inner ? mca$leggingsModel : mca$bodyModel;
        if (current != null) {
            return current;
        }

        current = (A) new MCAArmorModel<T>(
                Minecraft.getInstance().getEntityModels().bakeLayer(
                        inner ? MCAModelLayers.PLAYER_INNER_ARMOR : MCAModelLayers.PLAYER_OUTER_ARMOR
                )
        );
        if (inner) {
            mca$leggingsModel = current;
        } else {
            mca$bodyModel = current;
        }
        return current;
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
            PoseStack matrices,
            MultiBufferSource buffers,
            int light,
            T entity,
            float limbAngle,
            float limbDistance,
            float tickDelta,
            float animationProgress,
            float headYaw,
            float headPitch
    ) {
        if (entity instanceof Player && MCAClient.useGeneticsRenderer(entity.getUUID())) {
            return mca$getModel(usesInnerModel(slot));
        }
        return original.call(layer, slot);
    }
}
