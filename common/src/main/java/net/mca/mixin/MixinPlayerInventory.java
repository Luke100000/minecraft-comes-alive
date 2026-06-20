package net.mca.mixin;

import net.mca.item.BabyItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Nameable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInventory.class)
abstract class MixinPlayerInventory implements Inventory, Nameable {
    @Shadow @Final public PlayerEntity player;

    @Inject(method = "dropSelectedItem(Z)Lnet/minecraft/item/ItemStack;",
            at = @At("HEAD"),
            cancellable = true)
    public void onDropSelectedItem(boolean dropEntireStack, CallbackInfoReturnable<ItemStack> info) {
        ItemStack stack = ((PlayerInventory)(Object)this).getMainHandStack();
        if (BabyItem.shouldCancelDrop(stack, this.player)) {
            info.setReturnValue(ItemStack.EMPTY);
        }
    }
}
