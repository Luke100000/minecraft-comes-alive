package net.conczin.mca.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.client.render.VillagerVisualSnapshot;
import net.conczin.mca.entity.ai.relationship.VillagerDimensions;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;

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
                for (ModelPart part : getBreastParts()) {
                    part.render(matrices, vertices, light, overlay, color);
                }
            }
        }
    }

    default void applyVillagerDimensions(VillagerVisualSnapshot snapshot, boolean isSneaking) {
        getDimensions().set(snapshot.dimensions());
        setBreastSize(snapshot.breastSize());
        
        boolean female = snapshot.female();
        float geneticBreastSize = getBreastSize();
        float breastDimFactor = getDimensions().getBreasts();
        float breastScaleSize = geneticBreastSize * breastDimFactor;
        boolean hasBreasts = female && breastScaleSize > 0;
        
        getBreastPart().visible = hasBreasts;
        if (this instanceof PlayerEntityExtendedModel<?> playerModel) {
            playerModel.breastsWear.visible = playerModel.jacket.visible && hasBreasts;
        } else if (this instanceof VillagerEntityModelMCA villagerModel) {
            villagerModel.breastsWear.visible = villagerModel.bodyWear.visible && hasBreasts;
        }

        float scaleX = 1.0f;
        float scaleY = 1.0f;
        float scaleZ = 1.0f;
        if (hasBreasts) {
            scaleX = breastScaleSize * 0.2f + 1.05f;
            scaleY = breastScaleSize * 0.75f + 0.75f;
            scaleZ = breastScaleSize * 0.75f + 0.75f;
        }

        for (ModelPart part : getBreastParts()) {
            part.xScale = scaleX;
            part.yScale = scaleY;
            part.zScale = scaleZ;

            part.xRot = (float) Math.PI * 0.3f + getBodyPart().xRot;

            float cy = 0.0f;
            float cz = 0.0f;
            if (isSneaking) {
                cy = 3.0f;
                cz = 1.5f;
            }

            float unscaledX = 0.25f;
            float unscaledY = (float) (5.0f - Math.pow(geneticBreastSize, 0.5) * 2.5f + cy);
            float unscaledZ = -1.5f + geneticBreastSize * 0.25f + cz;

            part.setPos(unscaledX, unscaledY, unscaledZ);
        }
    }

    default void copyCommonAttributes(CommonVillagerModel<?> target) {
        target.getDimensions().set(getDimensions());
        target.setBreastSize(getBreastSize());
    }

    default void submitCommon(PoseStack matrices, SubmitNodeCollector submitNodeCollector, RenderType renderType, int light, int overlay, int color, HumanoidRenderState state) {
        submitNodeCollector.submitCustomGeometry(matrices, renderType, (pose, buffer) -> {
            setupCommonAnimation(state);
            PoseStack renderStack = new PoseStack();
            renderStack.last().set(pose);
            renderCommon(renderStack, buffer, light, overlay, color);
        });
    }

    static void copyPartState(ModelPart target, ModelPart source) {
        target.loadPose(source.storePose());
    }

    @SuppressWarnings("unchecked")
    private void setupCommonAnimation(HumanoidRenderState state) {
        if (this instanceof HumanoidModel<?> humanoidModel) {
            ((HumanoidModel<HumanoidRenderState>) humanoidModel).setupAnim(state);
        }
    }
}
