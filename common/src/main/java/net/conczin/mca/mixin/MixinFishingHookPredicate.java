package net.conczin.mca.mixin;

import net.conczin.mca.entity.MCAFishingBobberEntity;
import net.minecraft.advancements.critereon.FishingHookPredicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingHookPredicate.class)
public abstract class MixinFishingHookPredicate {
    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private void mca$matchVillagerFishingBobber(
            Entity entity,
            ServerLevel level,
            @Nullable Vec3 position,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (entity instanceof MCAFishingBobberEntity bobber) {
            FishingHookPredicate predicate = (FishingHookPredicate) (Object) this;
            cir.setReturnValue(
                    predicate.inOpenWater().isEmpty()
                            || predicate.inOpenWater().get() == bobber.isOpenWaterFishing()
            );
        }
    }
}
