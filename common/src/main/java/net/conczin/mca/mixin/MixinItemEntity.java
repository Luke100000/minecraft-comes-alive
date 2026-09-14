package net.conczin.mca.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ItemEntity.class)
public abstract class MixinItemEntity {
    @Shadow
    private int pickupDelay;

    @Shadow
    @Nullable
    private UUID thrower;

    @Shadow
    @Nullable
    private UUID target;

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void mca$releaseReloadedFishingReel(CompoundTag nbt, CallbackInfo ci) {
        if (pickupDelay == ItemEntity.INFINITE_PICKUP_DELAY && thrower != null && thrower.equals(target)) {
            ItemEntity item = (ItemEntity) (Object) this;
            item.setTarget(null);
            item.setNoPickUpDelay();
        }
    }
}
