package net.mca.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.mca.MCAClient;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer<T extends LivingEntity, M extends EntityModel<T>> {
    @ModifyReturnValue(
            method = "getRenderType(Lnet/minecraft/world/entity/LivingEntity;ZZZ)Lnet/minecraft/client/renderer/RenderType;",
            at = @At("RETURN")
    )
    public @Nullable RenderType mca$hideVanillaPlayerModel(@Nullable RenderType original, T entity, boolean showBody, boolean translucent, boolean showOutline) {
        return entity instanceof Player && MCAClient.useVillagerRenderer(entity.getUUID()) ? null : original;
    }
}
