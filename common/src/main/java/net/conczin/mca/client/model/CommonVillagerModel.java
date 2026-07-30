package net.conczin.mca.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.client.render.VillagerVisuals;
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

    void setBreastSize(float breastSize);

    /** Synchronizes MCA wear parts that mirror the canonical humanoid bones. */
    default void syncWearParts() {
    }

    default void renderCommon(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        //head
        float headSize = getDimensions().getHead();

        matrices.pushPose();
        matrices.scale(headSize, headSize, headSize);
        getCommonHeadParts().forEach(a -> a.render(matrices, vertices, light, overlay, color));
        matrices.popPose();

        // Keep root-level wear parts aligned with the final canonical pose.
        syncWearParts();

        //body
        getCommonBodyParts().forEach(a -> a.render(matrices, vertices, light, overlay, color));

        if (getBreastPart().visible && getBodyPart().visible) {
            float breastSize = getBreastSize() * getDimensions().getBreasts();

            if (breastSize > 0) {
                matrices.pushPose();
                // The breast parts are stored at the model root, but are logically attached to
                // the torso. Apply the current body transform before the local breast scaling.
                getBodyPart().translateAndRotate(matrices);
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

    default void applyVillagerDimensions(VillagerVisuals visuals) {
        getDimensions().set(visuals.dimensions());
        setBreastSize(visuals.breastSize());
        
        boolean female = visuals.female();
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

            part.setRotation((float) Math.PI * 0.3f, 0.0f, 0.0f);
            part.setPos(0.25f, (float) (5.0f - Math.pow(breastSize, 0.5) * 2.5f), -1.5f + breastSize * 0.25f);
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
