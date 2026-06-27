package net.conczin.mca.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.MCA;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.entity.ai.relationship.VillagerDimensions;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

import java.util.concurrent.atomic.AtomicInteger;

public interface CommonVillagerModel<T> {
    AtomicInteger MCA_BREAST_DEBUG_LOGS = new AtomicInteger();

    ModelPart getBreastPart();

    ModelPart getBodyPart();

    Iterable<ModelPart> getCommonHeadParts();

    Iterable<ModelPart> getCommonBodyParts();

    Iterable<ModelPart> getBreastParts();

    VillagerDimensions.Mutable getDimensions();

    float getBreastSize();

    void setBreastSize(float getBreastSize);

    boolean usesCommonRendering();

    default void renderCommon(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        float headSize = getDimensions().getHead();

        matrices.pushPose();
        matrices.scale(headSize, headSize, headSize);
        getCommonHeadParts().forEach(a -> a.render(matrices, vertices, light, overlay, color));
        matrices.popPose();

        getCommonBodyParts().forEach(a -> a.render(matrices, vertices, light, overlay, color));

        if (getBreastPart().visible && getBodyPart().visible) {
            float breastSize = getBreastSize() * getDimensions().getBreasts();

            if (breastSize > 0) {
                matrices.pushPose();
                // Keep breasts as MCA root parts so EMF replacement models do not hide them,
                // but render them in body space so Fresh Moves body transforms carry them.
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

                logBreastDebug("render", getBodyPart(), getBreastPart(), breastSize);
            }
        }
    }

    default void applyVillagerDimensions(VillagerVisuals visuals, boolean isSneaking) {
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

            part.xRot = (float) Math.PI * 0.3f;
            part.yRot = 0.0f;
            part.zRot = 0.0f;

            float cy = 0.0f;
            float cz = 0.0f;
            if (isSneaking) {
                cy = 3.0f;
                cz = 1.5f;
            }

            part.setPos(0.25f, (float) (5.0f - Math.pow(breastSize, 0.5) * 2.5f + cy), -1.5f + breastSize * 0.25f + cz);
        }

        logBreastDebug("setup", getBodyPart(), getBreastPart(), breastSize);
    }

    default void copyCommonAttributes(CommonVillagerModel<?> target) {
        target.getDimensions().set(getDimensions());
        target.setBreastSize(getBreastSize());
    }

    static void copyPartState(ModelPart target, ModelPart source) {
        target.loadPose(source.storePose());
    }

    static void setBaseVisible(HumanoidModel<?> model, boolean visible) {
        model.head.visible = visible;
        model.hat.visible = visible;
        model.body.visible = visible;
        model.leftArm.visible = visible;
        model.rightArm.visible = visible;
        model.leftLeg.visible = visible;
        model.rightLeg.visible = visible;
    }

    static void logBreastDebug(String phase, ModelPart body, ModelPart breasts, float breastSize) {
        if (!MCA.platformHelper.isDevelopmentEnvironment()) {
            return;
        }
        int count = MCA_BREAST_DEBUG_LOGS.getAndIncrement();
        if (count >= 40) {
            return;
        }
        MCA.LOGGER.info(
                "MCA breast debug [{} #{}]: body pos=({}, {}, {}) rot=({}, {}, {}), breast local pos=({}, {}, {}) rot=({}, {}, {}), size={}",
                phase,
                count,
                body.x, body.y, body.z,
                body.xRot, body.yRot, body.zRot,
                breasts.x, breasts.y, breasts.z,
                breasts.xRot, breasts.yRot, breasts.zRot,
                breastSize
        );
    }
}
