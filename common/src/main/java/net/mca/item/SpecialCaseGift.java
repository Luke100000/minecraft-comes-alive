package net.mca.item;

import net.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;

public interface SpecialCaseGift {

    boolean handle(ServerPlayer player, VillagerEntityMCA villager);
}
