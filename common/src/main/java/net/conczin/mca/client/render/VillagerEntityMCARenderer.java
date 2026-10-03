package net.conczin.mca.client.render;

import net.conczin.mca.client.model.HairOverlayModel;
import net.conczin.mca.client.model.MCALayerDefinitions;
import net.conczin.mca.client.model.MCAModelLayers;
import net.conczin.mca.client.model.VillagerOverlayModel;
import net.conczin.mca.client.model.VillagerPlayerModel;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.VillagerFishingLineLayer;
import net.conczin.mca.client.render.layer.MorphologyLayer;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class VillagerEntityMCARenderer extends VillagerLikeEntityMCARenderer<VillagerEntityMCA> {
    public VillagerEntityMCARenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new VillagerPlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false),
                new VillagerPlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true));

        layers.add(0, new MorphologyLayer<>(this, ctx.bakeLayer(MCAModelLayers.PLAYER_ATTACHMENTS)));
        addLayer(new FaceLayer<>(this, createOverlay(ctx, MCAModelLayers.VILLAGER_FACE).hideWears(), "normal"));
        addLayer(new ClothingLayer<>(this, createOverlay(ctx, MCAModelLayers.VILLAGER_CLOTHING), "normal"));
        addLayer(new ClothingLayer<>(this, new VillagerOverlayModel<>(
                ctx.bakeLayer(MCAModelLayers.VILLAGER_CLOTHING_SLIM), true), "normal", true));
        addLayer(new HairLayer<>(this, new HairOverlayModel<>(
                ctx.bakeLayer(MCAModelLayers.VILLAGER_HAIR),
                MCALayerDefinitions.VILLAGER_CLOTHING_DILATION
        )));
        addLayer(new VillagerFishingLineLayer(this));
    }
}
