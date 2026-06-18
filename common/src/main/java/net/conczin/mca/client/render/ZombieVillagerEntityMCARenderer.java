package net.conczin.mca.client.render;

import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.model.ZombieVillagerEntityModelMCA;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.SkinLayer;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class ZombieVillagerEntityMCARenderer extends VillagerLikeEntityMCARenderer<ZombieVillagerEntityMCA> {
    public ZombieVillagerEntityMCARenderer(EntityRendererProvider.Context ctx) {
        super(ctx, createModel(VillagerEntityModelMCA.bodyData(CubeDeformation.NONE)).hideWears());

        addLayer(new SkinLayer<>(this, model));
        addLayer(new FaceLayer<>(this, createModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.01F))).hideWears(), "zombie"));
        addLayer(new ClothingLayer<>(this, createModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.075F))), "zombie"));
        addLayer(new HairLayer<>(this, createModel(VillagerEntityModelMCA.hairData(new CubeDeformation(0.1F))).hideWears()));
    }

    private static VillagerEntityModelMCA createModel(MeshDefinition data) {
        return new ZombieVillagerEntityModelMCA(LayerDefinition.create(data, 64, 64).bakeRoot());
    }

    @Override
    public void extractRenderState(ZombieVillagerEntityMCA entity, VillagerRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.isConverting = entity.isConverting() || entity.isUnderWaterConverting();
    }

    @Override
    protected boolean isShaking(VillagerRenderState state) {
        return state.isConverting;
    }
}
