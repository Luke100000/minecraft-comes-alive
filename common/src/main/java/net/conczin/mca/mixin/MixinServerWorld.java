package net.conczin.mca.mixin;

import net.conczin.mca.server.SpawnQueue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
abstract class MixinServerWorld {
    @Inject(method = "addEntity(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onAddEntity(Entity entity, CallbackInfoReturnable<Boolean> info) {
        if (SpawnQueue.getInstance().addVillager(entity)) {
            info.setReturnValue(false);
        }
    }
}
