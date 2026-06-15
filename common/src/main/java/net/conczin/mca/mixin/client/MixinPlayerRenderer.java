package net.conczin.mca.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.PlayerEntityExtendedModel;
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
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
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
    private PlayerModel mca$geneticsModel;
    @Unique
    private PlayerModel mca$vanillaModel;
    @Unique
    private SkinLayer mca$skinLayer;
    @Unique
    private ClothingLayer mca$clothingLayer;

    protected MixinPlayerRenderer(EntityRendererProvider.Context context, PlayerModel model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Unique
    private static PlayerEntityExtendedModel<?> mca$createModel(CubeDeformation dilation) {
        return mca$createModel(dilation, false);
    }

    @Unique
    private static PlayerEntityExtendedModel<?> mca$createModel(CubeDeformation dilation, boolean slim) {
        return new PlayerEntityExtendedModel<>(LayerDefinition.create(VillagerEntityModelMCA.bodyData(dilation, slim), 64, 64).bakeRoot(), slim);
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
        mca$geneticsModel = mca$createModel(new CubeDeformation(0.0F), slim);

        mca$skinLayer = new SkinLayer((AvatarRenderer) (Object) this, mca$createWearlessModel(new CubeDeformation(0.0F)));
        this.addLayer(mca$skinLayer);
        this.addLayer(new FaceLayer((AvatarRenderer) (Object) this, mca$createWearlessModel(new CubeDeformation(0.01F)), "normal"));
        mca$clothingLayer = new ClothingLayer((AvatarRenderer) (Object) this, mca$createModel(new CubeDeformation(0.0625F)), "normal");
        this.addLayer(mca$clothingLayer);
        this.addLayer(new HairLayer((AvatarRenderer) (Object) this, mca$createHairModel(new CubeDeformation(0.125F))));
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

        model = mca$geneticsModel;
    }

    @Inject(
            method = "renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mca$injectRenderRightHand(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            Identifier skinTexture,
            boolean hasSleeve,
            CallbackInfo ci
    ) {
        if (mca$renderHand(Minecraft.getInstance().player, poseStack, submitNodeCollector, lightCoords, true, hasSleeve)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mca$injectRenderLeftHand(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            Identifier skinTexture,
            boolean hasSleeve,
            CallbackInfo ci
    ) {
        if (mca$renderHand(Minecraft.getInstance().player, poseStack, submitNodeCollector, lightCoords, false, hasSleeve)) {
            ci.cancel();
        }
    }

    @Unique
    private boolean mca$renderHand(
            AbstractClientPlayer player,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            boolean rightArm,
            boolean hasSleeve
    ) {
        if (player == null || mca$skinLayer == null || mca$clothingLayer == null) {
            return false;
        }

        String key = rightArm ? "right_arm" : "left_arm";
        if (!MCAClient.renderArms(player.getUUID(), key)) {
            return false;
        }

        return mca$renderCustomHand(player, poseStack, submitNodeCollector, lightCoords, rightArm, hasSleeve);
    }

    @Unique
    private boolean mca$renderCustomHand(
            AbstractClientPlayer player,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            boolean rightArm,
            boolean hasSleeve
    ) {
        var visuals = MCAClient.getPlayerData(player.getUUID())
                .map(VillagerVisuals::capture)
                .orElse(null);
        if (visuals == null) {
            return false;
        }

        PlayerEntityExtendedModel<?> skinModel = (PlayerEntityExtendedModel<?>) mca$skinLayer.model;
        PlayerEntityExtendedModel<?> clothingModel = (PlayerEntityExtendedModel<?>) mca$clothingLayer.model;
        skinModel.applyVillagerDimensions(visuals, player.isCrouching());
        clothingModel.applyVillagerDimensions(visuals, player.isCrouching());

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
                hasSleeve,
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
        mca$prepareArm(model, rightArm ? HumanoidArm.RIGHT : HumanoidArm.LEFT, false);

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
            boolean hasSleeve,
            PlayerEntityExtendedModel<?> model,
            ClothingLayer layer
    ) {
        mca$prepareArm(model, rightArm ? HumanoidArm.RIGHT : HumanoidArm.LEFT, hasSleeve);

        Identifier texture = layer.getSkin(visuals);
        if (texture == null || !layer.canUse(texture)) {
            return false;
        }

        var arm = rightArm ? model.rightArm : model.leftArm;
        if (arm.visible) {
            submitNodeCollector.submitModelPart(arm, poseStack, RenderTypes.entityCutout(texture), lightCoords, OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF, null);
            return true;
        }
        return false;
    }

    @Unique
    private static void mca$prepareArm(PlayerEntityExtendedModel<?> model, HumanoidArm arm, boolean hasSleeve) {
        var armPart = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        var sleevePart = arm == HumanoidArm.RIGHT ? model.rightSleeve : model.leftSleeve;

        model.setAllVisible(false);
        armPart.resetPose();
        sleevePart.resetPose();
        armPart.visible = true;
        sleevePart.visible = hasSleeve;
        model.leftArm.zRot = -0.1F;
        model.rightArm.zRot = 0.1F;
    }
}
