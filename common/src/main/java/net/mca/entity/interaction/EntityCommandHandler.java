package net.mca.entity.interaction;

import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerLike;
import net.mca.network.s2c.OpenGuiRequest;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public abstract class EntityCommandHandler<T extends Entity & VillagerLike<?>> {
    @Nullable
    protected Player interactingPlayer;

    protected final T entity;

    public EntityCommandHandler(T entity) {
        this.entity = entity;
    }

    public Optional<Player> getInteractingPlayer() {
        return Optional.ofNullable(interactingPlayer).filter(player -> player.containerMenu != null);
    }

    public void stopInteracting() {
        if (!entity.level().isClientSide) {
            if (interactingPlayer instanceof ServerPlayer serverPlayer) {
                serverPlayer.closeContainer();
            }
        }
        interactingPlayer = null;
    }

    public InteractionResult interactAt(Player player, Vec3 pos, @NotNull InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.INTERACT, entity), serverPlayer);
        }
        interactingPlayer = player;
        return InteractionResult.SUCCESS;
    }

    /**
     * Called on the server to respond to button events.
     */
    public boolean handle(ServerPlayer player, String command) {
        return false;
    }
}
