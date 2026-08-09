package net.mca.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.mca.MCAClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer<T extends LivingEntity, M extends EntityModel<T>> {
    @ModifyReturnValue(
            method = "getRenderLayer(Lnet/minecraft/entity/LivingEntity;ZZZ)Lnet/minecraft/client/render/RenderLayer;",
            at = @At("RETURN")
    )
    public @Nullable RenderLayer mca$hideVanillaPlayerModel(@Nullable RenderLayer original, T entity, boolean showBody, boolean translucent, boolean showOutline) {
        return entity instanceof PlayerEntity && MCAClient.useVillagerRenderer(entity.getUuid()) ? null : original;
    }
}
