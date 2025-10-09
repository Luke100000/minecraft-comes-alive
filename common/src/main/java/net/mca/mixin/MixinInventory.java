package net.mca.mixin;

import net.mca.item.BabyItem;
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
abstract class MixinInventory implements Container, Nameable {
    @Shadow
    @Final
    public Player player;

    @Inject(method = "removeFromSelected", at = @At("HEAD"), cancellable = true)
    public void mca$injectRemoveFromSelected(boolean dropEntireStack, CallbackInfoReturnable<ItemStack> info) {
        ItemStack stack = ((Inventory) (Object) this).getSelected();
        if (stack.getItem() instanceof BabyItem baby && !baby.onDropped(stack, this.player)) {
            info.setReturnValue(ItemStack.EMPTY);
        }
    }
}
