package net.conczin.mca.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.MCAClient;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.VillagerDimensions;
import net.conczin.mca.registry.EntitiesMCA;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.UUID;

public interface CommonVillagerModel<T extends LivingEntity> {
    static VillagerLike<?> getVillager(Level world, UUID uuid) {
        if (MCAClient.fallbackVillager == null) {
            MCAClient.fallbackVillager = EntitiesMCA.MALE_VILLAGER.create(world, EntitySpawnReason.COMMAND);
            if (MCAClient.fallbackVillager != null) {
                MCAClient.fallbackVillager.getGenetics().setGender(Gender.MALE);
                MCAClient.fallbackVillager.setHair("mca:skins/hair/male/0.png");
                MCAClient.fallbackVillager.setClothes("mca:skins/clothing/normal/male/none/0.png");
            }
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
                matrices.scale(breastSize * 0.2f + 1.05f, breastSize * 0.75f + 0.75f, breastSize * 0.75f + 0.75f);
                for (ModelPart part : getBreastParts()) {
                    part.render(matrices, vertices, light, overlay, color);
                }
                matrices.popPose();
            }
        }
    }

    default void applyVillagerDimensions(VillagerLike<?> villager, boolean isSneaking) {
        getDimensions().set(villager.getVillagerDimensions());
        setBreastSize(villager.getGenetics().getBreastSize());
        getBreastPart().visible = villager.getGenetics().getGender() == Gender.FEMALE;

        float headSize = getDimensions().getHead();
        getCommonHeadParts().forEach(part -> {
            part.xScale = headSize;
            part.yScale = headSize;
            part.zScale = headSize;
        });

        float scaledBreastSize = getBreastSize() * getDimensions().getBreasts();
        boolean renderBreasts = getBreastPart().visible && getBodyPart().visible && scaledBreastSize > 0.0F;
        float breastScaleX = scaledBreastSize * 0.2F + 1.05F;
        float breastScaleY = scaledBreastSize * 0.75F + 0.75F;
        float breastScaleZ = scaledBreastSize * 0.75F + 0.75F;

        for (ModelPart part : getBreastParts()) {
            part.visible = renderBreasts;
            part.xScale = renderBreasts ? breastScaleX : 1.0F;
            part.yScale = renderBreasts ? breastScaleY : 1.0F;
            part.zScale = renderBreasts ? breastScaleZ : 1.0F;
            part.xRot = (float) Math.PI * 0.3f + getBodyPart().xRot;

            float cy = 0.0f;
            float cz = 0.0f;
            if (isSneaking) {
                cy = 3.0f;
                cz = 1.5f;
            }

            part.setPos(0.25f, (float) (5.0f - Math.pow(getBreastSize(), 0.5) * 2.5f + cy), -1.5f + getBreastSize() * 0.25f + cz);
        }
    }

    default void copyCommonAttributes(CommonVillagerModel<T> target) {
        target.getDimensions().set(getDimensions());
        target.setBreastSize(getBreastSize());
    }
}
