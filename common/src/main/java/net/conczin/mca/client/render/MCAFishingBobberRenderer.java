package net.conczin.mca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.entity.MCAFishingBobberEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

public final class MCAFishingBobberRenderer extends EntityRenderer<MCAFishingBobberEntity> {
    private static final ResourceLocation TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/fishing_hook.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutout(TEXTURE_LOCATION);

    public MCAFishingBobberRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(MCAFishingBobberEntity bobber, float yaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        VillagerEntityMCA owner = bobber.getVillagerOwner();
        if (owner == null) {
            return;
        }

        poseStack.pushPose();
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

        Vec3 handPosition = getVillagerHandPos(owner, partialTicks);
        Vec3 bobberPosition = bobber.getPosition(partialTicks).add(0.0, 0.25, 0.0);
        float x = (float) (handPosition.x - bobberPosition.x);
        float y = (float) (handPosition.y - bobberPosition.y);
        float z = (float) (handPosition.z - bobberPosition.z);
        VertexConsumer lineBuffer = bufferSource.getBuffer(RenderType.lineStrip());
        PoseStack.Pose linePose = poseStack.last();

        for (int segment = 0; segment <= 16; segment++) {
            stringVertex(x, y, z, lineBuffer, linePose, fraction(segment, 16), fraction(segment + 1, 16));
        }

        poseStack.popPose();
        super.render(bobber, yaw, partialTicks, poseStack, bufferSource, packedLight);
    }

    private Vec3 getVillagerHandPos(VillagerEntityMCA owner, float partialTicks) {
        int side = owner.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        float bodyYaw = Mth.lerp(partialTicks, owner.yBodyRotO, owner.yBodyRot) * Mth.DEG_TO_RAD;
        double sin = Mth.sin(bodyYaw);
        double cos = Mth.cos(bodyYaw);
        float scale = owner.getScale();
        double sideOffset = side * 0.35 * scale;
        double forwardOffset = 0.8 * scale;
        float crouchOffset = owner.isCrouching() ? -0.1875F : 0.0F;

        return owner.getEyePosition(partialTicks).add(
                -cos * sideOffset - sin * forwardOffset,
                crouchOffset - 0.45 * scale,
                -sin * sideOffset + cos * forwardOffset
        );
    }

    private static float fraction(int value, int total) {
        return (float) value / total;
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

    private static void stringVertex(float x, float y, float z, VertexConsumer consumer, PoseStack.Pose pose,
                                     float fraction, float nextFraction) {
        float vertexX = x * fraction;
        float vertexY = y * (fraction * fraction + fraction) * 0.5F + 0.25F;
        float vertexZ = z * fraction;
        float normalX = x * nextFraction - vertexX;
        float normalY = y * (nextFraction * nextFraction + nextFraction) * 0.5F + 0.25F - vertexY;
        float normalZ = z * nextFraction - vertexZ;
        float normalLength = Mth.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        normalX /= normalLength;
        normalY /= normalLength;
        normalZ /= normalLength;
        consumer.addVertex(pose, vertexX, vertexY, vertexZ)
                .setColor(-16777216)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    @Override
    public ResourceLocation getTextureLocation(MCAFishingBobberEntity bobber) {
        return TEXTURE_LOCATION;
    }
}
