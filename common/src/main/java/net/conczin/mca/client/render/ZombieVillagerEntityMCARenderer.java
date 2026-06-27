package net.conczin.mca.client.render;

import net.conczin.mca.client.model.ModelLayersMCA;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.model.ZombieVillagerEntityModelMCA;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.SkinLayer;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class ZombieVillagerEntityMCARenderer extends VillagerLikeEntityMCARenderer<ZombieVillagerEntityMCA> {
    public ZombieVillagerEntityMCARenderer(EntityRendererProvider.Context ctx) {
        super(ctx, createModel(ctx, ModelLayersMCA.ZOMBIE_VILLAGER).hideWears(), createRawModel(VillagerEntityModelMCA.playerData(CubeDeformation.NONE)).hideWears());

        addLayer(new SkinLayer<>(this, createModel(ctx, ModelLayersMCA.ZOMBIE_VILLAGER).hideWears()));
        addLayer(new FaceLayer<>(this, createModel(ctx, ModelLayersMCA.ZOMBIE_VILLAGER).hideWears(), "normal"));
        addLayer(new ClothingLayer<>(this, createModel(ctx, ModelLayersMCA.ZOMBIE_VILLAGER), "zombie"));
        addLayer(new HairLayer<>(this, createModel(ctx, ModelLayersMCA.ZOMBIE_VILLAGER)));
    }

    private static VillagerEntityModelMCA createModel(EntityRendererProvider.Context ctx, ModelLayerLocation layer) {
        return new ZombieVillagerEntityModelMCA(ctx.bakeLayer(layer));
    }

    private static VillagerEntityModelMCA createRawModel(MeshDefinition data) {
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
