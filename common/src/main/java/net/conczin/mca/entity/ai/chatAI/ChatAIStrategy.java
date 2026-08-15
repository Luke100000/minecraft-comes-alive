package net.conczin.mca.entity.ai.chatAI;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;
import java.util.Optional;

/**
 * Interface for AI Strategies
 */
public interface ChatAIStrategy {
    Optional<String> answer(ServerPlayer player, VillagerEntityMCA villager, String msg);
}
