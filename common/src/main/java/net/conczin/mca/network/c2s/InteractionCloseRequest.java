package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public record InteractionCloseRequest(UUID villagerUUID, UUID interactionId, UUID sessionId, long offerToken) implements HandleablePayload {
    public static final CustomPacketPayload.Type<InteractionCloseRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("interaction_close_request"));
    public static final StreamCodec<FriendlyByteBuf, InteractionCloseRequest> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, InteractionCloseRequest::villagerUUID,
            UUIDUtil.STREAM_CODEC, InteractionCloseRequest::interactionId,
            UUIDUtil.STREAM_CODEC, InteractionCloseRequest::sessionId,
            ByteBufCodecs.VAR_LONG, InteractionCloseRequest::offerToken,
            InteractionCloseRequest::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        Entity v = player.serverLevel().getEntity(villagerUUID);
        if (!(v instanceof VillagerEntityMCA villager)) {
            return;
        }
        boolean ownsInteraction = villager.getInteractions().matchesInteraction(player, interactionId);
        // Another player can replace the screen owner while this player's dialogue
        // stays active. Only then may its own session/token authorize a pause
        // without the former screen ID; it must never close the new owner's screen.
        if (!ownsInteraction && villager.getInteractions().getInteractingPlayer()
                .filter(current -> !current.equals(player)).isEmpty()) {
            return;
        }
        boolean current = MCA.getDialogueEngine()
                .map(engine -> engine.pause(player, villagerUUID, sessionId, offerToken))
                .orElse(ownsInteraction);
        if (ownsInteraction && current) {
            villager.getInteractions().stopInteracting(player, interactionId);
        }
    }

    @Override
    public CustomPacketPayload.Type<InteractionCloseRequest> type() {
        return TYPE;
    }
}
