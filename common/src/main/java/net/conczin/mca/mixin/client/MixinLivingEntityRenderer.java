package net.conczin.mca.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.render.LivingEntityRenderContext;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.conczin.mca.client.render.VillagerVisualSnapshot;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer<T extends LivingEntity> {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("HEAD"))
    private void mca$injectExtractRenderState(T entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
        LivingEntityRenderContext.clear();
        boolean geneticsRenderer = entity instanceof Player && MCAClient.useGeneticsRenderer(entity.getUUID());
        if (state instanceof VillagerStateHolder holder) {
            VillagerLike<?> villager = null;
            if (entity instanceof VillagerLike<?> villagerLike) {
                villager = villagerLike;
            } else if (geneticsRenderer) {
                villager = CommonVillagerModel.getVillager(entity);
            }

            holder.mca$setVillager(villager);
            holder.mca$setVisualSnapshot(villager != null ? VillagerVisualSnapshot.capture(villager) : null);
        }
        LivingEntityRenderContext.setGeneticsRendererActive(geneticsRenderer);
        LivingEntityRenderContext.setVillagerRendererActive(geneticsRenderer && MCAClient.useVillagerRenderer(entity.getUUID()));
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"))
    private void mca$injectScaledBounds(T entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
        if (!(state instanceof VillagerStateHolder holder)) {
            return;
        }

        var visuals = CommonVillagerModel.peekVisuals(holder);
        if (visuals == null) {
            return;
        }

        if (!(entity instanceof Player) && !(entity instanceof VillagerLike<?>)) {
            return;
        }

        float horizontalBaseScale = entity instanceof VillagerLike<?> villagerEntity ? villagerEntity.getHorizontalScaleFactor() : 1.0F;
        float verticalBaseScale = entity instanceof VillagerLike<?> villagerEntity ? villagerEntity.getVerticalScaleFactor() : 1.0F;
        float horizontalRatio = visuals.rawHorizontalScaleFactor() / Math.max(horizontalBaseScale, 1.0E-4F);
        float verticalRatio = visuals.rawVerticalScaleFactor() / Math.max(verticalBaseScale, 1.0E-4F);

        state.boundingBoxWidth = entity.getBbWidth() * horizontalRatio;
        state.boundingBoxHeight = entity.getBbHeight() * verticalRatio;
        state.eyeHeight = entity.getEyeHeight(state.pose) * verticalRatio;
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("RETURN"))
    private void mca$clearRenderState(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, CallbackInfo ci) {
        LivingEntityRenderContext.clear();
    }

    @Inject(method = "getRenderType(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;", at = @At("HEAD"), cancellable = true)
    public void mca$injectGetRenderLayer(LivingEntityRenderState state, boolean showBody, boolean translucent, boolean showOutline, CallbackInfoReturnable<@Nullable RenderType> cir) {
        if (LivingEntityRenderContext.isVillagerRendererActive()) {
            //disable original model when villager renderer is active
            cir.setReturnValue(null);
        }
    }
}
