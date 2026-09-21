package net.conczin.mca.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public final class HairOverlayModel<T extends LivingEntity> extends EntityModel<T> implements VillagerLayerModel<T> {
    private static final float SURFACE_SEPARATION = 0.005F;
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart hat;
    private final ModelPart body;
    private final ModelPart breastTransform;
    private final ModelPart breasts;
    private final List<ModelPart> breastParts;
    private final float breastOffsetX;
    private final float breastOffsetY;
    private final float breastOffsetZ;

    public HairOverlayModel(ModelPart root, float clothingDilation) {
        this.root = root;
        head = root.getChild("head");
        hat = root.getChild("hat");
        body = root.getChild("body");
        breastTransform = body.getChild(MCAModelGeometry.BREAST_TRANSFORM);
        breasts = breastTransform.getChild(MCAModelGeometry.BREASTS);
        breastParts = List.of(breasts);

        // Both hair textures must clear the outer clothing surface without
        // inheriting the much larger spacing used by the torso hair shells.
        float projectionDilation = clothingDilation + MCAModelGeometry.BREAST_WEAR_DILATION + SURFACE_SEPARATION;
        float scaleX = 1.0F + projectionDilation * 2.0F / MCAModelGeometry.BREAST_WIDTH;
        float scaleY = 1.0F + projectionDilation * 2.0F / MCAModelGeometry.BREAST_HEIGHT;
        float scaleZ = 1.0F + projectionDilation * 2.0F / MCAModelGeometry.BREAST_DEPTH;
        breasts.xScale = scaleX;
        breasts.yScale = scaleY;
        breasts.zScale = scaleZ;

        // The outer texture follows the same pose through the breast parent.
        // Use relative dilation so its inherited scale is applied only once.
        ModelPart breastWear = breasts.getChild(MCAModelGeometry.BREASTPLATE);
        float wearDilation = projectionDilation + SURFACE_SEPARATION;
        breastWear.xScale = (MCAModelGeometry.BREAST_WIDTH + 2.0F * wearDilation)
                / (MCAModelGeometry.BREAST_WIDTH + 2.0F * projectionDilation);
        breastWear.yScale = (MCAModelGeometry.BREAST_HEIGHT + 2.0F * wearDilation)
                / (MCAModelGeometry.BREAST_HEIGHT + 2.0F * projectionDilation);
        breastWear.zScale = (MCAModelGeometry.BREAST_DEPTH + 2.0F * wearDilation)
                / (MCAModelGeometry.BREAST_DEPTH + 2.0F * projectionDilation);
        breastWear.setPos(
                MCAModelGeometry.BREAST_CENTER_X * (1.0F - breastWear.xScale),
                MCAModelGeometry.BREAST_CENTER_Y * (1.0F - breastWear.yScale),
                0.0F
        );

        float localOffsetX = MCAModelGeometry.BREAST_CENTER_X * (1.0F - scaleX);
        float localOffsetY = MCAModelGeometry.BREAST_CENTER_Y * (1.0F - scaleY);
        breastOffsetX = localOffsetX;
        breastOffsetY = (float) Math.cos(MCAModelMorphology.BREAST_ROTATION_X) * localOffsetY;
        breastOffsetZ = (float) Math.sin(MCAModelMorphology.BREAST_ROTATION_X) * localOffsetY;
    }

    @Override
    public void copyFrom(PlayerModel<T> parent) {
        head.copyFrom(parent.head);
        hat.copyFrom(parent.hat);
        body.copyFrom(parent.body);

        head.visible = parent.head.visible;
        hat.visible = parent.head.visible && parent.hat.visible;
        body.visible = parent.body.visible;
        breastTransform.visible = parent.body.visible;
        breasts.visible = parent.body.visible;
    }

    public void applyMorphology(VillagerLike<?> villager) {
        MCAModelMorphology.applyBreastDimensions(villager, breastTransform, breasts, breastParts);
        breasts.x += breastOffsetX;
        breasts.y += breastOffsetY;
        breasts.z += breastOffsetZ;
        breastTransform.visible &= body.visible;
        breasts.visible &= body.visible;
    }

    @Override
    public void setupAnim(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        root.render(matrices, vertices, light, overlay, color);
    }
}
