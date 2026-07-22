package net.conczin.mca.item;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;

public interface SpecialCaseGift {
    enum Result {
        PASS,
        HANDLED,
        CONSUME
    }

    Result handle(ServerPlayer player, VillagerEntityMCA villager);
}
