package net.mca.mixin.client;

import net.mca.MCAClient;
import net.mca.client.model.CommonVillagerModel;
import net.mca.client.model.PlayerEntityExtendedModel;
import net.mca.client.model.VillagerEntityModelMCA;
import net.mca.client.render.layer.*;
import net.mca.entity.ai.relationship.AgeState;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class MixinPlayerEntityRenderer extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    @Unique
    private PlayerEntityModel<AbstractClientPlayerEntity> mca$villagerModel;
    @Unique
    private PlayerEntityModel<AbstractClientPlayerEntity> mca$vanillaModel;

    @Shadow
    protected abstract void setModelPose(AbstractClientPlayerEntity player);

    @Unique
    SkinLayer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> mca$skinLayer;
    @Unique
    ClothingLayer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> mca$clothingLayer;

    public MixinPlayerEntityRenderer(EntityRendererFactory.Context ctx, PlayerEntityModel<AbstractClientPlayerEntity> model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/render/entity/EntityRendererFactory$Context;Z)V", at = @At("TAIL"))
    private void init(EntityRendererFactory.Context ctx, boolean slim, CallbackInfo ci) {
        if (MCAClient.isPlayerRendererAllowed()) {
            mca$villagerModel = mca$createModel(VillagerEntityModelMCA.bodyData(new Dilation(0.0F), slim));
            mca$vanillaModel = model;

            mca$skinLayer = new SkinLayer<>(this, mca$createModel(VillagerEntityModelMCA.bodyData(new Dilation(0.0F))));
            addFeature(mca$skinLayer);
            addFeature(new FaceLayer<>(this, mca$createModel(VillagerEntityModelMCA.bodyData(new Dilation(0.01F))), "normal"));

            mca$clothingLayer = new ClothingLayer<>(this, mca$createModel(VillagerEntityModelMCA.bodyData(new Dilation(0.0625F))), "normal");
            addFeature(mca$clothingLayer);
            addFeature(new HairLayer<>(this, mca$createModel(VillagerEntityModelMCA.hairData(new Dilation(0.125F)))));
        }
    }

    @Unique
    private static PlayerEntityExtendedModel<AbstractClientPlayerEntity> mca$createModel(ModelData data) {
        return new PlayerEntityExtendedModel<>(TexturedModelData.of(data, 64, 64).createModel());
    }

    @Inject(method = "scale(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("TAIL"))
    private void injectScale(AbstractClientPlayerEntity player, MatrixStack matrices, float f, CallbackInfo ci) {
        if (MCAClient.useGeneticsRenderer(player.getUuid())) {
            float height = CommonVillagerModel.getVillager(player).getRawScaleFactor();
            float width = CommonVillagerModel.getVillager(player).getHorizontalScaleFactor();
            matrices.scale(width, height, width);
            if (CommonVillagerModel.getVillager(player).getAgeState() == AgeState.BABY && !player.hasVehicle()) {
                matrices.translate(0, 0.6F, 0);
            }

            // switch to mca model
            model = mca$villagerModel;
        } else if (MCAClient.isPlayerRendererAllowed()) {
            // switch to vanilla model
            model = mca$vanillaModel;
        }
    }

    @Inject(method = "renderRightArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/network/AbstractClientPlayerEntity;)V", at = @At("HEAD"), cancellable = true)
    public void injectRenderRightArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player, CallbackInfo ci) {
        if (MCAClient.renderArms(player.getUuid(), "right_arm")) {
            mca$renderCustomArm(matrices, vertexConsumers, light, player, mca$skinLayer.model.rightArm, mca$skinLayer.model.rightSleeve, mca$skinLayer);
            mca$renderCustomArm(matrices, vertexConsumers, light, player, mca$clothingLayer.model.rightArm, mca$clothingLayer.model.rightSleeve, mca$clothingLayer);
            ci.cancel();
        }
    }

    @Inject(method = "renderLeftArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/network/AbstractClientPlayerEntity;)V", at = @At("HEAD"), cancellable = true)
    public void injectRenderLeftArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player, CallbackInfo ci) {
        if (MCAClient.renderArms(player.getUuid(), "left_arm")) {
            mca$renderCustomArm(matrices, vertexConsumers, light, player, mca$skinLayer.model.leftArm, mca$skinLayer.model.leftSleeve, mca$skinLayer);
            mca$renderCustomArm(matrices, vertexConsumers, light, player, mca$clothingLayer.model.leftArm, mca$clothingLayer.model.leftSleeve, mca$clothingLayer);
            ci.cancel();
        }
    }

    @Unique
    private void mca$renderCustomArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player, ModelPart arm, ModelPart sleeve, VillagerLayer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> layer) {
        PlayerEntityExtendedModel<AbstractClientPlayerEntity> model = (PlayerEntityExtendedModel<AbstractClientPlayerEntity>)layer.model;
        setModelPose(player);

        model.handSwingProgress = 0.0f;
        model.sneaking = false;
        model.leaningPitch = 0.0f;
        model.setAngles(player, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);

        model.applyVillagerDimensions(CommonVillagerModel.getVillager(player), player.isInSneakingPose());

        Identifier skin = layer.getSkin(player);
        if (layer.canUse(skin)) {
            VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(skin));

            float[] color = layer.getColor(player, 0.0f);

            arm.pitch = 0.0F;
            arm.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, color[0], color[1], color[2], 1.0f);
            sleeve.pitch = 0.0F;
            sleeve.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, color[0], color[1], color[2], 1.0f);
        }
    }
}
