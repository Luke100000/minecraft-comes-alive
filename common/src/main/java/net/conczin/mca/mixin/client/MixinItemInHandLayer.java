package net.conczin.mca.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class MixinItemInHandLayer<S extends ArmedEntityRenderState, M extends EntityModel<S> & ArmedModel> {
    @Shadow
    protected abstract void renderArmWithItem(S renderState, ItemStackRenderState itemStackRenderState, HumanoidArm arm, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight);

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V", at = @At("HEAD"), cancellable = true)
    public void mca$injectRender(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, S state, float yRot, float xRot, CallbackInfo ci) {
        if (!(state instanceof VillagerStateHolder holder) || holder.mca$getVisualSnapshot() == null) {
            return;
        }

        if (state.mainArm == HumanoidArm.RIGHT) {
            this.renderArmWithItem(state, state.rightHandItem, HumanoidArm.RIGHT, poseStack, bufferSource, packedLight);
            this.renderArmWithItem(state, state.leftHandItem, HumanoidArm.LEFT, poseStack, bufferSource, packedLight);
        } else {
            this.renderArmWithItem(state, state.leftHandItem, HumanoidArm.LEFT, poseStack, bufferSource, packedLight);
            this.renderArmWithItem(state, state.rightHandItem, HumanoidArm.RIGHT, poseStack, bufferSource, packedLight);
        }

        ci.cancel();
    }
}
