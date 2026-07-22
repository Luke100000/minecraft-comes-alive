package net.mca.item;

import net.mca.entity.VillagerEntityMCA;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

public interface SpecialCaseGift {
    ActionResult handle(ServerPlayerEntity player, VillagerEntityMCA villager);
}
