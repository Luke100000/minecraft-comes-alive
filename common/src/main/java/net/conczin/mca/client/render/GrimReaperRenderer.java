package net.conczin.mca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.client.model.GrimReaperEntityModel;
import net.conczin.mca.entity.GrimReaperEntity;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

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
        state.reaper = entity;
        state.attackState = entity.getAttackState();
    }

    @Override
    public void submit(GrimReaperRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        GrimReaperEntity reaper = state.reaper;
        if (reaper == null) {
            return;
        }

        poseStack.pushPose();
        float scale = state.scale;
        poseStack.scale(scale, scale, scale);
        this.setupRotations(state, poseStack, state.bodyRot, scale);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        this.scale(state, poseStack);
        poseStack.translate(0.0F, -1.501F, 0.0F);

        this.model.setupAnim(state);

        boolean isBodyVisible = this.isBodyVisible(state);
        boolean forceTransparent = !isBodyVisible && !state.isInvisibleToPlayer;
        RenderType renderType = this.getRenderType(state, isBodyVisible, forceTransparent, state.appearsGlowing());
        if (renderType != null) {
            int overlayCoords = LivingEntityRenderer.getOverlayCoords(state, this.getWhiteOverlayProgress(state));
            int baseColor = forceTransparent ? 654311423 : -1;
            int tintedColor = ARGB.multiply(baseColor, this.getModelTint(state));
            submitNodeCollector.submitModel(
                this.model, state, poseStack, renderType, state.lightCoords, overlayCoords, tintedColor, null, state.outlineColor, null
            );
        }

        poseStack.popPose();
        if (state.leashStates != null) {
            for (EntityRenderState.LeashState leashState : state.leashStates) {
                submitNodeCollector.submitLeash(poseStack, leashState);
            }
        }

        this.submitNameDisplay(state, poseStack, submitNodeCollector, camera);
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
