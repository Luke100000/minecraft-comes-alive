package net.conczin.mca.client.render;

import net.conczin.mca.client.model.ModelLayersMCA;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.SkinLayer;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class VillagerEntityMCARenderer extends VillagerLikeEntityMCARenderer<VillagerEntityMCA> {
    public VillagerEntityMCARenderer(EntityRendererProvider.Context ctx) {
        super(ctx, createModel(ctx, ModelLayersMCA.VILLAGER).hideWears(), createRawModel(VillagerEntityModelMCA.playerData(CubeDeformation.NONE)).hideWears());

        addLayer(new SkinLayer<>(this, createModel(ctx, ModelLayersMCA.VILLAGER).hideWears()));
        addLayer(new FaceLayer<>(this, createModel(ctx, ModelLayersMCA.VILLAGER).hideWears(), "normal"));
        addLayer(new ClothingLayer<>(this, createModel(ctx, ModelLayersMCA.VILLAGER), "normal"));
        addLayer(new HairLayer<>(this, createModel(ctx, ModelLayersMCA.VILLAGER)));
    }

    private static VillagerEntityModelMCA createModel(EntityRendererProvider.Context ctx, ModelLayerLocation layer) {
        return new VillagerEntityModelMCA(ctx.bakeLayer(layer));
    }

    private static VillagerEntityModelMCA createRawModel(MeshDefinition data) {
        return new VillagerEntityModelMCA(LayerDefinition.create(data, 64, 64).bakeRoot());
    }
}
