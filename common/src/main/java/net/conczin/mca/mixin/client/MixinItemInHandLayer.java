package net.conczin.mca.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class MixinItemInHandLayer {
    @Shadow
    protected abstract void renderArmWithItem(LivingEntity livingEntity, ItemStack itemStack, ItemDisplayContext itemDisplayContext, HumanoidArm humanoidArm, PoseStack poseStack, MultiBufferSource multiBufferSource, int i);

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"), cancellable = true)
    public void mca$injectRender(PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i, LivingEntity livingEntity, float f, float g, float h, float j, float k, float l, CallbackInfo ci) {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof net.conczin.mca.client.gui.VillagerEditorScreen) {
            ci.cancel();
            return;
        }
        if (livingEntity instanceof VillagerLike<?>) {
            boolean bl = livingEntity.getMainArm() == HumanoidArm.RIGHT;
            ItemStack itemStack = bl ? livingEntity.getOffhandItem() : livingEntity.getMainHandItem();
            ItemStack itemStack2 = bl ? livingEntity.getMainHandItem() : livingEntity.getOffhandItem();
            if (!itemStack.isEmpty() || !itemStack2.isEmpty()) {
                matrixStack.pushPose();
                this.renderArmWithItem(livingEntity, itemStack2, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, HumanoidArm.RIGHT, matrixStack, vertexConsumerProvider, i);
                this.renderArmWithItem(livingEntity, itemStack, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, HumanoidArm.LEFT, matrixStack, vertexConsumerProvider, i);
                matrixStack.popPose();
            }
            ci.cancel();
        }
    }
}
