package net.conczin.mca.mixin.client.journeymap;

import net.conczin.mca.client.render.JourneyMapIconBridge;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "journeymap.client.model.entity.EntityDTO", remap = false)
public abstract class MixinEntityDTO {
    @Shadow(remap = false)
    public abstract void setEntityIconLocation(ResourceLocation entityIconLocation);

    @Shadow(remap = false)
    public abstract void setDrawOutline(boolean drawOutline);

    @Inject(
            method = "update(Lnet/minecraft/world/entity/LivingEntity;Z)V",
            at = @At("TAIL"),
            remap = false
    )
    private void mca$useDynamicFaceIcon(LivingEntity entity, boolean ignored, CallbackInfo ci) {
        if (!(entity instanceof VillagerLike<?> villager)) {
            return;
        }

        ResourceLocation icon = JourneyMapIconBridge.getOrCreateFaceIcon(villager);
        if (icon != null) {
            this.setDrawOutline(false);
            this.setEntityIconLocation(icon);
        }
    }
}
