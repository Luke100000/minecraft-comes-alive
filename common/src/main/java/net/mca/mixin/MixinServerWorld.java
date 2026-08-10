package net.mca.mixin;

import net.mca.server.SpawnQueue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
abstract class MixinServerWorld extends Level implements WorldGenLevel {
    MixinServerWorld() { super(null, null, null, null, null, true, false, 0, 0);}

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

@Mixin(ProtoChunk.class)
abstract class MixinProtoChunk extends ChunkAccess {
    MixinProtoChunk() {super(null, null, null, null, 0, null, null);}

    @Inject(method = "addEntity(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void onAddEntity(Entity entity, CallbackInfo info) {
        if (SpawnQueue.getInstance().addVillager(entity)) {
            info.cancel();
        }
    }
}