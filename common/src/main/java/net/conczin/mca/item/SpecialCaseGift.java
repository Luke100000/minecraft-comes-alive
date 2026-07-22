package net.conczin.mca.item;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

public interface SpecialCaseGift {
    InteractionResult handle(ServerPlayer player, VillagerEntityMCA villager);
}
