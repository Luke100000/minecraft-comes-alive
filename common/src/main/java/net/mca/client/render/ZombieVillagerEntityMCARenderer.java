package net.mca.client.render;

import net.mca.client.model.VillagerEntityModelMCA;
import net.mca.client.model.ZombieVillagerEntityModelMCA;
import net.mca.client.render.layer.ClothingLayer;
import net.mca.client.render.layer.FaceLayer;
import net.mca.client.render.layer.HairLayer;
import net.mca.client.render.layer.SkinLayer;
import net.mca.entity.ZombieVillagerEntityMCA;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class ZombieVillagerEntityMCARenderer extends VillagerLikeEntityMCARenderer<ZombieVillagerEntityMCA> {
    public ZombieVillagerEntityMCARenderer(EntityRendererProvider.Context ctx) {
        super(ctx, createModel(VillagerEntityModelMCA.bodyData(CubeDeformation.NONE)).hideWears());

        addLayer(new SkinLayer<>(this, model));
        addLayer(new FaceLayer<>(this, createModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.01F))).hideWears(), "normal"));
        addLayer(new ClothingLayer<>(this, createModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.075F))), "zombie"));
        addLayer(new HairLayer<>(this, createModel(VillagerEntityModelMCA.hairData(new CubeDeformation(0.1F)))));
    }

    private static VillagerEntityModelMCA<ZombieVillagerEntityMCA> createModel(MeshDefinition data) {
        return new ZombieVillagerEntityModelMCA<>(LayerDefinition.create(data, 64, 64).bakeRoot());
    }

    @Override
    protected boolean isShaking(ZombieVillagerEntityMCA entity) {
        return entity.isConverting() || entity.isUnderWaterConverting();
    }
}
