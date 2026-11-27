package net.conczin.mca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.model.MCAModelLayers;
import net.conczin.mca.client.model.VillagerOverlayModel;
import net.conczin.mca.client.model.VillagerPlayerModel;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.MorphologyLayer;
import net.conczin.mca.client.render.layer.VillagerFishingLineLayer;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;

public class VillagerEntityMCARenderer extends VillagerLikeEntityMCARenderer<VillagerEntityMCA> {
    public VillagerEntityMCARenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new VillagerPlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false),
                new VillagerPlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true));

        layers.add(0, new MorphologyLayer<>(this, ctx.bakeLayer(MCAModelLayers.PLAYER_ATTACHMENTS)));
        addLayer(new FaceLayer<>(this, createOverlay(ctx, MCAModelLayers.VILLAGER_FACE).hideWears()));
        addLayer(new ClothingLayer<>(this, createOverlay(ctx, MCAModelLayers.VILLAGER_CLOTHING), "normal"));
        addLayer(new ClothingLayer<>(this, new VillagerOverlayModel<>(
                ctx.bakeLayer(MCAModelLayers.VILLAGER_CLOTHING_SLIM), true), "normal", true));
        addLayer(new HairLayer<>(this, createOverlay(ctx, MCAModelLayers.VILLAGER_HAIR)));
        addLayer(new VillagerFishingLineLayer(this));
    }

    @Override
    protected void setupRotations(VillagerEntityMCA villager, PoseStack poseStack, float bob, float bodyRot,
                                  float partialTick, float scale) {
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
