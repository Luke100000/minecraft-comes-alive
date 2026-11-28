package net.conczin.mca.mixin.client;

import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractClientPlayer.class)
public abstract class MixinAbstractClientPlayer {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void mca$refreshDimensionsAfterConstruction(CallbackInfo ci) {
        ((AbstractClientPlayer) (Object) this).refreshDimensions();
    }
}
