package net.conczin.mca.client.model;

import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.client.model.geom.ModelPart;

public final class MCAModelMorphology {
    private MCAModelMorphology() {
    }

    public static void applyBreastDimensions(
            VillagerLike<?> villager,
            ModelPart transform,
            ModelPart breastPart,
            Iterable<ModelPart> breastParts
    ) {
        var dimensions = villager.getVillagerDimensions();
        float rawBreastSize = villager.getGenetics().getBreastSize();
        float scaledBreastSize = rawBreastSize * dimensions.getBreasts();
        transform.visible = villager.getGenetics().getGender() == Gender.FEMALE && scaledBreastSize > 0.0F;
        setScale(
                transform,
                scaledBreastSize * 0.2F + 1.05F,
                scaledBreastSize * 0.75F + 0.75F,
                scaledBreastSize * 0.75F + 0.75F
        );

        breastPart.visible = villager.getGenetics().getGender() == Gender.FEMALE;
        float breastY = (float) (5.0F - Math.pow(rawBreastSize, 0.5) * 2.5F);
        float breastZ = -1.5F + rawBreastSize * 0.25F;
        for (ModelPart part : breastParts) {
            part.setRotation((float) Math.PI * 0.3F, 0.0F, 0.0F);
            part.setPos(0.25F, breastY, breastZ);
        }
    }

    private static void setScale(ModelPart part, float x, float y, float z) {
        part.xScale = x;
        part.yScale = y;
        part.zScale = z;
    }
}
