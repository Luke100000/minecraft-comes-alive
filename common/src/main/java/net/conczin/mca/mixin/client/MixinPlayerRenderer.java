package net.conczin.mca.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.model.PlayerEntityExtendedModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.conczin.mca.client.render.VillagerVisualSnapshot;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.SkinLayer;
import net.conczin.mca.client.render.LivingEntityRenderContext;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.Minecraft;
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
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class MixinPlayerRenderer extends LivingEntityRenderer<LivingEntity, AvatarRenderState, PlayerModel> {

    @Unique
    private PlayerModel mca$wideVillagerModel;
    @Unique
    private PlayerModel mca$slimVillagerModel;
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
    private static PlayerEntityExtendedModel<?> mca$createModel(CubeDeformation dilation, boolean slim) {
        return new PlayerEntityExtendedModel<>(LayerDefinition.create(VillagerEntityModelMCA.bodyData(dilation, slim), 64, 64).bakeRoot(), slim);
    }

    @Unique
    private static PlayerEntityExtendedModel<?> mca$createWearlessModel(CubeDeformation dilation, boolean slim) {
        return mca$createModel(dilation, slim).hideWears();
    }

    @Unique
    private static PlayerEntityExtendedModel<?> mca$createHairModel(CubeDeformation dilation, boolean slim) {
        return new PlayerEntityExtendedModel<>(LayerDefinition.create(VillagerEntityModelMCA.hairData(dilation, slim), 64, 64).bakeRoot(), slim);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V", at = @At("TAIL"))
    private void mca$injectInit(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        if (!MCAClient.isPlayerRendererAllowed()) {
            return;
        }

        mca$vanillaModel = model;
        mca$wideVillagerModel = mca$createModel(new CubeDeformation(0.0F), false);
        mca$slimVillagerModel = mca$createModel(new CubeDeformation(0.0F), true);

        mca$skinLayer = new SkinLayer((AvatarRenderer) (Object) this, mca$createWearlessModel(new CubeDeformation(0.0F), slim));
        this.addLayer(mca$skinLayer);
        this.addLayer(new FaceLayer((AvatarRenderer) (Object) this, mca$createWearlessModel(new CubeDeformation(0.01F), slim), "normal"));
        mca$clothingLayer = new ClothingLayer((AvatarRenderer) (Object) this, mca$createModel(new CubeDeformation(0.0625F), slim), "normal");
        this.addLayer(mca$clothingLayer);
        this.addLayer(new HairLayer((AvatarRenderer) (Object) this, mca$createHairModel(new CubeDeformation(0.125F), slim)));
    }

    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void mca$injectScale(AvatarRenderState state, PoseStack poseStack, CallbackInfo ci) {
        if (!LivingEntityRenderContext.isGeneticsRendererActive()) {
            if (MCAClient.isPlayerRendererAllowed()) {
                model = mca$vanillaModel;
            }
            return;
        }

        var visuals = CommonVillagerModel.getVisuals((net.conczin.mca.client.render.VillagerStateHolder) state);
        float verticalScale = Math.max(visuals.rawVerticalScaleFactor(), 1.0E-4F);
        float horizontalRatio = visuals.rawHorizontalScaleFactor() / verticalScale;
        poseStack.scale(horizontalRatio, 1.0F, horizontalRatio);
        if (visuals.baby() && !state.isPassenger) {
            poseStack.translate(0.0F, 0.6F, 0.0F);
        }

        model = state.skin.model() == PlayerModelType.SLIM ? mca$slimVillagerModel : mca$wideVillagerModel;
    }

    @Inject(method = "renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V", at = @At("HEAD"), cancellable = true)
    private void mca$injectRenderRightHand(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            Identifier skinTexture,
            boolean hasSleeve,
            CallbackInfo ci
    ) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (!MCAClient.renderArms(player.getUUID(), "right_arm") || mca$skinLayer == null || mca$clothingLayer == null) {
            return;
        }

        AvatarRenderState state = mca$createArmState(player);
        mca$renderCustomArm(poseStack, submitNodeCollector, lightCoords, state, true, hasSleeve, (PlayerEntityExtendedModel<?>) mca$skinLayer.model, mca$skinLayer);
        mca$renderCustomArm(poseStack, submitNodeCollector, lightCoords, state, true, hasSleeve, (PlayerEntityExtendedModel<?>) mca$clothingLayer.model, mca$clothingLayer);
        ci.cancel();
    }

    @Inject(method = "renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;ZLnet/minecraft/client/player/AbstractClientPlayer;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void mca$injectRenderRightHandLive(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            Identifier skinTexture,
            boolean hasSleeve,
            AbstractClientPlayer player,
            CallbackInfo ci
    ) {
        if (!MCAClient.renderArms(player.getUUID(), "right_arm") || mca$skinLayer == null || mca$clothingLayer == null) {
            return;
        }

        AvatarRenderState state = mca$createArmState(player);
        mca$renderCustomArm(poseStack, submitNodeCollector, lightCoords, state, true, hasSleeve, (PlayerEntityExtendedModel<?>) mca$skinLayer.model, mca$skinLayer);
        mca$renderCustomArm(poseStack, submitNodeCollector, lightCoords, state, true, hasSleeve, (PlayerEntityExtendedModel<?>) mca$clothingLayer.model, mca$clothingLayer);
        ci.cancel();
    }

    @Inject(method = "renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V", at = @At("HEAD"), cancellable = true)
    private void mca$injectRenderLeftHand(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            Identifier skinTexture,
            boolean hasSleeve,
            CallbackInfo ci
    ) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (!MCAClient.renderArms(player.getUUID(), "left_arm") || mca$skinLayer == null || mca$clothingLayer == null) {
            return;
        }

        AvatarRenderState state = mca$createArmState(player);
        mca$renderCustomArm(poseStack, submitNodeCollector, lightCoords, state, false, hasSleeve, (PlayerEntityExtendedModel<?>) mca$skinLayer.model, mca$skinLayer);
        mca$renderCustomArm(poseStack, submitNodeCollector, lightCoords, state, false, hasSleeve, (PlayerEntityExtendedModel<?>) mca$clothingLayer.model, mca$clothingLayer);
        ci.cancel();
    }

    @Inject(method = "renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;ZLnet/minecraft/client/player/AbstractClientPlayer;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void mca$injectRenderLeftHandLive(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            Identifier skinTexture,
            boolean hasSleeve,
            AbstractClientPlayer player,
            CallbackInfo ci
    ) {
        if (!MCAClient.renderArms(player.getUUID(), "left_arm") || mca$skinLayer == null || mca$clothingLayer == null) {
            return;
        }

        AvatarRenderState state = mca$createArmState(player);
        mca$renderCustomArm(poseStack, submitNodeCollector, lightCoords, state, false, hasSleeve, (PlayerEntityExtendedModel<?>) mca$skinLayer.model, mca$skinLayer);
        mca$renderCustomArm(poseStack, submitNodeCollector, lightCoords, state, false, hasSleeve, (PlayerEntityExtendedModel<?>) mca$clothingLayer.model, mca$clothingLayer);
        ci.cancel();
    }

    @Unique
    private AvatarRenderState mca$createArmState(AbstractClientPlayer player) {
        AvatarRenderState state = new AvatarRenderState();
        ((AvatarRenderer) (Object) this).extractRenderState(player, state, 0.0F);
        if (state instanceof VillagerStateHolder holder) {
            var villager = CommonVillagerModel.getVillager(player);
            holder.mca$setVillager(villager);
            holder.mca$setVisualSnapshot(VillagerVisualSnapshot.capture(villager));
        }
        state.attackTime = 0.0F;
        state.isCrouching = false;
        state.swimAmount = 0.0F;
        state.isUsingItem = false;
        state.isPassenger = false;
        state.showHat = player.isModelPartShown(PlayerModelPart.HAT);
        state.showJacket = player.isModelPartShown(PlayerModelPart.JACKET);
        state.showLeftSleeve = player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        state.showRightSleeve = player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        state.showLeftPants = player.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
        state.showRightPants = player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
        return state;
    }

    @Unique
    @SuppressWarnings("rawtypes")
    private void mca$renderCustomArm(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            AvatarRenderState state,
            boolean rightArm,
            boolean hasSleeve,
            PlayerEntityExtendedModel<?> model,
            SkinLayer layer
    ) {
        mca$prepareArm(model, rightArm ? HumanoidArm.RIGHT : HumanoidArm.LEFT, false);

        Identifier texture = layer.getSkin(state);
        if (texture == null || !layer.canUse(texture)) {
            return;
        }

        int color = layer.getColor(state, 0.0F);
        var arm = rightArm ? model.rightArm : model.leftArm;
        submitNodeCollector.submitModelPart(arm, poseStack, RenderTypes.entityCutout(texture), lightCoords, OverlayTexture.NO_OVERLAY, null, color, null);
    }

    @Unique
    @SuppressWarnings("rawtypes")
    private void mca$renderCustomArm(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            AvatarRenderState state,
            boolean rightArm,
            boolean hasSleeve,
            PlayerEntityExtendedModel<?> model,
            ClothingLayer layer
    ) {
        mca$prepareArm(model, rightArm ? HumanoidArm.RIGHT : HumanoidArm.LEFT, hasSleeve);

        Identifier texture = layer.getSkin(state);
        if (texture == null || !layer.canUse(texture)) {
            return;
        }

        int color = layer.getColor(state, 0.0F);
        var sleeve = rightArm ? model.rightSleeve : model.leftSleeve;
        if (hasSleeve && sleeve.visible) {
            submitNodeCollector.submitModelPart(sleeve, poseStack, RenderTypes.entityCutout(texture), lightCoords, OverlayTexture.NO_OVERLAY, null, color, null);
        }
    }

    @Unique
    private static void mca$prepareArm(PlayerEntityExtendedModel<?> model, HumanoidArm arm, boolean hasSleeve) {
        var armPart = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        var sleevePart = arm == HumanoidArm.RIGHT ? model.rightSleeve : model.leftSleeve;

        armPart.resetPose();
        sleevePart.resetPose();
        armPart.visible = true;
        sleevePart.visible = hasSleeve;
        model.leftSleeve.visible = arm == HumanoidArm.LEFT && hasSleeve;
        model.rightSleeve.visible = arm == HumanoidArm.RIGHT && hasSleeve;
        model.leftArm.zRot = -0.1F;
        model.rightArm.zRot = 0.1F;
    }
}
