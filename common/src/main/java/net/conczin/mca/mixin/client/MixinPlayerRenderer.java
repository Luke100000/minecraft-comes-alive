package net.conczin.mca.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.PlayerEntityExtendedModel;
import net.conczin.mca.client.model.McaModelAnimationDriver;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.SkinLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class MixinPlayerRenderer extends LivingEntityRenderer<LivingEntity, AvatarRenderState, PlayerModel> {
    @Unique
    private PlayerEntityExtendedModel<?> mca$villagerAnimationModel;
    @Unique
    private PlayerModel mca$vanillaModel;
    @Unique
    private PlayerEntityExtendedModel<?> mca$playerModel;
    @Unique
    private SkinLayer mca$skinLayer;
    @Unique
    private ClothingLayer mca$clothingLayer;
    @Unique
    private PlayerEntityExtendedModel<?> mca$firstPersonRightSkinModel;
    @Unique
    private PlayerEntityExtendedModel<?> mca$firstPersonLeftSkinModel;
    @Unique
    private PlayerEntityExtendedModel<?> mca$firstPersonRightClothingModel;
    @Unique
    private PlayerEntityExtendedModel<?> mca$firstPersonLeftClothingModel;
    @Unique
    private AbstractClientPlayer mca$firstPersonPlayer;
    @Unique
    private ModelPart mca$firstPersonSourceArm;
    @Unique
    private boolean mca$firstPersonRightArm;
    @Unique
    private boolean mca$firstPersonHasSleeve;

    protected MixinPlayerRenderer(EntityRendererProvider.Context context, PlayerModel model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Unique
    private static PlayerEntityExtendedModel<?> mca$createModel(CubeDeformation dilation) {
        return mca$createModel(dilation, false);
    }

    @Unique
    private static PlayerEntityExtendedModel<?> mca$createModel(CubeDeformation dilation, boolean slim) {
        return new PlayerEntityExtendedModel<>(LayerDefinition.create(VillagerEntityModelMCA.bodyData(dilation, slim), 64, 64).bakeRoot(), slim)
                .receiveDeferredAnimationPose();
    }

    @Unique
    private static PlayerEntityExtendedModel<?> mca$createWearlessModel(CubeDeformation dilation) {
        return mca$createModel(dilation).hideWears();
    }

    @Unique
    private static PlayerEntityExtendedModel<?> mca$createHairModel(CubeDeformation dilation) {
        return new PlayerEntityExtendedModel<>(LayerDefinition.create(VillagerEntityModelMCA.hairData(dilation), 64, 64).bakeRoot())
                .receiveDeferredAnimationPose()
                .hideWears();
    }

    @Unique
    private static PlayerEntityExtendedModel<?> mca$createAnimationModel(EntityRendererProvider.Context ctx) {
        return new PlayerEntityExtendedModel<>(ctx.bakeLayer(ModelLayers.PLAYER));
    }

    @Unique
    private static PlayerEntityExtendedModel<?> mca$createPlayerModel(EntityRendererProvider.Context ctx, boolean slim) {
        var mcaPartsRoot = LayerDefinition.create(VillagerEntityModelMCA.bodyData(CubeDeformation.NONE, slim), 64, 64).bakeRoot();
        return new PlayerEntityExtendedModel<>(ctx.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim, mcaPartsRoot);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V", at = @At("TAIL"))
    private void mca$injectInit(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        if (!MCAClient.isPlayerRendererAllowed()) {
            return;
        }

        mca$vanillaModel = model;
        // The villager model must be the renderer's active model: EMF only applies its manual
        // animation hook to LivingEntityRenderer#getModel(). MCA layers then copy those poses.
        mca$villagerAnimationModel = mca$createAnimationModel(ctx);
        mca$playerModel = mca$createPlayerModel(ctx, slim);

        mca$skinLayer = new SkinLayer((AvatarRenderer) (Object) this, mca$createWearlessModel(new CubeDeformation(0.0F)));
        this.layers.add(0, mca$skinLayer);
        this.addLayer(new FaceLayer((AvatarRenderer) (Object) this, mca$createWearlessModel(new CubeDeformation(0.01F)), "normal"));
        mca$clothingLayer = new ClothingLayer((AvatarRenderer) (Object) this, mca$createModel(new CubeDeformation(0.0625F)), "normal");
        this.addLayer(mca$clothingLayer);
        this.addLayer(new HairLayer((AvatarRenderer) (Object) this, mca$createHairModel(new CubeDeformation(0.125F))));

        // Model-part submissions retain the ModelPart reference until the deferred feature pass.
        // Keep first-person geometry separate from the layer models, which are mutated by queued
        // third-person model submissions before model parts are drawn.
        mca$firstPersonRightSkinModel = mca$createWearlessModel(new CubeDeformation(0.0F));
        mca$firstPersonLeftSkinModel = mca$createWearlessModel(new CubeDeformation(0.0F));
        mca$firstPersonRightClothingModel = mca$createModel(new CubeDeformation(0.0625F));
        mca$firstPersonLeftClothingModel = mca$createModel(new CubeDeformation(0.0625F));
    }

    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void mca$injectScale(AvatarRenderState state, PoseStack poseStack, CallbackInfo ci) {
        if (!(state instanceof VillagerStateHolder holder) || !holder.mca$isGeneticsRendererActive()) {
            if (MCAClient.isPlayerRendererAllowed()) {
                model = mca$vanillaModel;
            }
            return;
        }

        var visuals = VillagerVisuals.require(holder);
        poseStack.scale(visuals.rawHorizontalScaleFactor(), visuals.rawVerticalScaleFactor(), visuals.rawHorizontalScaleFactor());
        if (visuals.baby() && !state.isPassenger) {
            poseStack.translate(0.0F, 0.6F, 0.0F);
        }

        model = holder.mca$isVillagerRendererActive() ? mca$villagerAnimationModel : mca$playerModel;
    }

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void mca$beginRenderHand(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            Identifier skinTexture,
            ModelPart arm,
            boolean hasSleeve,
            CallbackInfo ci
    ) {
        mca$firstPersonPlayer = Minecraft.getInstance().player;
        mca$firstPersonSourceArm = arm;
        mca$firstPersonRightArm = arm == model.rightArm;
        mca$firstPersonHasSleeve = hasSleeve;
    }

    @WrapOperation(
            method = "renderHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"
            )
    )
    private void mca$submitCustomFirstPersonHand(
            SubmitNodeCollector submitNodeCollector,
            ModelPart originalArm,
            PoseStack poseStack,
            RenderType renderType,
            int lightCoords,
            int overlayCoords,
            TextureAtlasSprite sprite,
            Operation<Void> original
    ) {
        if (!mca$renderHand(
                mca$firstPersonPlayer,
                poseStack,
                submitNodeCollector,
                lightCoords,
                mca$firstPersonRightArm
        )) {
            original.call(submitNodeCollector, originalArm, poseStack, renderType, lightCoords, overlayCoords, sprite);
        }
    }

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void mca$endRenderHand(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            Identifier skinTexture,
            ModelPart arm,
            boolean hasSleeve,
            CallbackInfo ci
    ) {
        mca$firstPersonPlayer = null;
        mca$firstPersonSourceArm = null;
        mca$firstPersonHasSleeve = false;
    }

    @Unique
    private boolean mca$renderHand(
            AbstractClientPlayer player,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            boolean rightArm
    ) {
        if (player == null || mca$skinLayer == null || mca$clothingLayer == null) {
            return false;
        }

        String key = rightArm ? "right_arm" : "left_arm";
        if (!MCAClient.renderArms(player.getUUID(), key)) {
            return false;
        }
        var visuals = MCAClient.getPlayerData(player.getUUID())
                .map(VillagerVisuals::capture)
                .orElse(null);
        if (visuals == null) {
            return false;
        }

        var animatedArm = rightArm ? mca$villagerAnimationModel.rightArm : mca$villagerAnimationModel.leftArm;
        var animatedSleeve = rightArm ? mca$villagerAnimationModel.rightSleeve : mca$villagerAnimationModel.leftSleeve;
        if (mca$firstPersonSourceArm == null) {
            return false;
        }
        animatedArm.loadPose(mca$firstPersonSourceArm.storePose());
        animatedArm.visible = true;
        animatedArm.xRot = 0.0F;
        McaModelAnimationDriver.animate(animatedArm, poseStack, lightCoords, OverlayTexture.NO_OVERLAY);

        PlayerEntityExtendedModel<?> skinModel = rightArm ? mca$firstPersonRightSkinModel : mca$firstPersonLeftSkinModel;
        PlayerEntityExtendedModel<?> clothingModel = rightArm ? mca$firstPersonRightClothingModel : mca$firstPersonLeftClothingModel;
        skinModel.applyVillagerDimensions(visuals);
        clothingModel.applyVillagerDimensions(visuals);
        mca$prepareArm(skinModel, rightArm ? HumanoidArm.RIGHT : HumanoidArm.LEFT, false);
        mca$prepareArm(clothingModel, rightArm ? HumanoidArm.RIGHT : HumanoidArm.LEFT, mca$firstPersonHasSleeve);
        mca$copyArmPose(skinModel, animatedArm, animatedSleeve, rightArm);
        mca$copyArmPose(clothingModel, animatedArm, animatedSleeve, rightArm);

        boolean renderedSkin = mca$renderSkinArm(
                poseStack,
                submitNodeCollector,
                lightCoords,
                visuals,
                rightArm,
                skinModel,
                mca$skinLayer
        );
        boolean renderedClothing = mca$renderClothingArm(
                poseStack,
                submitNodeCollector,
                lightCoords,
                visuals,
                rightArm,
                clothingModel,
                mca$clothingLayer
        );
        return renderedSkin || renderedClothing;
    }

    @Unique
    @SuppressWarnings("rawtypes")
    private boolean mca$renderSkinArm(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            VillagerVisuals visuals,
            boolean rightArm,
            PlayerEntityExtendedModel<?> model,
            SkinLayer layer
    ) {
        Identifier texture = layer.getSkin(visuals);
        if (texture == null || !layer.canUse(texture)) {
            return false;
        }

        int color = layer.getColor(visuals, 0.0F);
        var arm = rightArm ? model.rightArm : model.leftArm;
        submitNodeCollector.submitModelPart(arm, poseStack, RenderTypes.entityCutout(texture), lightCoords, OverlayTexture.NO_OVERLAY, null, color, null);
        return true;
    }

    @Unique
    @SuppressWarnings("rawtypes")
    private boolean mca$renderClothingArm(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            VillagerVisuals visuals,
            boolean rightArm,
            PlayerEntityExtendedModel<?> model,
            ClothingLayer layer
    ) {
        Identifier texture = layer.getSkin(visuals);
        if (texture == null || !layer.canUse(texture)) {
            return false;
        }

        var arm = rightArm ? model.rightArm : model.leftArm;
        submitNodeCollector.submitModelPart(arm, poseStack, RenderTypes.entityCutout(texture), lightCoords, OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF, null);
        return true;
    }

    @Unique
    private static void mca$prepareArm(PlayerEntityExtendedModel<?> model, HumanoidArm arm, boolean showSleeve) {
        var armPart = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        var sleevePart = arm == HumanoidArm.RIGHT ? model.rightSleeve : model.leftSleeve;

        model.setAllVisible(false);
        armPart.resetPose();
        sleevePart.resetPose();
        armPart.visible = true;
        sleevePart.visible = showSleeve;
        model.leftArm.zRot = -0.1F;
        model.rightArm.zRot = 0.1F;
    }

    @Unique
    private static void mca$copyArmPose(PlayerEntityExtendedModel<?> model, ModelPart animatedArm, ModelPart animatedSleeve, boolean rightArm) {
        ModelPart arm = rightArm ? model.rightArm : model.leftArm;
        ModelPart sleeve = rightArm ? model.rightSleeve : model.leftSleeve;
        arm.loadPose(animatedArm.storePose());
        sleeve.loadPose(animatedSleeve.storePose());
    }
}
