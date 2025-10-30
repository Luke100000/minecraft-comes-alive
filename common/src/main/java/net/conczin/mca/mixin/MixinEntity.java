package net.conczin.mca.mixin;

import net.conczin.mca.Config;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class MixinEntity {
    @Inject(method = "getEyeHeight()F", at = @At("RETURN"), cancellable = true)
    private void onGetEyeHeight(CallbackInfoReturnable<Float> cir) {
        if ((Object)this instanceof Player plr && Config.getInstance().scaleEyeHeightWithPlayerHeight) {
            cir.setReturnValue(cir.getReturnValueF() * CommonVillagerModel.getVillager(plr).getRawVerticalScaleFactor());
        }
    }
}
