package net.conczin.mca.mixin.client;

import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class MixinLivingEntityScale {
    @Inject(method = "getScale()F", at = @At("RETURN"), cancellable = true)
    private void mca$scalePlayerEntity(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof Player player && MCAClient.useGeneticsRenderer(player.getUUID())) {
            cir.setReturnValue(CommonVillagerModel.getVillager(player).getRawVerticalScaleFactor());
        }
    }
}
