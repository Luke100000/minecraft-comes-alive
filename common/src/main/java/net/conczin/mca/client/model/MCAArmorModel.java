package net.conczin.mca.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

import static net.conczin.mca.client.model.MCAModelGeometry.BREASTS;
import static net.conczin.mca.client.model.MCAModelGeometry.BREAST_TRANSFORM;

/** Shared MCA humanoid armour model for villagers and genetics-enabled players. */
public class MCAArmorModel<T extends LivingEntity> extends HumanoidModel<T> {
    private final ModelPart breastTransform;
    private final ModelPart breasts;
    private final List<ModelPart> breastParts;

    public MCAArmorModel(ModelPart root) {
        super(root);
        breastTransform = body.getChild(BREAST_TRANSFORM);
        breasts = breastTransform.getChild(BREASTS);
        breastParts = List.of(breasts);
    }

    public void applyMorphology(VillagerLike<?> villager) {
        MCAModelMorphology.applyBreastDimensions(villager, breastTransform, breasts, breastParts);
    }

    @Override
    public void renderToBuffer(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        boolean wasBreastTransformVisible = breastTransform.visible;
        boolean wasYoung = young;
        breastTransform.visible &= leftArm.visible || rightArm.visible;
        young = false;
        try {
            super.renderToBuffer(matrices, vertices, light, overlay, color);
        } finally {
            breastTransform.visible = wasBreastTransformVisible;
            young = wasYoung;
        }
    }

}
