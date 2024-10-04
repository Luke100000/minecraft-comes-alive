package net.mca.entity.ai.chatAI;

import net.mca.entity.VillagerEntityMCA;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Optional;

/**
 * Interface for AI Strategies
 */
public interface ChatAIStrategy {
    Optional<String> answer(ServerPlayerEntity player, VillagerEntityMCA villager, String msg);
}
