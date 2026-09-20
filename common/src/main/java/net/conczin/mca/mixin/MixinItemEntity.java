package net.conczin.mca.mixin;

import net.conczin.mca.entity.ProtectedFishingReelItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.storage.ValueInput;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ItemEntity.class)
public abstract class MixinItemEntity implements ProtectedFishingReelItem {
    @Shadow
    private int pickupDelay;

    @Shadow
    @Nullable
    private EntityReference<Entity> thrower;

    @Shadow
    @Nullable
    private UUID target;

    @Override
    public boolean mca$isProtectedFishingReel() {
        return pickupDelay == ItemEntity.INFINITE_PICKUP_DELAY
                && thrower != null
                && thrower.getUUID().equals(target);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void mca$releaseReloadedFishingReel(ValueInput input, CallbackInfo ci) {
        if (mca$isProtectedFishingReel()) {
            ItemEntity item = (ItemEntity) (Object) this;
            item.setTarget(null);
            item.setNoPickUpDelay();
        }
    }
}
