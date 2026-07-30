package net.conczin.mca.mixin.client;

import net.conczin.mca.client.gui.PreviewEntityAnimation;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DeltaTracker.Timer.class)
public class MixinDeltaTrackerTimer {
    @Inject(method = "getGameTimeDeltaTicks", at = @At("HEAD"), cancellable = true)
    private void mca$initializePreviewAnimationState(CallbackInfoReturnable<Float> cir) {
        Float previewDeltaTicks = PreviewEntityAnimation.getActiveGameTimeDeltaTicks();
        if (previewDeltaTicks != null) {
            cir.setReturnValue(previewDeltaTicks);
        }
    }

    @Inject(method = "getGameTimeDeltaPartialTick", at = @At("HEAD"), cancellable = true)
    private void mca$usePreviewPartialTick(boolean usePausedPartialTick, CallbackInfoReturnable<Float> cir) {
        Float previewPartialTick = PreviewEntityAnimation.getActivePartialTick();
        if (previewPartialTick != null) {
            cir.setReturnValue(previewPartialTick);
        }
    }
}
