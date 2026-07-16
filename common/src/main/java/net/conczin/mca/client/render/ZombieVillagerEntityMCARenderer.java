package net.conczin.mca.client.render;

import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.model.ZombieVillagerEntityModelMCA;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.SkinLayer;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class ZombieVillagerEntityMCARenderer extends VillagerLikeEntityMCARenderer<ZombieVillagerEntityMCA> {
    public ZombieVillagerEntityMCARenderer(EntityRendererProvider.Context ctx) {
        super(ctx, createAnimationModel(ctx).hideWears());

        layers.add(0, new SkinLayer<>(this, createVisibleModel(VillagerEntityModelMCA.bodyData(CubeDeformation.NONE)).hideWears()));
        addLayer(new FaceLayer<>(this, createVisibleModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.01F))).hideWears(), "normal"));
        addLayer(new ClothingLayer<>(this, createVisibleModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.075F))), "zombie"));
        addLayer(new HairLayer<>(this, createVisibleModel(VillagerEntityModelMCA.hairData(new CubeDeformation(0.1F)))));
    }

    private static VillagerEntityModelMCA<ZombieVillagerEntityMCA> createAnimationModel(EntityRendererProvider.Context ctx) {
        return new ZombieVillagerEntityModelMCA<>(ctx.bakeLayer(ModelLayers.PLAYER));
    }

    private static VillagerEntityModelMCA<ZombieVillagerEntityMCA> createVisibleModel(MeshDefinition data) {
        return new ZombieVillagerEntityModelMCA<>(LayerDefinition.create(data, 64, 64).bakeRoot());
    }

    @Override
    protected boolean isShaking(ZombieVillagerEntityMCA entity) {
        return entity.isConverting() || entity.isUnderWaterConverting();
    }
}
