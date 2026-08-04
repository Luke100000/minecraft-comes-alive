package net.conczin.mca.client.render;

import net.conczin.mca.client.model.MCAModelLayers;
import net.conczin.mca.client.model.PlayerAnimationBridge;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class VillagerEntityMCARenderer extends VillagerLikeEntityMCARenderer<VillagerEntityMCA> {
    public VillagerEntityMCARenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new VillagerEntityModelMCA<VillagerEntityMCA>(
                ctx.bakeLayer(MCAModelLayers.VILLAGER),
                new PlayerAnimationBridge<VillagerEntityMCA>(new PlayerModel<VillagerEntityMCA>(ctx.bakeLayer(ModelLayers.PLAYER), false))
        ).hideWears());

        addLayer(new FaceLayer<>(this, createModel(ctx, MCAModelLayers.VILLAGER_FACE).hideWears(), "normal"));
        addLayer(new ClothingLayer<>(this, createModel(ctx, MCAModelLayers.VILLAGER_CLOTHING), "normal"));
        addLayer(new HairLayer<>(this, createModel(ctx, MCAModelLayers.VILLAGER_HAIR)));
    }

    private static VillagerEntityModelMCA<VillagerEntityMCA> createModel(
            EntityRendererProvider.Context ctx,
            ModelLayerLocation layer
    ) {
        return new VillagerEntityModelMCA<VillagerEntityMCA>(ctx.bakeLayer(layer));
    }
}
