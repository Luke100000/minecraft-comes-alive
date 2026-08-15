package net.conczin.mca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.client.model.GrimReaperEntityModel;
import net.conczin.mca.entity.GrimReaperEntity;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class GrimReaperRenderer extends HumanoidMobRenderer<GrimReaperEntity, GrimReaperEntityModel<GrimReaperEntity>> {
    private static final ResourceLocation TEXTURE = MCA.locate("textures/entity/grimreaper.png");

    public GrimReaperRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new GrimReaperEntityModel<>(
            LayerDefinition.create(GrimReaperEntityModel.getModelData(CubeDeformation.NONE), 64, 64).bakeRoot()
        ), 0.5F);
    }

    @Override
    protected void scale(GrimReaperEntity reaper, PoseStack matrices, float tickDelta) {
        matrices.scale(1.3F, 1.3F, 1.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(GrimReaperEntity reaper) {
        return TEXTURE;
    }
}
