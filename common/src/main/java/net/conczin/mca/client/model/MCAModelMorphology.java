package net.conczin.mca.client.model;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.client.model.geom.ModelPart;

public final class MCAModelMorphology {
    static final float BREAST_ROTATION_X = (float) Math.PI * 0.3F;

    private MCAModelMorphology() {
    }

    public static void applyBreastDimensions(
            VillagerLike<?> villager,
            ModelPart transform
    ) {
        var dimensions = villager.getVillagerDimensions();
        float rawBreastSize = villager.getGenetics().getBreastSize();
        applyBreastDimensions(
                transform,
                rawBreastSize,
                dimensions.getBreasts(),
                Config.getInstance().enableBoobs && villager.getGenetics().getGender() == Gender.FEMALE
        );
    }

    static void applyBreastDimensions(ModelPart transform, float rawBreastSize, float dimensionScale, boolean visible) {
        float scaledBreastSize = rawBreastSize * dimensionScale;
        float scaleX = scaledBreastSize * 0.2F + 1.05F;
        float scaleYZ = scaledBreastSize * 0.75F + 0.75F;
        float breastY = (float) (5.0F - Math.pow(rawBreastSize, 0.5) * 2.5F);
        float breastZ = -1.5F + rawBreastSize * 0.25F;

        transform.visible = visible && scaledBreastSize > 0.0F;
        transform.setRotation(BREAST_ROTATION_X, 0.0F, 0.0F);
        // Previously the transform supplied scale while each child supplied its
        // pivot. Pre-scaling the pivot preserves that S*T*R result when the
        // complete pose moves onto this single T*R*S node. Y/Z share one scale,
        // so the X rotation commutes with that scale.
        transform.setPos(0.25F * scaleX, breastY * scaleYZ, breastZ * scaleYZ);
        setScale(transform, scaleX, scaleYZ, scaleYZ);
    }

    public static void applyHeadScale(ModelPart head, ModelPart hat, float scale) {
        head.xScale *= scale;
        head.yScale *= scale;
        head.zScale *= scale;
        hat.xScale *= scale;
        hat.yScale *= scale;
        hat.zScale *= scale;
    }

    private static void setScale(ModelPart part, float x, float y, float z) {
        part.xScale = x;
        part.yScale = y;
        part.zScale = z;
    }
}
