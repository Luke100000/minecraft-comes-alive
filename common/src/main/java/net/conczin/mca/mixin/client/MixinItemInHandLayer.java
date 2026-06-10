package net.conczin.mca.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class MixinItemInHandLayer<S extends ArmedEntityRenderState, M extends EntityModel<S> & ArmedModel<S>> {
    @Shadow
    protected abstract void submitArmWithItem(
            S state,
            net.minecraft.client.renderer.item.ItemStackRenderState item,
            net.minecraft.world.item.ItemStack itemStack,
            HumanoidArm arm,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords
    );

    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mca$submitVillagerHands(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            S state,
            float yRot,
            float xRot,
            CallbackInfo ci
    ) {
        if (!(state instanceof VillagerStateHolder holder) || holder.mca$getVisualSnapshot() == null) {
            return;
        }

        if (state.mainArm == HumanoidArm.RIGHT) {
            this.submitArmWithItem(state, state.rightHandItemState, state.rightHandItemStack, HumanoidArm.RIGHT, poseStack, submitNodeCollector, lightCoords);
            this.submitArmWithItem(state, state.leftHandItemState, state.leftHandItemStack, HumanoidArm.LEFT, poseStack, submitNodeCollector, lightCoords);
        } else {
            this.submitArmWithItem(state, state.leftHandItemState, state.leftHandItemStack, HumanoidArm.LEFT, poseStack, submitNodeCollector, lightCoords);
            this.submitArmWithItem(state, state.rightHandItemState, state.rightHandItemStack, HumanoidArm.RIGHT, poseStack, submitNodeCollector, lightCoords);
        }

        ci.cancel();
    }
}
