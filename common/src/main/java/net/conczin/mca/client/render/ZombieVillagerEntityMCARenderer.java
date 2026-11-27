package net.conczin.mca.client.render;

import net.conczin.mca.client.model.MCAModelLayers;
import net.conczin.mca.client.model.VillagerOverlayModel;
import net.conczin.mca.client.model.ZombieVillagerPlayerModel;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.MorphologyLayer;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class ZombieVillagerEntityMCARenderer extends VillagerLikeEntityMCARenderer<ZombieVillagerEntityMCA> {
    public ZombieVillagerEntityMCARenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ZombieVillagerPlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false),
                new ZombieVillagerPlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true));

        layers.add(0, new MorphologyLayer<>(this, ctx.bakeLayer(MCAModelLayers.PLAYER_ATTACHMENTS)));
        addLayer(new FaceLayer<>(this, createOverlay(ctx, MCAModelLayers.ZOMBIE_VILLAGER_FACE).hideWears()));
        addLayer(new ClothingLayer<>(this, createOverlay(ctx, MCAModelLayers.ZOMBIE_VILLAGER_CLOTHING), "zombie"));
        addLayer(new ClothingLayer<>(this, new VillagerOverlayModel<>(
                ctx.bakeLayer(MCAModelLayers.ZOMBIE_VILLAGER_CLOTHING_SLIM), true), "zombie", true));
        addLayer(new HairLayer<>(this, createOverlay(ctx, MCAModelLayers.ZOMBIE_VILLAGER_HAIR)));
    }

    @Override
    protected boolean isShaking(ZombieVillagerEntityMCA entity) {
        return entity.isConverting() || entity.isUnderWaterConverting();
    }
}
