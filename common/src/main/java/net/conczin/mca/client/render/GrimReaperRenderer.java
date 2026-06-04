package net.conczin.mca.client.render;

import net.conczin.mca.MCA;
import net.conczin.mca.client.model.GrimReaperEntityModel;
import net.conczin.mca.entity.GrimReaperEntity;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

public class GrimReaperRenderer extends HumanoidMobRenderer<GrimReaperEntity, HumanoidRenderState, GrimReaperEntityModel> {
    private static final Identifier TEXTURE = MCA.locate("textures/entity/grimreaper.png");

    public GrimReaperRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new GrimReaperEntityModel(
                LayerDefinition.create(GrimReaperEntityModel.getModelData(CubeDeformation.NONE), 64, 64).bakeRoot()
        ), 0.5F);
    }

    @Override
    protected void scale(HumanoidRenderState renderState, com.mojang.blaze3d.vertex.PoseStack matrices) {
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }
}
