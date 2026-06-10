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
import net.minecraft.world.entity.player.PlayerModelType;
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

        mca$skinLayer = new SkinLayer((AvatarRenderer) (Object) this, mca$createWearlessModel(new CubeDeformation(0.0F), false));
        this.addLayer(mca$skinLayer);
        this.addLayer(new FaceLayer((AvatarRenderer) (Object) this, mca$createWearlessModel(new CubeDeformation(0.01F), false), "normal"));
        mca$clothingLayer = new ClothingLayer((AvatarRenderer) (Object) this, mca$createModel(new CubeDeformation(0.0625F), false), "normal");
        this.addLayer(mca$clothingLayer);
        this.addLayer(new HairLayer((AvatarRenderer) (Object) this, mca$createHairModel(new CubeDeformation(0.125F), false)));
    }

    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void mca$injectScale(AvatarRenderState state, PoseStack poseStack, CallbackInfo ci) {
        if (!(state instanceof VillagerStateHolder holder) || !holder.mca$isGeneticsRendererActive()) {
            if (MCAClient.isPlayerRendererAllowed()) {
                model = mca$vanillaModel;
            }
            return;
        }

        var visuals = VillagerVisualSnapshot.require(holder);
        poseStack.scale(visuals.rawHorizontalScaleFactor(), visuals.rawVerticalScaleFactor(), visuals.rawHorizontalScaleFactor());
        if (visuals.baby() && !state.isPassenger) {
            poseStack.translate(0.0F, 0.6F, 0.0F);
        }

        model = state.skin.model() == PlayerModelType.SLIM ? mca$slimVillagerModel : mca$wideVillagerModel;
    }

    @Inject(
            method = "renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;ZLnet/minecraft/client/player/AbstractClientPlayer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Lnet/minecraft/client/model/geom/ModelPart;Z)V"
            ),
            cancellable = true,
            require = 0
    )
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

        mca$renderCustomHand(player, poseStack, submitNodeCollector, lightCoords, true, hasSleeve);
        ci.cancel();
    }

    @Inject(
            method = "renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;ZLnet/minecraft/client/player/AbstractClientPlayer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Lnet/minecraft/client/model/geom/ModelPart;Z)V"
            ),
            cancellable = true,
            require = 0
    )
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

        mca$renderCustomHand(player, poseStack, submitNodeCollector, lightCoords, false, hasSleeve);
        ci.cancel();
    }

    @Unique
    private void mca$renderCustomHand(
            AbstractClientPlayer player,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            boolean rightArm,
            boolean hasSleeve
    ) {
        var visuals = MCAClient.getPlayerData(player.getUUID())
                .map(VillagerVisualSnapshot::capture)
                .orElse(null);
        if (visuals == null) {
            return;
        }

        mca$renderSkinArm(
                poseStack,
                submitNodeCollector,
                lightCoords,
                visuals,
                rightArm,
                (PlayerEntityExtendedModel<?>) mca$skinLayer.model,
                mca$skinLayer
        );
        mca$renderClothingArm(
                poseStack,
                submitNodeCollector,
                lightCoords,
                visuals,
                rightArm,
                hasSleeve,
                (PlayerEntityExtendedModel<?>) mca$clothingLayer.model,
                mca$clothingLayer
        );
    }

    @Unique
    @SuppressWarnings("rawtypes")
    private void mca$renderSkinArm(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            VillagerVisualSnapshot visuals,
            boolean rightArm,
            PlayerEntityExtendedModel<?> model,
            SkinLayer layer
    ) {
        mca$prepareArm(model, rightArm ? HumanoidArm.RIGHT : HumanoidArm.LEFT, false);

        Identifier texture = layer.getSkin(visuals);
        if (texture == null || !layer.canUse(texture)) {
            return;
        }

        int color = layer.getColor(visuals, 0.0F);
        var arm = rightArm ? model.rightArm : model.leftArm;
        submitNodeCollector.submitModelPart(arm, poseStack, RenderTypes.entityCutout(texture), lightCoords, OverlayTexture.NO_OVERLAY, null, color, null);
    }

    @Unique
    @SuppressWarnings("rawtypes")
    private void mca$renderClothingArm(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            VillagerVisualSnapshot visuals,
            boolean rightArm,
            boolean hasSleeve,
            PlayerEntityExtendedModel<?> model,
            ClothingLayer layer
    ) {
        mca$prepareArm(model, rightArm ? HumanoidArm.RIGHT : HumanoidArm.LEFT, hasSleeve);

        Identifier texture = layer.getSkin(visuals);
        if (texture == null || !layer.canUse(texture)) {
            return;
        }

        var arm = rightArm ? model.rightArm : model.leftArm;
        if (arm.visible) {
            submitNodeCollector.submitModelPart(arm, poseStack, RenderTypes.entityCutout(texture), lightCoords, OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF, null);
        }
        var sleeve = rightArm ? model.rightSleeve : model.leftSleeve;
        if (sleeve.visible) {
            submitNodeCollector.submitModelPart(sleeve, poseStack, RenderTypes.entityCutout(texture), lightCoords, OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF, null);
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
