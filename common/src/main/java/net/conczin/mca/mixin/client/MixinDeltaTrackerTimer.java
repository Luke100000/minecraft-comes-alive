package net.conczin.mca.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.conczin.mca.client.gui.PreviewEntityAnimation;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DeltaTracker.Timer.class)
public class MixinDeltaTrackerTimer {
    @ModifyReturnValue(method = "getGameTimeDeltaPartialTick", at = @At("RETURN"))
    private float mca$usePreviewPartialTick(float original, boolean usePausedPartialTick) {
        Float previewPartialTick = PreviewEntityAnimation.getActivePartialTick();
        return previewPartialTick == null ? original : previewPartialTick;
    }
}
