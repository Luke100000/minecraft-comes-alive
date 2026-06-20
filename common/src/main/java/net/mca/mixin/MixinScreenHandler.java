package net.mca.mixin;

import net.mca.item.BabyItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
abstract class MixinScreenHandler {
    @Shadow @Final public DefaultedList<Slot> slots;

    @Shadow public abstract ItemStack getCursorStack();

    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo info) {
        ItemStack stack = mca$getDroppedStack(slotIndex, actionType, player);
        if (BabyItem.shouldCancelDrop(stack, player)) {
            info.cancel();
        }
    }

    @Unique
    private ItemStack mca$getDroppedStack(int slotIndex, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex == ScreenHandler.EMPTY_SPACE_SLOT_INDEX && actionType == SlotActionType.PICKUP) {
            return getCursorStack();
        }
        if (slotIndex >= 0 && slotIndex < slots.size() && actionType == SlotActionType.THROW) {
            Slot slot = slots.get(slotIndex);
            if (slot.canTakeItems(player)) {
                return slot.getStack();
            }
        }
        return ItemStack.EMPTY;
    }
}
