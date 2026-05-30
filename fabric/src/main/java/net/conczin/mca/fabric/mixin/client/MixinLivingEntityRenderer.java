package net.conczin.mca.fabric.mixin.client;

import net.conczin.mca.client.render.VillagerRenderStateHooks;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer<T extends LivingEntity> {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("HEAD"))
    private void mca$injectExtractRenderState(T entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
        VillagerRenderStateHooks.extract(entity, state);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"))
    private void mca$injectScaledBounds(T entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
        VillagerRenderStateHooks.extractScaledBounds(entity, state);
    }
}
