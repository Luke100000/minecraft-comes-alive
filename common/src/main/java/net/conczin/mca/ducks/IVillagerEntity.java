package net.conczin.mca.ducks;

import net.minecraft.world.entity.MobSpawnType;

public interface IVillagerEntity {
    MobSpawnType getSpawnReason();
}
