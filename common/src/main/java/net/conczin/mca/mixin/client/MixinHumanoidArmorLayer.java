package net.conczin.mca.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.model.PlayerArmorExtendedModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.LivingEntityRenderContext;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HumanoidArmorLayer.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class MixinHumanoidArmorLayer {
    @Unique
    protected final HumanoidModel<?> mca$leggingsModel = mca$createModel(0.5F, false);
    @Unique
    protected final HumanoidModel<?> mca$bodyModel = mca$createModel(1.0F, false);
    @Unique
    protected final HumanoidModel<?> mca$slimLeggingsModel = mca$createModel(0.5F, true);
    @Unique
    protected final HumanoidModel<?> mca$slimBodyModel = mca$createModel(1.0F, true);
    @Unique
    protected boolean mca$injectionActive;

    @Shadow
    protected abstract boolean usesInnerModel(EquipmentSlot slot);

    @Unique
    private HumanoidModel<?> mca$createModel(float dilation, boolean slim) {
        return new PlayerArmorExtendedModel<>(LayerDefinition.create(VillagerEntityModelMCA.armorData(new CubeDeformation(dilation), slim), 64, 64).bakeRoot());
    }

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V", at = @At("HEAD"))
    private void mca$injectSubmit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, HumanoidRenderState state, float yRot, float xRot, CallbackInfo ci) {
        mca$injectionActive = LivingEntityRenderContext.isGeneticsRendererActive();
    }

    @Inject(method = "getArmorModel(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/client/model/HumanoidModel;", at = @At("HEAD"), cancellable = true)
    private void mca$injectGetArmorModel(HumanoidRenderState state, EquipmentSlot slot, CallbackInfoReturnable<HumanoidModel> cir) {
        if (mca$injectionActive) {
            boolean slim = state instanceof AvatarRenderState avatar && avatar.skin.model() == PlayerModelType.SLIM;
            HumanoidModel<?> model = this.usesInnerModel(slot)
                ? (slim ? mca$slimLeggingsModel : mca$leggingsModel)
                : (slim ? mca$slimBodyModel : mca$bodyModel);
            cir.setReturnValue((HumanoidModel) model);
        }
    }
}
