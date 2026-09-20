package net.conczin.mca.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.MCAModelLayers;
import net.conczin.mca.client.model.VillagerOverlayModel;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.PlayerMorphologyLayer;
import net.conczin.mca.client.render.layer.VillagerLayer;
import net.conczin.mca.client.resources.SkinExporter;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class MixinPlayerRenderer extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    @Unique
    private ClothingLayer<AbstractClientPlayer> mca$clothingLayer;

    public MixinPlayerRenderer(EntityRendererProvider.Context ctx, PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Unique
    private static VillagerOverlayModel<AbstractClientPlayer> mca$createVisibleModel(
            EntityRendererProvider.Context ctx,
            ModelLayerLocation layer,
            boolean slim
    ) {
        return new VillagerOverlayModel<>(ctx.bakeLayer(layer), slim);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V", at = @At("TAIL"))
    private void mca$injectInit(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        if (!MCAClient.isPlayerRendererAllowed()) {
            return;
        }

        addLayer(new FaceLayer<>(
                this,
                mca$createVisibleModel(
                        ctx,
                        slim ? MCAModelLayers.VILLAGER_FACE_SLIM : MCAModelLayers.VILLAGER_FACE,
                        slim
                ).hideWears(),
                "normal"
        ));
        mca$clothingLayer = new ClothingLayer<>(
                this,
                mca$createVisibleModel(
                        ctx,
                        slim ? MCAModelLayers.VILLAGER_CLOTHING_SLIM : MCAModelLayers.VILLAGER_CLOTHING,
                        slim
                ),
                "normal"
        );
        addLayer(mca$clothingLayer);
        addLayer(new HairLayer<>(
                this,
                mca$createVisibleModel(
                        ctx,
                        slim ? MCAModelLayers.VILLAGER_HAIR_SLIM : MCAModelLayers.VILLAGER_HAIR,
                        slim
                )
        ));
        // Player morphology replaces geometry that used to render with the base model,
        // so keep it ahead of armor and the other player render layers.
        layers.add(0, new PlayerMorphologyLayer(this, ctx.bakeLayer(MCAModelLayers.PLAYER_ATTACHMENTS)));
    }

    @WrapOperation(
            method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
            )
    )
    private void mca$preservePlayerModelState(
            PlayerRenderer renderer,
            LivingEntity entity,
            float yaw,
            float tickDelta,
            PoseStack matrices,
            MultiBufferSource buffers,
            int light,
            Operation<Void> original
    ) {
        AbstractClientPlayer player = (AbstractClientPlayer) entity;
        if (!MCAClient.useGeneticsRenderer(player.getUUID())) {
            original.call(renderer, entity, yaw, tickDelta, matrices, buffers, light);
            return;
        }

        PlayerModel<AbstractClientPlayer> playerModel = renderer.getModel();
        ModelPart head = playerModel.head;
        ModelPart hat = playerModel.hat;
        float headX = head.xScale;
        float headY = head.yScale;
        float headZ = head.zScale;
        float hatX = hat.xScale;
        float hatY = hat.yScale;
        float hatZ = hat.zScale;
        try {
            original.call(renderer, entity, yaw, tickDelta, matrices, buffers, light);
        } finally {
            head.xScale = headX;
            head.yScale = headY;
            head.zScale = headZ;
            hat.xScale = hatX;
            hat.yScale = hatY;
            hat.zScale = hatZ;
        }
    }

    @ModifyReturnValue(
            method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("RETURN")
    )
    private ResourceLocation mca$useVillagerSkin(ResourceLocation original, AbstractClientPlayer player) {
        return MCAClient.useVillagerRenderer(player.getUUID())
                ? SkinExporter.getSkin(MCAClient.resolveVillager(player))
                : original;
    }

    @Inject(method = "scale(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("TAIL"))
    private void mca$injectScale(AbstractClientPlayer player, PoseStack matrices, float tickDelta, CallbackInfo ci) {
        if (MCAClient.useGeneticsRenderer(player.getUUID())) {
            var villager = MCAClient.resolveVillager(player);
            float width = villager.getRawHorizontalScaleFactor();
            matrices.scale(width, villager.getRawVerticalScaleFactor(), width);
            if (villager.getAgeState() == AgeState.BABY && !player.isPassenger()) {
                matrices.translate(0, 0.6F, 0);
            }
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
        boolean right = arm == model.rightArm;
        if (!MCAClient.renderArms(player.getUUID(), right ? "right_arm" : "left_arm")) {
            return;
        }

        ModelPart skinArm = arm;
        skinArm.visible = true;
        var villager = MCAClient.resolveVillager(player);
        ResourceLocation skin = SkinExporter.getSkin(villager);
        if (VillagerLayer.canUse(skin)) {
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

}
