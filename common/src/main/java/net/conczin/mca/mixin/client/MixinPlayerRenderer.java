package net.conczin.mca.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.model.MCAModelLayers;
import net.conczin.mca.client.model.PlayerAnimationBridge;
import net.conczin.mca.client.model.PlayerEntityExtendedModel;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.resources.SkinExporter;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerRenderer.class)
public abstract class MixinPlayerRenderer extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    @Unique
    private PlayerEntityExtendedModel<AbstractClientPlayer> mca$villagerModel;
    @Unique
    private PlayerEntityExtendedModel<AbstractClientPlayer> mca$playerModel;
    @Unique
    private PlayerModel<AbstractClientPlayer> mca$vanillaModel;
    @Unique
    private ClothingLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> mca$clothingLayer;

    public MixinPlayerRenderer(EntityRendererProvider.Context ctx, PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Unique
    private static PlayerEntityExtendedModel<AbstractClientPlayer> mca$createVisibleModel(
            EntityRendererProvider.Context ctx,
            ModelLayerLocation layer
    ) {
        return new PlayerEntityExtendedModel<>(ctx.bakeLayer(layer));
    }

    @Unique
    private void mca$selectModel(AbstractClientPlayer player) {
        if (!MCAClient.isPlayerRendererAllowed()) {
            if (mca$vanillaModel != null) {
                model = mca$vanillaModel;
            }
            return;
        }

        VillagerLike.PlayerModel selectedModel = MCAClient.getPlayerData(player.getUUID())
                .map(VillagerLike::getPlayerModel)
                .orElse(VillagerLike.PlayerModel.VANILLA);
        model = switch (selectedModel) {
            case VILLAGER -> mca$villagerModel;
            case PLAYER -> mca$playerModel;
            case VANILLA -> mca$vanillaModel;
        };
    }

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V", at = @At("TAIL"))
    private void mca$injectInit(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        if (!MCAClient.isPlayerRendererAllowed()) {
            return;
        }

        mca$vanillaModel = model;
        ModelLayerLocation playerLayer = slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER;

        mca$villagerModel = new PlayerEntityExtendedModel<>(
                ctx.bakeLayer(MCAModelLayers.VILLAGER),
                false,
                new PlayerAnimationBridge<>(new PlayerModel<>(ctx.bakeLayer(playerLayer), slim))
        );

        // Player mode renders the actual EMF-interceptable player root and adds only
        // MCA morphology from a separately registered attachment layer.
        mca$playerModel = new PlayerEntityExtendedModel<>(
                ctx.bakeLayer(playerLayer),
                slim,
                ctx.bakeLayer(MCAModelLayers.PLAYER_ATTACHMENTS)
        );

        addLayer(new FaceLayer<>(
                this,
                mca$createVisibleModel(ctx, MCAModelLayers.VILLAGER_FACE).hideWears(),
                "normal"
        ));
        mca$clothingLayer = new ClothingLayer<>(
                this,
                mca$createVisibleModel(ctx, MCAModelLayers.VILLAGER_CLOTHING),
                "normal"
        );
        addLayer(mca$clothingLayer);
        addLayer(new HairLayer<>(this, mca$createVisibleModel(ctx, MCAModelLayers.VILLAGER_HAIR)));
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void mca$selectThirdPersonModel(AbstractClientPlayer player, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, CallbackInfo ci) {
        mca$selectModel(player);
    }

    @Inject(
            method = "renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V",
            at = @At("HEAD")
    )
    private void mca$selectRightHandModel(PoseStack matrices, MultiBufferSource buffers, int light, AbstractClientPlayer player, CallbackInfo ci) {
        mca$selectModel(player);
        if (model == mca$villagerModel && !MCAClient.renderArms(player.getUUID(), "right_arm")) {
            model = mca$vanillaModel;
        }
    }

    @Inject(
            method = "renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V",
            at = @At("HEAD")
    )
    private void mca$selectLeftHandModel(PoseStack matrices, MultiBufferSource buffers, int light, AbstractClientPlayer player, CallbackInfo ci) {
        mca$selectModel(player);
        if (model == mca$villagerModel && !MCAClient.renderArms(player.getUUID(), "left_arm")) {
            model = mca$vanillaModel;
        }
    }

    @Inject(
            method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mca$useVillagerSkin(AbstractClientPlayer player, CallbackInfoReturnable<ResourceLocation> cir) {
        if (MCAClient.useVillagerRenderer(player.getUUID())) {
            cir.setReturnValue(SkinExporter.getSkin(CommonVillagerModel.getVillager(player)));
        }
    }

    @Inject(method = "scale(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("TAIL"), cancellable = true)
    private void mca$injectScale(AbstractClientPlayer player, PoseStack matrices, float tickDelta, CallbackInfo ci) {
        if (MCAClient.useGeneticsRenderer(player.getUUID())) {
            var villager = CommonVillagerModel.getVillager(player);
            float width = villager.getRawHorizontalScaleFactor();
            matrices.scale(width, villager.getRawVerticalScaleFactor(), width);
            if (villager.getAgeState() == AgeState.BABY && !player.isPassenger()) {
                matrices.translate(0, 0.6F, 0);
            }
            ci.cancel();
        }
    }

    @Inject(
            method = "renderHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
                    ordinal = 0
            ),
            cancellable = true
    )
    private void mca$renderVillagerHand(
            PoseStack matrices,
            MultiBufferSource buffers,
            int light,
            AbstractClientPlayer player,
            ModelPart arm,
            ModelPart sleeve,
            CallbackInfo ci
    ) {
        if (model != mca$villagerModel) {
            return;
        }

        boolean right = arm == mca$villagerModel.rightArm;
        mca$villagerModel.applyAnimationBridgeForArm(matrices, light, OverlayTexture.NO_OVERLAY, right);
        ModelPart skinArm = right ? mca$villagerModel.rightArm : mca$villagerModel.leftArm;
        skinArm.visible = true;
        var villager = CommonVillagerModel.getVillager(player);
        ResourceLocation skin = SkinExporter.getSkin(villager);
        if (mca$clothingLayer.canUse(skin)) {
            mca$renderArmPart(matrices, buffers, light, skin, SkinExporter.getSkinColor(villager), skinArm);
        }

        ResourceLocation clothing = mca$clothingLayer.getSkin(player);
        if (mca$clothingLayer.canUse(clothing)) {
            PlayerModel<AbstractClientPlayer> clothingModel = mca$clothingLayer.model;
            ModelPart clothingArm = right ? clothingModel.rightArm : clothingModel.leftArm;
            ModelPart clothingSleeve = right ? clothingModel.rightSleeve : clothingModel.leftSleeve;
            clothingArm.copyFrom(skinArm);
            clothingSleeve.copyFrom(clothingArm);
            clothingArm.visible = true;
            clothingSleeve.visible = true;
            mca$renderArmPart(matrices, buffers, light, clothing, 0xFFFFFFFF, clothingArm);
            mca$renderArmPart(matrices, buffers, light, clothing, 0xFFFFFFFF, clothingSleeve);
        }
        ci.cancel();
    }

    @Unique
    private static void mca$renderArmPart(
            PoseStack matrices,
            MultiBufferSource buffers,
            int light,
            ResourceLocation texture,
            int color,
            ModelPart part
    ) {
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        part.render(matrices, buffer, light, OverlayTexture.NO_OVERLAY, color);
    }

    @Inject(
            method = "renderHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
                    ordinal = 1
            )
    )
    private void mca$syncPlayerSleeve(
            PoseStack matrices,
            MultiBufferSource buffers,
            int light,
            AbstractClientPlayer player,
            ModelPart arm,
            ModelPart sleeve,
            CallbackInfo ci
    ) {
        if (model == mca$playerModel) {
            sleeve.copyFrom(arm);
        }
    }
}
