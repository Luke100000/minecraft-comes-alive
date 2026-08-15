package net.conczin.mca.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.model.PlayerEntityExtendedModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.layer.*;
import net.mca.client.render.layer.*;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.client.model.PlayerModel;
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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class MixinPlayerEntityRenderer extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    @Unique
    private PlayerModel<AbstractClientPlayer> mca$villagerModel;
    @Unique
    private PlayerModel<AbstractClientPlayer> mca$vanillaModel;

    @Shadow
    protected abstract void setModelProperties(AbstractClientPlayer player);

    @Unique
    SkinLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> mca$skinLayer;
    @Unique
    ClothingLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> mca$clothingLayer;

    public MixinPlayerEntityRenderer(EntityRendererProvider.Context ctx, PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V", at = @At("TAIL"))
    private void init(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        if (MCAClient.isPlayerRendererAllowed()) {
            mca$villagerModel = mca$createModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.0F), slim));
            mca$vanillaModel = model;

            mca$skinLayer = new SkinLayer<>(this, mca$createModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.0F))));
            addLayer(mca$skinLayer);
            addLayer(new FaceLayer<>(this, mca$createModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.01F))), "normal"));

            mca$clothingLayer = new ClothingLayer<>(this, mca$createModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.0625F))), "normal");
            addLayer(mca$clothingLayer);
            addLayer(new HairLayer<>(this, mca$createModel(VillagerEntityModelMCA.hairData(new CubeDeformation(0.125F)))));
        }
    }

    @Unique
    private static PlayerEntityExtendedModel<AbstractClientPlayer> mca$createModel(MeshDefinition data) {
        return new PlayerEntityExtendedModel<>(LayerDefinition.create(data, 64, 64).bakeRoot());
    }

    @Inject(method = "scale(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("TAIL"))
    private void injectScale(AbstractClientPlayer player, PoseStack matrices, float f, CallbackInfo ci) {
        if (MCAClient.useGeneticsRenderer(player.getUUID())) {
            float height = CommonVillagerModel.getVillager(player).getRawScaleFactor();
            float width = CommonVillagerModel.getVillager(player).getHorizontalScaleFactor();
            matrices.scale(width, height, width);
            if (CommonVillagerModel.getVillager(player).getAgeState() == AgeState.BABY && !player.isPassenger()) {
                matrices.translate(0, 0.6F, 0);
            }

            // switch to mca model
            model = mca$villagerModel;
        } else if (MCAClient.isPlayerRendererAllowed()) {
            // switch to vanilla model
            model = mca$vanillaModel;
        }
    }

    @Inject(method = "renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V", at = @At("HEAD"), cancellable = true)
    public void injectRenderRightArm(PoseStack matrices, MultiBufferSource vertexConsumers, int light, AbstractClientPlayer player, CallbackInfo ci) {
        if (MCAClient.renderArms(player.getUUID(), "right_arm")) {
            mca$renderCustomArm(matrices, vertexConsumers, light, player, mca$skinLayer.model.rightArm, mca$skinLayer.model.rightSleeve, mca$skinLayer);
            mca$renderCustomArm(matrices, vertexConsumers, light, player, mca$clothingLayer.model.rightArm, mca$clothingLayer.model.rightSleeve, mca$clothingLayer);
            ci.cancel();
        }
    }

    @Inject(method = "renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V", at = @At("HEAD"), cancellable = true)
    public void injectRenderLeftArm(PoseStack matrices, MultiBufferSource vertexConsumers, int light, AbstractClientPlayer player, CallbackInfo ci) {
        if (MCAClient.renderArms(player.getUUID(), "left_arm")) {
            mca$renderCustomArm(matrices, vertexConsumers, light, player, mca$skinLayer.model.leftArm, mca$skinLayer.model.leftSleeve, mca$skinLayer);
            mca$renderCustomArm(matrices, vertexConsumers, light, player, mca$clothingLayer.model.leftArm, mca$clothingLayer.model.leftSleeve, mca$clothingLayer);
            ci.cancel();
        }
    }

    @Unique
    private void mca$renderCustomArm(PoseStack matrices, MultiBufferSource vertexConsumers, int light, AbstractClientPlayer player, ModelPart arm, ModelPart sleeve, VillagerLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> layer) {
        PlayerEntityExtendedModel<AbstractClientPlayer> model = (PlayerEntityExtendedModel<AbstractClientPlayer>)layer.model;
        setModelProperties(player);

        model.attackTime = 0.0f;
        model.crouching = false;
        model.swimAmount = 0.0f;
        model.setupAnim(player, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);

        model.applyVillagerDimensions(CommonVillagerModel.getVillager(player), player.isCrouching());

        ResourceLocation skin = layer.getSkin(player);
        if (layer.canUse(skin)) {
            VertexConsumer buffer = vertexConsumers.getBuffer(RenderType.entityCutoutNoCull(skin));

            float[] color = layer.getColor(player, 0.0f);

            arm.xRot = 0.0F;
            arm.render(matrices, buffer, light, OverlayTexture.NO_OVERLAY, color[0], color[1], color[2], 1.0f);
            sleeve.xRot = 0.0F;
            sleeve.render(matrices, buffer, light, OverlayTexture.NO_OVERLAY, color[0], color[1], color[2], 1.0f);
        }
    }
}
