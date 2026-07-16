package net.conczin.mca.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.model.McaModelAnimationDriver;
import net.conczin.mca.client.model.PlayerEntityExtendedModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.layer.*;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class MixinPlayerRenderer extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    @Unique
    private SkinLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> mca$skinLayer;
    @Unique
    private ClothingLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> mca$clothingLayer;
    @Unique
    private PlayerEntityExtendedModel<AbstractClientPlayer> mca$villagerAnimationModel;
    @Unique
    private PlayerEntityExtendedModel<AbstractClientPlayer> mca$playerModel;
    @Unique
    private PlayerModel<AbstractClientPlayer> mca$vanillaModel;
    @Unique
    private AbstractClientPlayer mca$firstPersonPlayer;
    @Unique
    private MultiBufferSource mca$firstPersonBuffers;

    public MixinPlayerRenderer(EntityRendererProvider.Context ctx, PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Unique
    private static PlayerEntityExtendedModel<AbstractClientPlayer> mca$createVisibleModel(MeshDefinition data) {
        return new PlayerEntityExtendedModel<>(LayerDefinition.create(data, 64, 64).bakeRoot());
    }

    @Unique
    private static PlayerEntityExtendedModel<AbstractClientPlayer> mca$createVillagerAnimationModel(EntityRendererProvider.Context ctx) {
        return new PlayerEntityExtendedModel<>(ctx.bakeLayer(ModelLayers.PLAYER));
    }

    @Unique
    private void mca$selectModel(AbstractClientPlayer player) {
        if (!MCAClient.isPlayerRendererAllowed()) {
            return;
        }

        VillagerLike.PlayerModel selectedModel = MCAClient.getPlayerData(player.getUUID())
                .map(VillagerLike::getPlayerModel)
                .orElse(VillagerLike.PlayerModel.VANILLA);
        model = switch (selectedModel) {
            case VILLAGER -> mca$villagerAnimationModel;
            case PLAYER -> mca$playerModel;
            case VANILLA -> mca$vanillaModel;
        };
    }

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V", at = @At("TAIL"))
    private void mca$injectInit(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        if (MCAClient.isPlayerRendererAllowed()) {
            mca$vanillaModel = model;
            mca$villagerAnimationModel = mca$createVillagerAnimationModel(ctx);
            // PLAYER renders an EMF-interceptable vanilla root directly. MCA-only breast parts
            // stay detached so they can be rendered by PlayerEntityExtendedModel without mutating that root.
            ModelPart mcaPlayerParts = LayerDefinition.create(
                    VillagerEntityModelMCA.bodyData(CubeDeformation.NONE), 64, 64
            ).bakeRoot();
            mca$playerModel = new PlayerEntityExtendedModel<>(
                    ctx.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER),
                    slim,
                    mcaPlayerParts
            );

            // The villager parent is animation-only; its visible appearance stays in MCA layers.
            mca$skinLayer = new SkinLayer<>(this, mca$createVisibleModel(VillagerEntityModelMCA.bodyData(CubeDeformation.NONE)));
            layers.add(0, mca$skinLayer);
            addLayer(new FaceLayer<>(this, mca$createVisibleModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.01F))), "normal"));

            mca$clothingLayer = new ClothingLayer<>(this, mca$createVisibleModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.0625F))), "normal");
            addLayer(mca$clothingLayer);
            addLayer(new HairLayer<>(this, mca$createVisibleModel(VillagerEntityModelMCA.hairData(new CubeDeformation(0.125F)))));
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void mca$selectThirdPersonModel(AbstractClientPlayer player, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, CallbackInfo ci) {
        mca$selectModel(player);
    }

    @Inject(
            method = {
                    "renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V",
                    "renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V"
            },
            at = @At("HEAD")
    )
    private void mca$selectFirstPersonModel(PoseStack matrices, MultiBufferSource vertexConsumers, int light, AbstractClientPlayer player, CallbackInfo ci) {
        mca$selectModel(player);
    }

    @Inject(method = "scale(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("TAIL"), cancellable = true)
    private void mca$injectScale(AbstractClientPlayer player, PoseStack matrices, float f, CallbackInfo ci) {
        if (MCAClient.useGeneticsRenderer(player.getUUID())) {
            var villager = CommonVillagerModel.getVillager(player);
            matrices.scale(
                    villager.getRawHorizontalScaleFactor(),
                    villager.getRawVerticalScaleFactor(),
                    villager.getRawHorizontalScaleFactor()
            );
            if (villager.getAgeState() == AgeState.BABY && !player.isPassenger()) {
                matrices.translate(0, 0.6F, 0);
            }
            ci.cancel();
        }
    }

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void mca$beginRenderHand(PoseStack matrices, MultiBufferSource vertexConsumers, int light, AbstractClientPlayer player, ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        String armName = arm == model.rightArm ? "right_arm" : "left_arm";
        if (!MCAClient.renderArms(player.getUUID(), armName)) {
            mca$firstPersonPlayer = null;
            mca$firstPersonBuffers = null;
            return;
        }

        mca$firstPersonPlayer = player;
        mca$firstPersonBuffers = vertexConsumers;
    }

    @Redirect(
            method = "renderHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
                    ordinal = 0
            )
    )
    private void mca$redirectFirstPersonArmRender(ModelPart originalArm, PoseStack matrices, VertexConsumer originalBuffer, int light, int overlay) {
        if (mca$firstPersonPlayer == null || mca$firstPersonBuffers == null) {
            originalArm.render(matrices, originalBuffer, light, overlay);
            return;
        }

        boolean right = originalArm == model.rightArm;
        ModelPart animatedArm = right ? mca$villagerAnimationModel.rightArm : mca$villagerAnimationModel.leftArm;

        if (model != mca$villagerAnimationModel) {
            model.copyPropertiesTo(mca$villagerAnimationModel);
        }
        animatedArm.xRot = 0.0F;
        McaModelAnimationDriver.animate(animatedArm, matrices, light, overlay);

        mca$renderArmLayer(
                matrices, mca$firstPersonBuffers, light, mca$firstPersonPlayer, mca$skinLayer, right, animatedArm
        );
        mca$renderArmLayer(
                matrices, mca$firstPersonBuffers, light, mca$firstPersonPlayer, mca$clothingLayer, right, animatedArm
        );
    }

    @Redirect(
            method = "renderHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
                    ordinal = 1
            )
    )
    private void mca$redirectFirstPersonSleeveRender(ModelPart originalSleeve, PoseStack matrices, VertexConsumer originalBuffer, int light, int overlay) {
        if (mca$firstPersonPlayer == null || mca$firstPersonBuffers == null) {
            if (model == mca$playerModel) {
                ModelPart arm = originalSleeve == model.rightSleeve ? model.rightArm : model.leftArm;
                originalSleeve.copyFrom(arm);
            }
            originalSleeve.render(matrices, originalBuffer, light, overlay);
        }
        // Custom arm rendering above already rendered both MCA skin and clothing sleeves.
    }

    @Inject(method = "renderHand", at = @At("TAIL"))
    private void mca$endRenderHand(PoseStack matrices, MultiBufferSource vertexConsumers, int light, AbstractClientPlayer player, ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        mca$firstPersonPlayer = null;
        mca$firstPersonBuffers = null;
    }

    @Unique
    private void mca$renderArmLayer(
            PoseStack matrices,
            MultiBufferSource vertexConsumers,
            int light,
            AbstractClientPlayer player,
            VillagerLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> layer,
            boolean right,
            ModelPart sourceArm
    ) {
        ResourceLocation skin = layer.getSkin(player);
        if (skin == null || !layer.canUse(skin)) {
            return;
        }

        PlayerModel<AbstractClientPlayer> visibleModel = layer.model;
        ModelPart arm = right ? visibleModel.rightArm : visibleModel.leftArm;
        ModelPart sleeve = right ? visibleModel.rightSleeve : visibleModel.leftSleeve;
        arm.copyFrom(sourceArm);
        sleeve.copyFrom(arm);

        VertexConsumer buffer = vertexConsumers.getBuffer(RenderType.entityCutoutNoCull(skin));
        int color = layer.getColor(player, 0.0f);
        arm.render(matrices, buffer, light, OverlayTexture.NO_OVERLAY, color);
        sleeve.render(matrices, buffer, light, OverlayTexture.NO_OVERLAY, color);
    }
}
