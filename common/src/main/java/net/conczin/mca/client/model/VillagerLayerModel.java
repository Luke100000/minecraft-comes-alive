package net.conczin.mca.client.model;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;

public interface VillagerLayerModel<T extends LivingEntity> {
    void copyFrom(PlayerModel<T> parent);
}
