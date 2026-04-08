package net.conczin.mca.mixin;

import net.conczin.mca.server.SpawnQueue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProtoChunk.class)
abstract class MixinProtoChunk {
    @Inject(method = "addEntity(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void onAddEntity(Entity entity, CallbackInfo info) {
        if (SpawnQueue.getInstance().addVillager(entity)) {
            info.cancel();
        }
    }
}
