package net.conczin.mca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.entity.MCAFishingBobberEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class MCAFishingBobberRenderer extends EntityRenderer<MCAFishingBobberEntity> {
    private static final ResourceLocation TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/fishing_hook.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutout(TEXTURE_LOCATION);

    public MCAFishingBobberRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(MCAFishingBobberEntity bobber, float yaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        PoseStack.Pose hookPose = poseStack.last();
        VertexConsumer hookBuffer = bufferSource.getBuffer(RENDER_TYPE);
        vertex(hookBuffer, hookPose, packedLight, 0.0F, 0, 0, 1);
        vertex(hookBuffer, hookPose, packedLight, 1.0F, 0, 1, 1);
        vertex(hookBuffer, hookPose, packedLight, 1.0F, 1, 1, 0);
        vertex(hookBuffer, hookPose, packedLight, 0.0F, 1, 0, 0);
        poseStack.popPose();
        super.render(bobber, yaw, partialTicks, poseStack, bufferSource, packedLight);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int packedLight,
                               float x, int y, int u, int v) {
        consumer.addVertex(pose, x - 0.5F, y - 0.5F, 0.0F)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(MCAFishingBobberEntity bobber) {
        return TEXTURE_LOCATION;
    }
}
