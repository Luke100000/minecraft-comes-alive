package net.conczin.mca.mixin;

import net.conczin.mca.item.BabyItem;
import net.minecraft.world.Container;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
abstract class MixinPlayerInventory implements Container, Nameable {
    @Shadow @Final public Player player;

    @Inject(method = "removeFromSelected(Z)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"),
            cancellable = true)
    public void onDropSelectedItem(boolean dropEntireStack, CallbackInfoReturnable<ItemStack> info) {
        ItemStack stack = ((Inventory)(Object)this).getSelected();
        if (BabyItem.shouldCancelDrop(stack, this.player)) {
            info.setReturnValue(ItemStack.EMPTY);
        }
    }
}
