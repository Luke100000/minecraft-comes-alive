package net.conczin.mca.mixin;

import net.conczin.mca.ducks.IVillagerEntity;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.npc.villager.Villager;
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
    private transient EntitySpawnReason mca$reason;

    @Override
    public EntitySpawnReason mca$getSpawnReason() {
        return mca$reason == null ? EntitySpawnReason.NATURAL : mca$reason;
    }

    @Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void mca$injectFinalizeSpawn(
            ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnType, SpawnGroupData spawnGroupData, CallbackInfoReturnable<SpawnGroupData> cir
    ) {
        mca$reason = spawnType;
    }
}
