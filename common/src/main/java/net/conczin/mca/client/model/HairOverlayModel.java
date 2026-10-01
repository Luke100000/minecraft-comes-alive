package net.conczin.mca.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public final class HairOverlayModel<T extends LivingEntity> extends EntityModel<T> implements VillagerLayerModel<T> {
    private static final float SURFACE_SEPARATION = 0.005F;
    private final ModelPart head;
    private final ModelPart hat;
    private final ModelPart body;
    private final ModelPart jacket;
    private final ModelPart breastTransform;
    private final ModelPart breasts;
    private final List<ModelPart> breastParts;

    public HairOverlayModel(ModelPart root, float clothingDilation) {
        head = root.getChild(PartNames.HEAD);
        hat = root.getChild(PartNames.HAT);
        body = root.getChild(PartNames.BODY);
        jacket = root.getChild(PartNames.JACKET);
        breastTransform = body.getChild(MCAModelGeometry.BREAST_TRANSFORM);
        breasts = breastTransform.getChild(MCAModelGeometry.BREASTS);
        breastParts = List.of(breasts);

        // Inflate the two UV shells once around the same centre. The shared
        // breast pivot supplies the full morphology pose on every render.
        float innerDilation = clothingDilation + MCAModelGeometry.BREAST_WEAR_DILATION + SURFACE_SEPARATION;
        sizeProjection(breasts.getChild(MCAModelGeometry.BREAST_HAIR_SURFACE), innerDilation);
        sizeProjection(breasts.getChild(MCAModelGeometry.BREASTPLATE), innerDilation + SURFACE_SEPARATION);
    }

    private static void sizeProjection(ModelPart shell, float dilation) {
        shell.xScale = 1.0F + 2.0F * dilation / MCAModelGeometry.BREAST_WIDTH;
        shell.yScale = 1.0F + 2.0F * dilation / MCAModelGeometry.BREAST_HEIGHT;
        shell.zScale = 1.0F + 2.0F * dilation / MCAModelGeometry.BREAST_DEPTH;
        shell.setPos(
                MCAModelGeometry.BREAST_CENTER_X * (1.0F - shell.xScale),
                MCAModelGeometry.BREAST_CENTER_Y * (1.0F - shell.yScale),
                0.0F
        );
    }

    @Override
    public void copyFrom(PlayerModel<T> parent) {
        head.copyFrom(parent.head);
        hat.copyFrom(parent.hat);
        body.copyFrom(parent.body);
        jacket.copyFrom(parent.body);

        head.visible = parent.head.visible;
        hat.visible = parent.head.visible && parent.hat.visible;
        body.visible = parent.body.visible;
        jacket.visible = parent.body.visible;
        breastTransform.visible = parent.body.visible;
        breasts.visible = parent.body.visible;
    }

    public void applyMorphology(VillagerLike<?> villager) {
        MCAModelMorphology.applyBreastDimensions(villager, breastTransform, breasts, breastParts);
        breastTransform.visible &= body.visible;
        breasts.visible &= body.visible;
    }

    @Override
    public void setupAnim(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        head.render(matrices, vertices, light, overlay, color);
        hat.render(matrices, vertices, light, overlay, color);
        body.render(matrices, vertices, light, overlay, color);
        jacket.render(matrices, vertices, light, overlay, color);
    }
}
