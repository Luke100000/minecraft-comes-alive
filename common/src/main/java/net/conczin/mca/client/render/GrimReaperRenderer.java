package net.conczin.mca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.client.model.GrimReaperEntityModel;
import net.conczin.mca.entity.GrimReaperEntity;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

@SuppressWarnings({"rawtypes", "unchecked"})
public class GrimReaperRenderer extends HumanoidMobRenderer<GrimReaperEntity, GrimReaperRenderState, GrimReaperEntityModel> {
    private static final Identifier TEXTURE = MCA.locate("textures/entity/grimreaper.png");

    public GrimReaperRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new GrimReaperEntityModel(
                LayerDefinition.create(GrimReaperEntityModel.getModelData(CubeDeformation.NONE), 64, 64).bakeRoot()
        ), 0.5F);
    }

    @Override
    public GrimReaperRenderState createRenderState() {
        return new GrimReaperRenderState();
    }

    @Override
    public void extractRenderState(GrimReaperEntity entity, GrimReaperRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.attackState = entity.getAttackState();
    }

    @Override
    protected void scale(GrimReaperRenderState state, PoseStack matrices) {
        matrices.scale(1.3F, 1.3F, 1.3F);
    }

    @Override
    public Identifier getTextureLocation(GrimReaperRenderState state) {
        return TEXTURE;
    }
}
