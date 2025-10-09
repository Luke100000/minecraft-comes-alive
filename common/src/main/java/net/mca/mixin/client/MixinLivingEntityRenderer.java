package net.mca.mixin.client;

import net.mca.MCAClient;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer<T extends LivingEntity> {
    @Inject(method = "getRenderType(Lnet/minecraft/world/entity/LivingEntity;ZZZ)Lnet/minecraft/client/renderer/RenderType;", at = @At("HEAD"), cancellable = true)
    public void mca$injectGetRenderLayer(T entity, boolean showBody, boolean translucent, boolean showOutline, CallbackInfoReturnable<@Nullable RenderType> cir) {
        if (entity instanceof Player && MCAClient.useVillagerRenderer(entity.getUUID())) {
            //disable original model when villager renderer is active
            cir.setReturnValue(null);
        }
    }
}
