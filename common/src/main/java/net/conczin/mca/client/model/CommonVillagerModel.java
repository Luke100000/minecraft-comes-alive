package net.conczin.mca.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.client.render.VillagerVisualSnapshot;
import net.conczin.mca.entity.ai.relationship.VillagerDimensions;
import net.minecraft.client.model.geom.ModelPart;

public interface CommonVillagerModel<T> {
    ModelPart getBreastPart();

    ModelPart getBodyPart();

    Iterable<ModelPart> getCommonHeadParts();

    Iterable<ModelPart> getCommonBodyParts();

    Iterable<ModelPart> getBreastParts();

    VillagerDimensions.Mutable getDimensions();

    float getBreastSize();

    void setBreastSize(float getBreastSize);

    default void renderCommon(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        //head
        float headSize = getDimensions().getHead();

        matrices.pushPose();
        matrices.scale(headSize, headSize, headSize);
        getCommonHeadParts().forEach(a -> a.render(matrices, vertices, light, overlay, color));
        matrices.popPose();

        //body
        getCommonBodyParts().forEach(a -> a.render(matrices, vertices, light, overlay, color));

        if (getBreastPart().visible && getBodyPart().visible) {
            float breastSize = getBreastSize() * getDimensions().getBreasts();

            if (breastSize > 0) {
                matrices.pushPose();
                matrices.scale(
                        breastSize * 0.2f + 1.05f,
                        breastSize * 0.75f + 0.75f,
                        breastSize * 0.75f + 0.75f
                );
                for (ModelPart part : getBreastParts()) {
                    part.render(matrices, vertices, light, overlay, color);
                }
                matrices.popPose();
            }
        }
    }

    default void applyVillagerDimensions(VillagerVisualSnapshot snapshot, boolean isSneaking) {
        getDimensions().set(snapshot.dimensions());
        setBreastSize(snapshot.breastSize());
        
        boolean female = snapshot.female();
        float breastSize = getBreastSize();
        boolean hasBreasts = female && breastSize * getDimensions().getBreasts() > 0;
        
        getBreastPart().visible = hasBreasts;
        if (this instanceof PlayerEntityExtendedModel<?> playerModel) {
            playerModel.breastsWear.visible = playerModel.jacket.visible && hasBreasts;
        } else if (this instanceof VillagerEntityModelMCA villagerModel) {
            villagerModel.breastsWear.visible = villagerModel.bodyWear.visible && hasBreasts;
        }

        for (ModelPart part : getBreastParts()) {
            part.xScale = 1.0f;
            part.yScale = 1.0f;
            part.zScale = 1.0f;

            part.xRot = (float) Math.PI * 0.3f + getBodyPart().xRot;

            float cy = 0.0f;
            float cz = 0.0f;
            if (isSneaking) {
                cy = 3.0f;
                cz = 1.5f;
            }

            part.setPos(0.25f, (float) (5.0f - Math.pow(breastSize, 0.5) * 2.5f + cy), -1.5f + breastSize * 0.25f + cz);
        }
    }

    default void copyCommonAttributes(CommonVillagerModel<?> target) {
        target.getDimensions().set(getDimensions());
        target.setBreastSize(getBreastSize());
    }

    static void copyPartState(ModelPart target, ModelPart source) {
        target.loadPose(source.storePose());
    }
}
