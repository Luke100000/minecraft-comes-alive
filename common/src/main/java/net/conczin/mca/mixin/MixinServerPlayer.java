package net.conczin.mca.mixin;

import net.conczin.mca.item.BabyItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
abstract class MixinServerPlayer {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"),
            cancellable = true)
    private void onDropItem(ItemStack stack, boolean randomly, boolean thrownFromHand, CallbackInfoReturnable<ItemEntity> info) {
        //noinspection ConstantValue
        if (stack.getItem() instanceof BabyItem baby && !baby.onDropped(stack, (ServerPlayer) (Object) this)) {
            info.setReturnValue(null);
        }
    }
}
