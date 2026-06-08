package net.mca.mixin.client;

import net.mca.MCAClient;
import net.mca.client.model.PlayerArmorExtendedModel;
import net.mca.client.model.VillagerEntityModelMCA;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArmorFeatureRenderer.class)
public abstract class MixinArmorFeatureRenderer<T extends LivingEntity, A extends BipedEntityModel<T>> {
    @Shadow
    protected abstract boolean usesInnerModel(EquipmentSlot slot);

    protected boolean mca$injectionActive;
    protected final A mca$leggingsModel = createModel(0.5F);
    protected final A mca$bodyModel = createModel(1.0F);

    @SuppressWarnings("unchecked")
    private A createModel(float dilation) {
        return (A)new PlayerArmorExtendedModel<T>(TexturedModelData.of(VillagerEntityModelMCA.armorData(new Dilation(dilation)), 64, 32).createModel());
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"))
    public void render(MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, T livingEntity, float f, float g, float h, float j, float k, float l, CallbackInfo ci) {
        mca$injectionActive = livingEntity instanceof PlayerEntity && MCAClient.useGeneticsRenderer(livingEntity.getUuid());
    }

    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void getArmor(EquipmentSlot slot, CallbackInfoReturnable<A> cir) {
        if (mca$injectionActive) {
            cir.setReturnValue(this.usesInnerModel(slot) ? mca$leggingsModel : mca$bodyModel);
        }
    }
}
