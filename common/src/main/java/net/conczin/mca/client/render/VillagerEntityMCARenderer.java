package net.conczin.mca.client.render;

import net.conczin.mca.client.model.McaModelLayerBaker;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.SkinLayer;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class VillagerEntityMCARenderer extends VillagerLikeEntityMCARenderer<VillagerEntityMCA> {
    public VillagerEntityMCARenderer(EntityRendererProvider.Context ctx) {
        super(ctx, createAnimationModel(ctx).hideWears());

        // The parent drives external animation; visible layers keep MCA geometry and textures.
        layers.add(0, new SkinLayer<>(this, createVisibleModel(VillagerEntityModelMCA.bodyData(CubeDeformation.NONE)).hideWears()));
        addLayer(new FaceLayer<>(this, createVisibleModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.01F))).hideWears(), "normal"));
        addLayer(new ClothingLayer<>(this, createVisibleModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.0625F))), "normal"));
        addLayer(new HairLayer<>(this, createVisibleModel(VillagerEntityModelMCA.hairData(new CubeDeformation(0.125F)))));
    }

    private static VillagerEntityModelMCA<VillagerEntityMCA> createAnimationModel(EntityRendererProvider.Context ctx) {
        MeshDefinition data = VillagerEntityModelMCA.bodyData(CubeDeformation.NONE);
        return new VillagerEntityModelMCA<>(McaModelLayerBaker.bakeAnimationRoot(ctx, data));
    }

    private static VillagerEntityModelMCA<VillagerEntityMCA> createVisibleModel(MeshDefinition data) {
        return new VillagerEntityModelMCA<>(LayerDefinition.create(data, 64, 64).bakeRoot());
    }
}
