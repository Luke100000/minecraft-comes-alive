package net.conczin.mca.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.conczin.mca.client.render.VillagerRenderState;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemInHandLayer.class)
public abstract class MixinItemInHandLayer<S extends ArmedEntityRenderState> {
    @ModifyReturnValue(method = "useBabyOffset", at = @At("RETURN"))
    private boolean mca$useBabyOffset(boolean original, S state) {
        return original && !(state instanceof VillagerRenderState);
    }

}
