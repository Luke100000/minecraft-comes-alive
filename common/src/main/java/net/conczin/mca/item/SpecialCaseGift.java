package net.conczin.mca.item;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;

public interface SpecialCaseGift {
    boolean handle(ServerPlayer player, VillagerEntityMCA villager);
}
