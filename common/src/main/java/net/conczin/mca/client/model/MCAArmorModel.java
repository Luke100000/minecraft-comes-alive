package net.conczin.mca.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

import static net.conczin.mca.client.model.MCAModelGeometry.BREAST_TRANSFORM;

/** Shared MCA humanoid armour model for villagers and genetics-enabled players. */
public class MCAArmorModel<T extends LivingEntity> extends HumanoidModel<T> {
    private final ModelPart breastTransform;
    private boolean renderBreastMorphology;

    public MCAArmorModel(ModelPart root) {
        super(root);
        breastTransform = body.getChild(BREAST_TRANSFORM);
    }

    public void applyMorphology(VillagerLike<?> villager) {
        MCAModelMorphology.applyBreastDimensions(villager, breastTransform);
        renderBreastMorphology = true;
    }

    public void hideMorphology() {
        renderBreastMorphology = false;
    }

    @Override
    public void renderToBuffer(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        boolean wasBreastTransformVisible = breastTransform.visible;
        breastTransform.visible &= renderBreastMorphology;
        try {
            super.renderToBuffer(matrices, vertices, light, overlay, color);
        } finally {
            breastTransform.visible = wasBreastTransformVisible;
        }
    }

}
