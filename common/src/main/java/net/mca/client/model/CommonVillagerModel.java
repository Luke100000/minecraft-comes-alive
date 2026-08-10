package net.mca.client.model;

import net.mca.MCAClient;
import net.mca.entity.EntitiesMCA;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.relationship.Gender;
import net.mca.entity.ai.relationship.VillagerDimensions;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.UUID;

public interface CommonVillagerModel<T extends LivingEntity> {
    ModelPart getBreastPart();

    ModelPart getBodyPart();

    Iterable<ModelPart> getCommonHeadParts();

    Iterable<ModelPart> getCommonBodyParts();

    Iterable<ModelPart> getBreastParts();

    VillagerDimensions.Mutable getDimensions();

    float getBreastSize();

    void setBreastSize(float getBreastSize);

    default void renderCommon(PoseStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
        //head
        float headSize = getDimensions().getHead();

        matrices.pushPose();
        matrices.scale(headSize, headSize, headSize);
        getCommonHeadParts().forEach(a -> a.render(matrices, vertices, light, overlay, red, green, blue, alpha));
        matrices.popPose();

        //body
        getCommonBodyParts().forEach(a -> a.render(matrices, vertices, light, overlay, red, green, blue, alpha));

        if (getBreastPart().visible && getBodyPart().visible) {
            float breastSize = getBreastSize() * getDimensions().getBreasts();

            if (breastSize > 0) {
                matrices.pushPose();
                matrices.scale(breastSize * 0.2F + 1.05F, breastSize * 0.75F + 0.75F, breastSize * 0.45F + 0.75F);
                for (ModelPart part : getBreastParts()) {
                    part.render(matrices, vertices, light, overlay, red, green, blue, alpha);
                }
                matrices.popPose();
            }
        }
    }

    default void applyVillagerDimensions(VillagerLike<?> villager, boolean isSneaking) {
        getDimensions().set(villager.getVillagerDimensions());
        setBreastSize(villager.getGenetics().getBreastSize());
        getBreastPart().visible = villager.getGenetics().getGender() == Gender.FEMALE;

        for (ModelPart part : getBreastParts()) {
            part.xRot = (float)Math.PI * 0.3f + getBodyPart().xRot;

            float cy = 0.0f;
            float cz = 0.0f;
            if (isSneaking) {
                cy = 3.0f;
                cz = 1.5f;
            }

            part.setPos(0.25f, (float)(5.0f - Math.pow(getBreastSize(), 0.5) * 2.5f + cy), -1.5f + getBreastSize() * 0.25f + cz);
        }
    }

    default void copyCommonAttributes(CommonVillagerModel<T> target) {
        target.getDimensions().set(getDimensions());
        target.setBreastSize(getBreastSize());
    }

    static VillagerLike<?> getVillager(Level world, UUID uuid) {
        if (MCAClient.fallbackVillager == null) {
            MCAClient.fallbackVillager = EntitiesMCA.MALE_VILLAGER.get().create(world);
        }
        return MCAClient.playerData.getOrDefault(uuid, MCAClient.fallbackVillager);
    }

    static VillagerLike<?> getVillager(Entity villager) {
        if (villager instanceof VillagerLike<?> v) {
            return v;
        } else {
            return getVillager(villager.level(), villager.getUUID());
        }
    }
}
