package net.conczin.mca.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.PlayerEntityExtendedModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.conczin.mca.client.render.VillagerVisualSnapshot;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.SkinLayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class MixinPlayerRenderer extends LivingEntityRenderer<LivingEntity, PlayerRenderState, PlayerModel> {
    @Unique
    private PlayerModel mca$wideVillagerModel;
    @Unique
    private PlayerModel mca$vanillaModel;

    protected MixinPlayerRenderer(EntityRendererProvider.Context context, PlayerModel model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Unique
    private static PlayerEntityExtendedModel<?> mca$createModel(CubeDeformation dilation) {
        return new PlayerEntityExtendedModel<>(LayerDefinition.create(VillagerEntityModelMCA.bodyData(dilation), 64, 64).bakeRoot());
    }

    @Unique
    private static PlayerEntityExtendedModel<?> mca$createWearlessModel(CubeDeformation dilation) {
        return mca$createModel(dilation).hideWears();
    }

    @Unique
    private static PlayerEntityExtendedModel<?> mca$createHairModel(CubeDeformation dilation) {
        return new PlayerEntityExtendedModel<>(LayerDefinition.create(VillagerEntityModelMCA.hairData(dilation), 64, 64).bakeRoot());
    }

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V", at = @At("TAIL"))
    private void mca$injectInit(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        if (!MCAClient.isPlayerRendererAllowed()) {
            return;
        }

        mca$vanillaModel = model;
        mca$wideVillagerModel = mca$createModel(new CubeDeformation(0.0F));

        this.addLayer(new SkinLayer(this, mca$createWearlessModel(new CubeDeformation(0.0F))));
        this.addLayer(new FaceLayer(this, mca$createWearlessModel(new CubeDeformation(0.01F)), "normal"));
        this.addLayer(new ClothingLayer(this, mca$createModel(new CubeDeformation(0.0625F)), "normal"));
        this.addLayer(new HairLayer(this, mca$createHairModel(new CubeDeformation(0.125F))));
    }

    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void mca$injectScale(PlayerRenderState state, PoseStack poseStack, CallbackInfo ci) {
        if (!(state instanceof VillagerStateHolder holder) || !holder.mca$isGeneticsRendererActive()) {
            if (MCAClient.isPlayerRendererAllowed()) {
                model = mca$vanillaModel;
            }
            return;
        }

        VillagerVisualSnapshot visuals = VillagerVisualSnapshot.require(holder);
        poseStack.scale(visuals.rawHorizontalScaleFactor(), visuals.rawVerticalScaleFactor(), visuals.rawHorizontalScaleFactor());
        if (visuals.baby() && !state.isPassenger) {
            poseStack.translate(0.0F, 0.6F, 0.0F);
        }

        model = mca$wideVillagerModel;
    }
}
