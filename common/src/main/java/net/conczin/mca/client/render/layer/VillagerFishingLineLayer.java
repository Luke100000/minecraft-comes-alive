package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.entity.MCAFishingBobberEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class VillagerFishingLineLayer
        extends RenderLayer<VillagerEntityMCA, VillagerEntityModelMCA<VillagerEntityMCA>> {
    private static final double BOBBER_SEARCH_RADIUS = 32.0;

    public VillagerFishingLineLayer(
            RenderLayerParent<VillagerEntityMCA, VillagerEntityModelMCA<VillagerEntityMCA>> parent
    ) {
        super(parent);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            VillagerEntityMCA villager,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        if (!(villager.getItemInHand(villager.getDominantHand()).getItem() instanceof FishingRodItem)) {
            return;
        }

        MCAFishingBobberEntity bobber = findOwnedBobber(villager);
        if (bobber == null) {
            return;
        }

        Vector3f hand = getRenderedRodOrigin(villager);
        Vector3f hook = getHookInCurrentModelSpace(poseStack, bobber, partialTicks);
        renderLine(poseStack, bufferSource, hook, hand);
    }

    private Vector3f getRenderedRodOrigin(VillagerEntityMCA villager) {
        PoseStack handPose = new PoseStack();
        HumanoidArm arm = villager.getMainArm();
        getParentModel().translateToHand(arm, handPose);
        handPose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        handPose.mulPose(Axis.YP.rotationDegrees(180.0F));
        handPose.translate((arm == HumanoidArm.LEFT ? -1 : 1) / 16.0F, 0.125F, -0.625F);

        Vector3f origin = new Vector3f();
        handPose.last().pose().transformPosition(origin);
        return origin;
    }

    private static Vector3f getHookInCurrentModelSpace(
            PoseStack poseStack,
            MCAFishingBobberEntity bobber,
            float partialTicks
    ) {
        Vec3 hookWorld = bobber.getPosition(partialTicks).add(0.0, 0.25, 0.0);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vector3f hookRenderSpace = new Vector3f(
                (float) (hookWorld.x - camera.x),
                (float) (hookWorld.y - camera.y),
                (float) (hookWorld.z - camera.z)
        );

        Matrix4f inverseVillagerPose = new Matrix4f(poseStack.last().pose()).invert();
        inverseVillagerPose.transformPosition(hookRenderSpace);
        return hookRenderSpace;
    }

    private static void renderLine(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Vector3f hook,
            Vector3f hand
    ) {
        float dx = hand.x() - hook.x();
        float dy = hand.y() - hook.y();
        float dz = hand.z() - hook.z();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lineStrip());
        poseStack.pushPose();
        poseStack.translate(hook.x(), hook.y() - 0.25F, hook.z());
        PoseStack.Pose pose = poseStack.last();

        for (int segment = 0; segment <= 16; segment++) {
            float fraction = (float) segment / 16.0F;
            float nextFraction = (float) (segment + 1) / 16.0F;
            FishingHookRenderer.stringVertex(dx, dy, dz, consumer, pose, fraction, nextFraction);
        }
        poseStack.popPose();
    }

    @Nullable
    private static MCAFishingBobberEntity findOwnedBobber(VillagerEntityMCA villager) {
        return villager.level()
                .getEntitiesOfClass(
                        MCAFishingBobberEntity.class,
                        villager.getBoundingBox().inflate(BOBBER_SEARCH_RADIUS),
                        bobber -> !bobber.isRemoved() && bobber.getVillagerOwner() == villager
                )
                .stream()
                .findFirst()
                .orElse(null);
    }
}
