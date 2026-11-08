package net.conczin.mca.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemInHandLayer.class)
abstract class MixinItemInHandLayer {
    @ModifyExpressionValue(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/model/EntityModel;young:Z")
    )
    private boolean mca$skipYoungItemTransform(boolean young, PoseStack matrices, MultiBufferSource buffers,
                                               int light, LivingEntity entity) {
        return young && !(entity instanceof VillagerLike<?>);
    }
}
