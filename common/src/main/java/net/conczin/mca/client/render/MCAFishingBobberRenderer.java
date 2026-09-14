package net.conczin.mca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.entity.MCAFishingBobberEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

public final class MCAFishingBobberRenderer extends EntityRenderer<MCAFishingBobberEntity, FishingHookRenderState> {
    private static final Identifier TEXTURE_LOCATION = Identifier.withDefaultNamespace("textures/entity/fishing/fishing_hook.png");
    private static final RenderType RENDER_TYPE = RenderTypes.entityCutoutCull(TEXTURE_LOCATION);

    public MCAFishingBobberRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(MCAFishingBobberEntity bobber, Frustum culler, double camX, double camY, double camZ) {
        return super.shouldRender(bobber, culler, camX, camY, camZ) && bobber.getVillagerOwner() != null;
    }

    @Override
    public void submit(FishingHookRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(camera.orientation);
        submitNodeCollector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            vertex(buffer, pose, state.lightCoords, 0.0F, 0, 0, 1);
            vertex(buffer, pose, state.lightCoords, 1.0F, 0, 1, 1);
            vertex(buffer, pose, state.lightCoords, 1.0F, 1, 1, 0);
            vertex(buffer, pose, state.lightCoords, 0.0F, 1, 0, 0);
        });
        poseStack.popPose();

        float x = (float) state.lineOriginOffset.x;
        float y = (float) state.lineOriginOffset.y;
        float z = (float) state.lineOriginOffset.z;
        float width = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
            for (int segment = 0; segment < 16; segment++) {
                float current = fraction(segment, 16);
                float next = fraction(segment + 1, 16);
                stringVertex(x, y, z, buffer, pose, current, next, width);
                stringVertex(x, y, z, buffer, pose, next, current, width);
            }
        });

        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    @Override
    public FishingHookRenderState createRenderState() {
        return new FishingHookRenderState();
    }

    @Override
    public void extractRenderState(MCAFishingBobberEntity bobber, FishingHookRenderState state, float partialTicks) {
        super.extractRenderState(bobber, state, partialTicks);
        VillagerEntityMCA owner = bobber.getVillagerOwner();
        if (owner == null) {
            state.lineOriginOffset = Vec3.ZERO;
            return;
        }

        Vec3 handPosition = getVillagerHandPos(owner, partialTicks);
        Vec3 bobberPosition = bobber.getPosition(partialTicks).add(0.0, 0.25, 0.0);
        state.lineOriginOffset = handPosition.subtract(bobberPosition);
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
                                     float fraction, float nextFraction, float width) {
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
                .setNormal(pose, normalX, normalY, normalZ)
                .setLineWidth(width);
    }

    @Override
    protected boolean affectedByCulling(MCAFishingBobberEntity bobber) {
        return false;
    }
}
