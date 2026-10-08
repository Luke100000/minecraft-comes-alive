package net.conczin.mca.entity.interaction;

import net.conczin.mca.entity.VillagerLike;
import net.minecraft.Util;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.OpenGuiRequest;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public abstract class EntityCommandHandler<T extends Entity & VillagerLike<?>> {
    protected final T entity;
    @Nullable
    protected Player interactingPlayer;
    private UUID interactionId = Util.NIL_UUID;

    public EntityCommandHandler(T entity) {
        this.entity = entity;
    }

    public Optional<Player> getInteractingPlayer() {
        return Optional.ofNullable(interactingPlayer).filter(player -> player.containerMenu != null);
    }

    public UUID interactionId() {
        return interactionId;
    }

    public boolean matchesInteraction(Player player, UUID id) {
        return !interactionId.equals(Util.NIL_UUID) && interactionId.equals(id)
                && getInteractingPlayer().filter(player::equals).isPresent();
    }

    public void stopInteracting() {
        if (!entity.level().isClientSide) {
            if (interactingPlayer instanceof ServerPlayer serverPlayer) {
                serverPlayer.closeContainer();
            }
        }
        interactingPlayer = null;
        interactionId = Util.NIL_UUID;
    }

    /** Stops only the named player's concrete interaction, leaving newer or foreign screens alone. */
    public void stopInteracting(Player player, UUID id) {
        if (matchesInteraction(player, id)) {
            stopInteracting();
        }
    }

    public InteractionResult interactAt(Player player, Vec3 pos, @NotNull InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            interactionId = UUID.randomUUID();
            Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.INTERACT, entity, interactionId), serverPlayer);
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
