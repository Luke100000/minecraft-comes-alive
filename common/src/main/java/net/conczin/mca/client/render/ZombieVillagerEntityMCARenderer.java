package net.conczin.mca.client.render;

import net.conczin.mca.client.model.MCAModelLayers;
import net.conczin.mca.client.model.VillagerOverlayModel;
import net.conczin.mca.client.model.ZombieVillagerPlayerModel;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.VillagerMorphologyLayer;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class ZombieVillagerEntityMCARenderer extends VillagerLikeEntityMCARenderer<ZombieVillagerEntityMCA> {
    public ZombieVillagerEntityMCARenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ZombieVillagerPlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER)));

        layers.add(0, new VillagerMorphologyLayer<>(this, ctx.bakeLayer(MCAModelLayers.PLAYER_ATTACHMENTS)));
        addLayer(new FaceLayer<>(this, createOverlay(ctx, MCAModelLayers.ZOMBIE_VILLAGER_FACE).hideWears(), "normal"));
        addLayer(new ClothingLayer<>(this, createOverlay(ctx, MCAModelLayers.ZOMBIE_VILLAGER_CLOTHING), "zombie"));
        addLayer(new HairLayer<>(this, createOverlay(ctx, MCAModelLayers.ZOMBIE_VILLAGER_HAIR)));
    }

    private static VillagerOverlayModel<ZombieVillagerEntityMCA> createOverlay(
            EntityRendererProvider.Context ctx,
            ModelLayerLocation layer
    ) {
        return new VillagerOverlayModel<>(ctx.bakeLayer(layer), false);
    }

    @Override
    protected boolean isShaking(ZombieVillagerEntityMCA entity) {
        return entity.isConverting() || entity.isUnderWaterConverting();
    }
}
