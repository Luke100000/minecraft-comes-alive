package net.conczin.mca.mixin.client;

import net.conczin.mca.client.render.VillagerStateHolder;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {
    @Inject(method = "getRenderType(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;", at = @At("HEAD"), cancellable = true)
    public void mca$injectGetRenderLayer(LivingEntityRenderState state, boolean showBody, boolean translucent, boolean showOutline, CallbackInfoReturnable<@Nullable RenderType> cir) {
        if (state instanceof VillagerStateHolder holder && holder.mca$isVillagerRendererActive()) {
            //disable original model when villager renderer is active
            cir.setReturnValue(null);
        }
    }
}
