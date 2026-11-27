package net.conczin.mca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.layer.*;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;

public class VillagerEntityMCARenderer extends VillagerLikeEntityMCARenderer<VillagerEntityMCA> {
    public VillagerEntityMCARenderer(EntityRendererProvider.Context ctx) {
        super(ctx, createAnimationModel(ctx).hideWears());

        // The parent drives external animation; visible layers keep MCA geometry and textures.
        layers.add(0, new SkinLayer<>(this, createVisibleModel(VillagerEntityModelMCA.bodyData(CubeDeformation.NONE)).hideWears()));
        addLayer(new FaceLayer<>(this, createVisibleModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.01F))).hideWears()));
        addLayer(new ClothingLayer<>(this, createVisibleModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.0625F))), "normal"));
        addLayer(new HairLayer<>(this, createVisibleModel(VillagerEntityModelMCA.hairData(new CubeDeformation(0.125F)))));
        addLayer(new VillagerFishingLineLayer(this));
    }

    private static VillagerEntityModelMCA<VillagerEntityMCA> createAnimationModel(EntityRendererProvider.Context ctx) {
        return new VillagerEntityModelMCA<>(ctx.bakeLayer(ModelLayers.PLAYER));
    }

    private static VillagerEntityModelMCA<VillagerEntityMCA> createVisibleModel(MeshDefinition data) {
        return new VillagerEntityModelMCA<>(LayerDefinition.create(data, 64, 64).bakeRoot());
    }

    @Override
    protected void setupRotations(VillagerEntityMCA villager, PoseStack poseStack, float bob, float bodyRot, float partialTick, float scale) {
        if (villager.hasPose(Pose.SLEEPING)) {
            Direction direction = villager.getBedOrientation();
            if (direction != null) {
                // Vanilla already offset by the physical eye height; add the uncapped visual remainder.
                float correction = VillagerLike.PLAYER_MODEL_EYE_HEIGHT
                        * (villager.getVisualVerticalScaleFactor() - villager.getPhysicalVerticalScaleFactor());
                poseStack.translate(
                        -direction.getStepX() * correction,
                        0.0,
                        -direction.getStepZ() * correction
                );
            }
        }

        super.setupRotations(villager, poseStack, bob, bodyRot, partialTick, scale);
    }
}
