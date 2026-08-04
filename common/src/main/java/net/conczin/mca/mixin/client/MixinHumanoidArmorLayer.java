package net.conczin.mca.mixin.client;

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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HumanoidArmorLayer.class)
public abstract class MixinHumanoidArmorLayer<T extends LivingEntity, A extends HumanoidModel<T>> {
    @Unique
    @Nullable
    private A mca$leggingsModel;
    @Unique
    @Nullable
    private A mca$bodyModel;
    @Unique
    private boolean mca$injectionActive;

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

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"))
    private void mca$selectArmorModels(PoseStack matrices, MultiBufferSource buffers, int light, T entity, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        mca$injectionActive = entity instanceof Player && MCAClient.useGeneticsRenderer(entity.getUUID());
    }

    @Inject(method = "getArmorModel(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/client/model/HumanoidModel;", at = @At("HEAD"), cancellable = true)
    private void mca$useRegisteredArmorModel(EquipmentSlot slot, CallbackInfoReturnable<A> cir) {
        if (mca$injectionActive) {
            cir.setReturnValue(mca$getModel(usesInnerModel(slot)));
        }
    }
}
