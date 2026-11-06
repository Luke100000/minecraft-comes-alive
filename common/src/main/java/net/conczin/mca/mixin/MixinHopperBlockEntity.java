package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.conczin.mca.entity.ProtectedFishingReelItem;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HopperBlockEntity.class)
public abstract class MixinHopperBlockEntity {
    @WrapOperation(
            method = "suckInItems(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/entity/Hopper;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z"
            )
    )
    private static boolean mca$protectFishingReel(
            Container container,
            ItemEntity item,
            Operation<Boolean> original
    ) {
        if (item instanceof ProtectedFishingReelItem protectedItem && protectedItem.mca$isProtectedFishingReel()) {
            return false;
        }

        return original.call(container, item);
    }
}
