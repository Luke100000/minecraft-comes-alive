package net.mca.item;

import net.mca.entity.VillagerEntityMCA;
import net.minecraft.server.network.ServerPlayerEntity;

public interface SpecialCaseGift {
    enum Result {
        PASS,
        HANDLED,
        CONSUME
    }

    Result handle(ServerPlayerEntity player, VillagerEntityMCA villager);
}
