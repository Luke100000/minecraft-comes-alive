package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.VillagerRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class VillagerFishingLineLayer extends RenderLayer<VillagerRenderState, VillagerEntityModelMCA> {
    public VillagerFishingLineLayer(RenderLayerParent<VillagerRenderState, VillagerEntityModelMCA> parent) {
        super(parent);
    }

    @Override
    public void submit(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            VillagerRenderState state,
            float yRot,
            float xRot
    ) {
        if (!(state.getMainHandItemStack().getItem() instanceof FishingRodItem) || state.fishingHookPosition == null) {
            return;
        }

        Vector3f hand = getRenderedRodOrigin(state);
        Vector3f hook = getHookInCurrentModelSpace(poseStack, state.fishingHookPosition);
        renderLine(poseStack, submitNodeCollector, hook, hand);
    }

    private Vector3f getRenderedRodOrigin(VillagerRenderState state) {
        PoseStack handPose = new PoseStack();
        HumanoidArm arm = state.mainArm;
        getParentModel().translateToHand(state, arm, handPose);
        handPose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        handPose.mulPose(Axis.YP.rotationDegrees(180.0F));

        boolean babyOffset = state.isBaby && state.entityType != EntityType.ARMOR_STAND;
        float offsetX = babyOffset ? 0.0F : 1.0F;
        float offsetY = babyOffset ? 1.0F : 2.0F;
        float offsetZ = babyOffset ? -4.5F : -10.0F;
        handPose.translate((arm == HumanoidArm.LEFT ? -1 : 1) * offsetX / 16.0F, offsetY / 16.0F, offsetZ / 16.0F);

        return handPose.last().pose().transformPosition(new Vector3f());
    }

    private static Vector3f getHookInCurrentModelSpace(PoseStack poseStack, Vec3 hookWorld) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        Vector3f hookRenderSpace = new Vector3f(
                (float) (hookWorld.x - camera.x),
                (float) (hookWorld.y - camera.y),
                (float) (hookWorld.z - camera.z)
        );

        Matrix4f inverseVillagerPose = new Matrix4f(poseStack.last().pose()).invert();
        return inverseVillagerPose.transformPosition(hookRenderSpace);
    }

    private static void renderLine(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            Vector3f hook,
            Vector3f hand
    ) {
        float dx = hand.x() - hook.x();
        float dy = hand.y() - hook.y();
        float dz = hand.z() - hook.z();
        float width = Minecraft.getInstance().gameRenderer.getGameRenderState().windowRenderState.appropriateLineWidth;

        poseStack.pushPose();
        poseStack.translate(hook.x(), hook.y() - 0.25F, hook.z());
        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
            for (int segment = 0; segment < 16; segment++) {
                float current = fraction(segment, 16);
                float next = fraction(segment + 1, 16);
                FishingHookRenderer.stringVertex(dx, dy, dz, buffer, pose, current, next, width);
                FishingHookRenderer.stringVertex(dx, dy, dz, buffer, pose, next, current, width);
            }
        });
        poseStack.popPose();
    }

    private static float fraction(int value, int total) {
        return (float) value / total;
    }
}
