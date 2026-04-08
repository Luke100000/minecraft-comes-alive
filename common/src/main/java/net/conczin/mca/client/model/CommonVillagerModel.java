package net.conczin.mca.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.conczin.mca.client.render.VillagerVisualSnapshot;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.VillagerDimensions;
import net.conczin.mca.registry.EntitiesMCA;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public interface CommonVillagerModel<T> {
    enum RenderMask {
        FULL,
        HEAD_ONLY,
        NO_LEGS
    }

    static VillagerLike<?> getVillager(Level world, UUID uuid) {
        if (MCAClient.fallbackVillager == null) {
            MCAClient.fallbackVillager = EntitiesMCA.MALE_VILLAGER.create(world, EntitySpawnReason.LOAD);
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

    static @Nullable VillagerLike<?> peekVillager(VillagerStateHolder state) {
        return state.mca$getVillager();
    }

    static @Nullable VillagerVisualSnapshot peekVisuals(VillagerStateHolder state) {
        return state.mca$getVisualSnapshot();
    }

    static VillagerVisualSnapshot getVisuals(VillagerStateHolder state) {
        VillagerVisualSnapshot snapshot = peekVisuals(state);
        if (snapshot != null) {
            return snapshot;
        }

        VillagerLike<?> villager = peekVillager(state);
        if (villager != null) {
            snapshot = VillagerVisualSnapshot.capture(villager);
            state.mca$setVisualSnapshot(snapshot);
            return snapshot;
        }

        VillagerLike<?> fallbackVillager = MCAClient.fallbackVillager;
        if (fallbackVillager == null) {
            throw new IllegalStateException("No villager visuals available for render state");
        }

        return VillagerVisualSnapshot.capture(fallbackVillager);
    }

    static VillagerLike<?> getVillager(VillagerStateHolder state) {
        VillagerLike<?> villager = peekVillager(state);
        if (villager != null) {
            return villager;
        }

        VillagerLike<?> fallbackVillager = MCAClient.fallbackVillager;
        if (fallbackVillager == null) {
            throw new IllegalStateException("No villager available for render state");
        }

        return fallbackVillager;
    }

    ModelPart getBreastPart();

    ModelPart getBodyPart();

    Iterable<ModelPart> getCommonHeadParts();

    Iterable<ModelPart> getCommonBodyParts();

    Iterable<ModelPart> getBreastParts();

    VillagerDimensions.Mutable getDimensions();

    float getBreastSize();

    void setBreastSize(float getBreastSize);

    RenderMask getRenderMask();

    void setRenderMask(RenderMask renderMask);

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
        getBreastPart().visible = villager.getGenetics().getGender() == net.conczin.mca.entity.ai.relationship.Gender.FEMALE;

        for (ModelPart part : getBreastParts()) {
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

    default void applyVillagerDimensions(VillagerVisualSnapshot snapshot, boolean isSneaking) {
        getDimensions().set(snapshot.dimensions());
        setBreastSize(snapshot.breastSize());
        getBreastPart().visible = snapshot.female();

        for (ModelPart part : getBreastParts()) {
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

    default void copyCommonAttributes(CommonVillagerModel<?> target) {
        target.getDimensions().set(getDimensions());
        target.setBreastSize(getBreastSize());
    }

    static void applyRenderMask(HumanoidModel<?> model, RenderMask renderMask) {
        switch (renderMask) {
            case FULL -> {
                return;
            }
            case HEAD_ONLY -> {
                model.head.visible = true;
                model.hat.visible = false;
                model.body.visible = false;
                model.rightArm.visible = false;
                model.leftArm.visible = false;
                model.rightLeg.visible = false;
                model.leftLeg.visible = false;

                if (model instanceof PlayerEntityExtendedModel<?> playerModel) {
                    playerModel.jacket.visible = false;
                    playerModel.leftPants.visible = false;
                    playerModel.rightPants.visible = false;
                    playerModel.leftSleeve.visible = false;
                    playerModel.rightSleeve.visible = false;
                    playerModel.breasts.visible = false;
                    playerModel.breastsWear.visible = false;
                } else if (model instanceof PlayerArmorExtendedModel<?> armorModel) {
                    armorModel.breasts.visible = false;
                } else if (model instanceof VillagerEntityModelMCA villagerModel) {
                    villagerModel.bodyWear.visible = false;
                    villagerModel.leftArmwear.visible = false;
                    villagerModel.rightArmwear.visible = false;
                    villagerModel.leftLegwear.visible = false;
                    villagerModel.rightLegwear.visible = false;
                    villagerModel.breasts.visible = false;
                    villagerModel.breastsWear.visible = false;
                } else if (model instanceof VillagerEntityBaseModelMCA villagerModel) {
                    villagerModel.breasts.visible = false;
                }
            }
            case NO_LEGS -> {
                model.rightLeg.visible = false;
                model.leftLeg.visible = false;

                if (model instanceof PlayerEntityExtendedModel<?> playerModel) {
                    playerModel.leftPants.visible = false;
                    playerModel.rightPants.visible = false;
                    playerModel.jacket.visible = true;
                    playerModel.leftSleeve.visible = true;
                    playerModel.rightSleeve.visible = true;
                } else if (model instanceof VillagerEntityModelMCA villagerModel) {
                    villagerModel.leftLegwear.visible = false;
                    villagerModel.rightLegwear.visible = false;
                    villagerModel.bodyWear.visible = true;
                    villagerModel.leftArmwear.visible = true;
                    villagerModel.rightArmwear.visible = true;
                    villagerModel.breastsWear.visible = true;
                }
            }
        }
    }

    default void submitCommon(PoseStack matrices, SubmitNodeCollector submitNodeCollector, RenderType renderType, int light, int overlay, int color) {
        float headSize = getDimensions().getHead();

        matrices.pushPose();
        matrices.scale(headSize, headSize, headSize);
        getCommonHeadParts().forEach(part -> submitNodeCollector.submitModelPart(part, matrices, renderType, light, overlay, null, color, null));
        matrices.popPose();

        getCommonBodyParts().forEach(part -> submitNodeCollector.submitModelPart(part, matrices, renderType, light, overlay, null, color, null));

        if (getBreastPart().visible && getBodyPart().visible) {
            float breastSize = getBreastSize() * getDimensions().getBreasts();

            if (breastSize > 0) {
                matrices.pushPose();
                matrices.scale(breastSize * 0.2f + 1.05f, breastSize * 0.75f + 0.75f, breastSize * 0.75f + 0.75f);
                for (ModelPart part : getBreastParts()) {
                    submitNodeCollector.submitModelPart(part, matrices, renderType, light, overlay, null, color, null);
                }
                matrices.popPose();
            }
        }
    }

    static void copyPartState(ModelPart target, ModelPart source) {
        target.loadPose(source.storePose());
    }
}
