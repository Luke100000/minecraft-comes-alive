package net.conczin.mca.mixin;

import net.conczin.mca.item.BabyItem;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
abstract class MixinScreenHandler {
    @Shadow
    @Final
    public NonNullList<Slot> slots;

    @Shadow
    public abstract ItemStack getCarried();

    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void mca$onSlotClick(int slotId, int button, ContainerInput clickType, Player player, CallbackInfo info) {
        ItemStack stack = mca$getDroppedStack(slotId, clickType, player);
        if (stack.getItem() instanceof BabyItem baby && !baby.onDropped(stack, player)) {
            info.cancel();
        }
    }

    @Unique
    private ItemStack mca$getDroppedStack(int slotId, ContainerInput clickType, Player player) {
        if (slotId == AbstractContainerMenu.SLOT_CLICKED_OUTSIDE && clickType == ContainerInput.PICKUP) {
            return getCarried();
        }
        if (slotId >= 0 && slotId < slots.size() && clickType == ContainerInput.THROW) {
            Slot slot = slots.get(slotId);
            if (slot.mayPickup(player)) {
                return slot.getItem();
            }
        }
        return ItemStack.EMPTY;
    }
}
