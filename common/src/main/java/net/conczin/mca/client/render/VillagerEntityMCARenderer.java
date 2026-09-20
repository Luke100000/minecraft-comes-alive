package net.conczin.mca.client.render;

import net.conczin.mca.client.model.MCAModelLayers;
import net.conczin.mca.client.model.VillagerOverlayModel;
import net.conczin.mca.client.model.VillagerPlayerModel;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.VillagerMorphologyLayer;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class VillagerEntityMCARenderer extends VillagerLikeEntityMCARenderer<VillagerEntityMCA> {
    public VillagerEntityMCARenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new VillagerPlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER)));

        layers.add(0, new VillagerMorphologyLayer<>(this, ctx.bakeLayer(MCAModelLayers.PLAYER_ATTACHMENTS)));
        addLayer(new FaceLayer<>(this, createOverlay(ctx, MCAModelLayers.VILLAGER_FACE).hideWears(), "normal"));
        addLayer(new ClothingLayer<>(this, createOverlay(ctx, MCAModelLayers.VILLAGER_CLOTHING), "normal"));
        addLayer(new HairLayer<>(this, createOverlay(ctx, MCAModelLayers.VILLAGER_HAIR)));
    }

    private static VillagerOverlayModel<VillagerEntityMCA> createOverlay(
            EntityRendererProvider.Context ctx,
            ModelLayerLocation layer
    ) {
        return new VillagerOverlayModel<>(ctx.bakeLayer(layer), false);
    }
}
