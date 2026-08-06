package net.conczin.mca.mixin;

import net.conczin.mca.datafix.McaDataFixers;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Upgrades the villager-shaped payload stored in MCA's per-player SavedData as
 * soon as that SavedData is loaded. The normal SavedData save then persists the
 * canonical representation once.
 */
@Mixin(PlayerSaveData.class)
public abstract class MixinPlayerSaveData {
    @Shadow
    private CompoundTag entityData;

    @Inject(
            method = "<init>(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("TAIL")
    )
    private void migrateEntityData(ServerLevel world, UUID uuid, CompoundTag nbt, CallbackInfo callback) {
        CompoundTag migrated = McaDataFixers.update(entityData);
        if (!migrated.equals(entityData)) {
            entityData = migrated;
            ((PlayerSaveData) (Object) this).setDirty();
        }
    }
}
