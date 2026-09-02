package net.conczin.mca.entity.ai.chatAI;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Serializes requests for one villager while keeping Minecraft-owned preparation and application
 * on the server executor. Strategy instances are scoped per villager by {@link ChatAI}.
 */
abstract class AbstractChatAIStrategy implements ChatAIStrategy {
    private CompletableFuture<Void> requestChain = CompletableFuture.completedFuture(null);

    @Override
    public final synchronized CompletableFuture<Optional<String>> answerAsync(ServerPlayer player, VillagerEntityMCA villager, String msg) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        CompletableFuture<Optional<String>> currentRequest = requestChain
                .handle((ignored, throwable) -> null)
                .thenComposeAsync(ignored -> {
                    if (!isConversationValid(player, villager)) {
                        return CompletableFuture.completedFuture(Optional.empty());
                    }
                    return requestAndApply(player, villager, msg, server);
                }, server);
        requestChain = currentRequest.handle((ignored, throwable) -> null);
        return currentRequest;
    }

    protected final boolean isConversationValid(ServerPlayer player, VillagerEntityMCA villager) {
        return !player.isRemoved()
                && !villager.isRemoved()
                && player.level() == villager.level();
    }

    protected abstract CompletableFuture<Optional<String>> requestAndApply(
            ServerPlayer player,
            VillagerEntityMCA villager,
            String msg,
            MinecraftServer server
    );
}
