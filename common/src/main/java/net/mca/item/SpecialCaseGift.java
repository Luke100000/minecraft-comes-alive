package net.mca.item;

import net.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

public interface SpecialCaseGift {
    InteractionResult handle(ServerPlayer player, VillagerEntityMCA villager);
}
