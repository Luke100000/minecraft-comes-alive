package net.conczin.mca.client.model;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

import static net.conczin.mca.client.model.VillagerEntityBaseModelMCA.BREASTS;
import static net.conczin.mca.client.model.VillagerEntityBaseModelMCA.BREAST_TRANSFORM;

/** Shared MCA humanoid armour model for villagers and genetics-enabled players. */
public class MCAArmorModel<T extends LivingEntity> extends HumanoidModel<T> implements CommonVillagerModel<T> {
    public final ModelPart breastTransform;
    public final ModelPart breasts;

    public MCAArmorModel(ModelPart root) {
        super(root);
        breastTransform = body.getChild(BREAST_TRANSFORM);
        breasts = breastTransform.getChild(BREASTS);
    }

    @Override
    public ModelPart getMorphologyHead() {
        return head;
    }

    @Override
    public ModelPart getMorphologyHat() {
        return hat;
    }

    @Override
    public ModelPart getBreastTransform() {
        return breastTransform;
    }

    @Override
    public ModelPart getBreastPart() {
        return breasts;
    }

    @Override
    public Iterable<ModelPart> getBreastParts() {
        return ImmutableList.of(breasts);
    }

    @Override
    public void renderToBuffer(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        boolean wasYoung = young;
        young = false;
        super.renderToBuffer(matrices, vertices, light, overlay, color);
        young = wasYoung;
    }

    @Override
    public void setupAnim(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        super.setupAnim(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        applyVillagerDimensions(CommonVillagerModel.getVillager(entity));
    }
}
