package net.conczin.mca.entity.interaction;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.ActivitiesMCA;
import net.conczin.mca.entity.ai.Chore;
import net.minecraft.Util;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.OpenGuiRequest;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
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
        return Optional.ofNullable(interactingPlayer);
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
                if (entity instanceof VillagerEntityMCA villager) {
                    MCA.getDialogueEngine().ifPresent(engine ->
                            engine.onInteractionReleased(serverPlayer, villager.getUUID()));
                }
                serverPlayer.closeContainer();
            }
        }
        interactingPlayer = null;
        interactionId = Util.NIL_UUID;
        if (entity instanceof VillagerEntityMCA villager && villager.getBrain().isActive(ActivitiesMCA.INTERACT)) {
            Activity resume = villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                    ? Activity.IDLE
                    : villager.getVillagerBrain().getCurrentJob() == Chore.NONE
                    ? villager.getBrain().getSchedule().getActivityAt((int) (villager.level().getDayTime() % 24000L))
                    : ActivitiesMCA.CHORE;
            villager.getMCABrain().setActiveActivityIfPossible(resume);
        }
    }

    /** Stops only the named player's concrete interaction, leaving newer or foreign screens alone. */
    public void stopInteracting(Player player, UUID id) {
        if (matchesInteraction(player, id)) {
            stopInteracting();
        }
    }

    public InteractionResult interactAt(Player player, Vec3 pos, @NotNull InteractionHand hand) {
        if (entity instanceof VillagerEntityMCA villager && (villager.isConversationDangerous() || !villager.isAlive())) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (interactingPlayer instanceof ServerPlayer priorPlayer) {
                // Superseding the screen must close only the previous interaction ID.
                UUID oldId = interactionId;
                MCA.getDialogueEngine().ifPresent(engine -> engine.pause(priorPlayer));
                stopInteracting(priorPlayer, oldId);
                Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.CLOSE, entity, oldId), priorPlayer);
            }
            if (entity instanceof VillagerEntityMCA) {
                // A player can have only one MCA conversation screen at a time.
                serverPlayer.serverLevel().getEntitiesOfClass(VillagerEntityMCA.class,
                        serverPlayer.getBoundingBox().inflate(6.0D), other -> other != entity)
                        .forEach(other -> {
                            if (other.getInteractions().getInteractingPlayer()
                                    .filter(serverPlayer::equals).isPresent()) {
                                UUID previousId = other.getInteractions().interactionId();
                                MCA.getDialogueEngine().ifPresent(engine -> engine.pause(serverPlayer));
                                other.getInteractions().stopInteracting(serverPlayer, previousId);
                                Network.sendToPlayer(
                                        new OpenGuiRequest(OpenGuiRequest.Type.CLOSE, other, previousId), serverPlayer);
                            }
                        });
            }
            if (entity instanceof VillagerEntityMCA villager) {
                if (villager.isSleeping()) {
                    villager.stopSleeping();
                }
            }
            interactionId = UUID.randomUUID();
            interactingPlayer = player;
            if (entity instanceof VillagerEntityMCA villager) {
                villager.getMCABrain().setActiveActivityIfPossible(ActivitiesMCA.INTERACT);
            }
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
