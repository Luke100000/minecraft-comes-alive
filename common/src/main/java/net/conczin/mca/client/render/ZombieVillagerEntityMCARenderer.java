package net.conczin.mca.client.render;

import net.conczin.mca.client.model.MCAModelLayers;
import net.conczin.mca.client.model.PlayerAnimationBridge;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.model.ZombieVillagerEntityModelMCA;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class ZombieVillagerEntityMCARenderer extends VillagerLikeEntityMCARenderer<ZombieVillagerEntityMCA> {
    public ZombieVillagerEntityMCARenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ZombieVillagerEntityModelMCA<ZombieVillagerEntityMCA>(
                ctx.bakeLayer(MCAModelLayers.ZOMBIE_VILLAGER),
                new PlayerAnimationBridge<ZombieVillagerEntityMCA>(new PlayerModel<ZombieVillagerEntityMCA>(ctx.bakeLayer(ModelLayers.PLAYER), false))
        ).hideWears());

        addLayer(new FaceLayer<>(this, createModel(ctx, MCAModelLayers.ZOMBIE_VILLAGER_FACE).hideWears(), "normal"));
        addLayer(new ClothingLayer<>(this, createModel(ctx, MCAModelLayers.ZOMBIE_VILLAGER_CLOTHING), "zombie"));
        addLayer(new HairLayer<>(this, createModel(ctx, MCAModelLayers.ZOMBIE_VILLAGER_HAIR)));
    }

    private static VillagerEntityModelMCA<ZombieVillagerEntityMCA> createModel(
            EntityRendererProvider.Context ctx,
            ModelLayerLocation layer
    ) {
        return new VillagerEntityModelMCA<>(ctx.bakeLayer(layer));
    }

    @Override
    protected boolean isShaking(ZombieVillagerEntityMCA entity) {
        return entity.isConverting() || entity.isUnderWaterConverting();
    }
}
