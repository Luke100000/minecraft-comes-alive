package net.mca.mixin;

import net.mca.server.SpawnQueue;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
abstract class MixinServerWorld extends World implements StructureWorldAccess {
    MixinServerWorld() { super(null, null, null, null, null, true, false, 0, 0);}

    @Inject(method = "addEntity(Lnet/minecraft/entity/Entity;)Z",
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
abstract class MixinProtoChunk extends Chunk {
    MixinProtoChunk() {super(null, null, null, null, 0, null, null);}

    @Inject(method = "addEntity(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void onAddEntity(Entity entity, CallbackInfo info) {
        if (SpawnQueue.getInstance().addVillager(entity)) {
            info.cancel();
        }
    }
}