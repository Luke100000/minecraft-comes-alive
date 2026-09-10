package net.conczin.mca.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.resources.SkinExporter;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {
    @WrapOperation(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"
            )
    )
    private void mca$applyPlayerRenderStateAfterAnimation(
            EntityModel<?> model,
            Entity entity,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float headYaw,
            float headPitch,
            Operation<Void> original,
            LivingEntity renderedEntity
    ) {
        if (!(renderedEntity instanceof AbstractClientPlayer player)
                || !(model instanceof PlayerModel<?> playerModel)) {
            original.call(model, entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
            return;
        }

        VillagerLike<?> villager = MCAClient.getGeneticsPlayerData(player.getUUID())
                .orElse(null);
        if (villager == null) {
            original.call(model, entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
            return;
        }

        if (villager.getAgeState() == AgeState.BABY && !player.isPassenger()) {
            limbDistance = (float) Math.sin(player.tickCount / 12.0F);
            limbAngle = (float) Math.cos(player.tickCount / 9.0F) * 3.0F;
            headYaw += (float) Math.sin(player.tickCount / 2.0F);
        }

        original.call(model, entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);

        float headScale = villager.getVillagerDimensions().getHead();
        playerModel.head.xScale *= headScale;
        playerModel.head.yScale *= headScale;
        playerModel.head.zScale *= headScale;
        playerModel.hat.xScale *= headScale;
        playerModel.hat.yScale *= headScale;
        playerModel.hat.zScale *= headScale;

        if (villager.getPlayerModel() == VillagerLike.PlayerModel.VILLAGER) {
            playerModel.jacket.visible = false;
            playerModel.leftSleeve.visible = false;
            playerModel.rightSleeve.visible = false;
            playerModel.leftPants.visible = false;
            playerModel.rightPants.visible = false;
        }
    }

    @WrapOperation(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"
            )
    )
    private void mca$tintVillagerPlayerSkin(
            EntityModel<?> model,
            PoseStack matrices,
            VertexConsumer vertices,
            int light,
            int overlay,
            int color,
            Operation<Void> original,
            LivingEntity entity,
            float yaw,
            float tickDelta,
            PoseStack renderMatrices,
            MultiBufferSource buffers,
            int renderLight
    ) {
        if (entity instanceof AbstractClientPlayer player && MCAClient.useVillagerRenderer(player.getUUID())) {
            color = FastColor.ARGB32.multiply(
                    color,
                    SkinExporter.getSkinColor(CommonVillagerModel.getVillager(player))
            );
        }
        original.call(model, matrices, vertices, light, overlay, color);
    }
}
