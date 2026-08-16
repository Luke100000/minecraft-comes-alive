package net.conczin.mca.entity.ai.chatAI;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for AI Strategies
 */
public interface ChatAIStrategy {
    /**
     * Starts an answer request from the server thread. Implementations may perform remote I/O
     * asynchronously, but Minecraft state must only be read or mutated on the server thread.
     */
    CompletableFuture<Optional<String>> answerAsync(ServerPlayer player, VillagerEntityMCA villager, String msg);
}
