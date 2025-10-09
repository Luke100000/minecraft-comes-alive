package net.mca.mixin;

import net.mca.ducks.IVillagerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
abstract class MixinVillager implements IVillagerEntity {
    @Unique
    @Nullable
    private transient MobSpawnType mca$reason;

    @Override
    public MobSpawnType mca$getSpawnReason() {
        return mca$reason == null ? MobSpawnType.NATURAL : mca$reason;
    }

    @Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void mca$injectFinalizeSpawn(
            ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason,
            @Nullable SpawnGroupData entityData,
            @Nullable CompoundTag entityNbt, CallbackInfoReturnable<SpawnGroupData> info
    ) {
        mca$reason = spawnReason;
    }
}
