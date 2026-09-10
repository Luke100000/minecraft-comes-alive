package net.conczin.mca.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

import static net.conczin.mca.client.model.VillagerEntityBaseModelMCA.BREASTS;
import static net.conczin.mca.client.model.VillagerEntityBaseModelMCA.BREAST_TRANSFORM;
import static net.conczin.mca.client.model.VillagerEntityModelMCA.BREASTPLATE;

/** MCA-only player geometry that follows, but never replaces, the renderer's player model. */
public final class PlayerMorphologyModel {
    private final ModelPart breastTransform;
    private final ModelPart breasts;
    private final ModelPart breastsWear;
    private final List<ModelPart> breastParts;

    public PlayerMorphologyModel(ModelPart root) {
        ModelPart body = root.getChild(PartNames.BODY);
        breastTransform = body.getChild(BREAST_TRANSFORM);
        breasts = breastTransform.getChild(BREASTS);
        breastsWear = breastTransform.getChild(BREASTPLATE);
        breastParts = List.of(breasts, breastsWear);
    }

    public void apply(VillagerLike<?> villager, boolean showWear) {
        CommonVillagerModel.applyBreastDimensions(
                villager,
                breastTransform,
                breasts,
                breastParts
        );
        breastsWear.visible = showWear && breastTransform.visible;
    }

    public <T extends LivingEntity> void render(
            PlayerModel<T> parent,
            PoseStack matrices,
            VertexConsumer vertices,
            int light,
            int overlay,
            int color
    ) {
        if (!parent.body.visible || !breastTransform.visible) {
            return;
        }

        matrices.pushPose();
        try {
            parent.body.translateAndRotate(matrices);
            breastTransform.render(matrices, vertices, light, overlay, color);
        } finally {
            matrices.popPose();
        }
    }
}
