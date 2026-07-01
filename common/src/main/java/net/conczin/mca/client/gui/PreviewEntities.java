package net.conczin.mca.client.gui;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.registry.EntitiesMCA;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntitySpawnReason;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

final class PreviewEntities {
    // Preview entities never enter the world, so keep their ids in the negative range.
    private static final AtomicInteger NEXT_ID = new AtomicInteger(-1);

    private PreviewEntities() {
    }

    static VillagerEntityMCA villager() {
        VillagerEntityMCA villager = Objects.requireNonNull(
                EntitiesMCA.MALE_VILLAGER.create(
                        Objects.requireNonNull(Minecraft.getInstance().level),
                        EntitySpawnReason.LOAD
                )
        );
        villager.setId(NEXT_ID.getAndDecrement());
        return villager;
    }
}
