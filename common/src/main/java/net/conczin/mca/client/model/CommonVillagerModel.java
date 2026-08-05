package net.conczin.mca.client.model;

import net.conczin.mca.MCAClient;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.registry.EntitiesMCA;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** Shared morphology contract for MCA humanoid models. */
public interface CommonVillagerModel<T extends LivingEntity> {
    static VillagerLike<?> getVillager(Level world, UUID uuid) {
        if (MCAClient.fallbackVillager == null) {
            MCAClient.fallbackVillager = EntitiesMCA.MALE_VILLAGER.create(world);
        }
        return MCAClient.playerData.getOrDefault(uuid, MCAClient.fallbackVillager);
    }

    static VillagerLike<?> getVillager(Entity villager) {
        if (villager instanceof VillagerLike<?> v) {
            return v;
        }
        return getVillager(villager.level(), villager.getUUID());
    }


    static int multiplyColor(int first, int second) {
        int alpha = ((first >>> 24) * (second >>> 24) / 255) << 24;
        int red = (((first >>> 16) & 0xFF) * ((second >>> 16) & 0xFF) / 255) << 16;
        int green = (((first >>> 8) & 0xFF) * ((second >>> 8) & 0xFF) / 255) << 8;
        int blue = (first & 0xFF) * (second & 0xFF) / 255;
        return alpha | red | green | blue;
    }

    ModelPart getMorphologyHead();

    ModelPart getMorphologyHat();

    ModelPart getBreastTransform();

    ModelPart getBreastPart();

    Iterable<ModelPart> getBreastParts();

    /** Synchronizes MCA wear parts that mirror canonical humanoid bones. */
    default void syncWearParts() {
    }

    /** Copies parent visibility while preserving the concrete model's layer role. */
    default void copyVisibility(HumanoidModel<?> parent) {
    }

    /** Copies body-local morphology transforms without copying layer-specific wear visibility. */
    default void copyMorphologyTo(CommonVillagerModel<?> target) {
        target.getBreastTransform().copyFrom(getBreastTransform());
        target.getBreastTransform().visible = getBreastTransform().visible;
        target.getBreastPart().copyFrom(getBreastPart());
        target.getBreastPart().visible = getBreastPart().visible;
    }

    /** Applies current entity genetics directly to model parts; no mutable snapshot is retained. */
    default void applyVillagerDimensions(VillagerLike<?> villager) {
        var dimensions = villager.getVillagerDimensions();
        float headScale = dimensions.getHead();
        setScale(getMorphologyHead(), headScale, headScale, headScale);
        setScale(getMorphologyHat(), headScale, headScale, headScale);

        float rawBreastSize = villager.getGenetics().getBreastSize();
        float scaledBreastSize = rawBreastSize * dimensions.getBreasts();
        ModelPart transform = getBreastTransform();
        transform.visible = villager.getGenetics().getGender() == Gender.FEMALE && scaledBreastSize > 0.0F;
        setScale(
                transform,
                scaledBreastSize * 0.2F + 1.05F,
                scaledBreastSize * 0.75F + 0.75F,
                scaledBreastSize * 0.75F + 0.75F
        );

        getBreastPart().visible = villager.getGenetics().getGender() == Gender.FEMALE;
        float breastY = (float) (5.0F - Math.pow(rawBreastSize, 0.5) * 2.5F);
        float breastZ = -1.5F + rawBreastSize * 0.25F;
        for (ModelPart part : getBreastParts()) {
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
