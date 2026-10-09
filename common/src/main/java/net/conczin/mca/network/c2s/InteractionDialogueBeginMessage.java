package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.InteractionDialogueOptionsResponse;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Objects;
import java.util.UUID;

public record InteractionDialogueBeginMessage(UUID villagerUUID, boolean showOpening) implements HandleablePayload {
    public static final CustomPacketPayload.Type<InteractionDialogueBeginMessage> TYPE =
            new CustomPacketPayload.Type<>(MCA.locate("interaction_dialogue_begin"));
    public static final StreamCodec<FriendlyByteBuf, InteractionDialogueBeginMessage> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, InteractionDialogueBeginMessage::villagerUUID,
            ByteBufCodecs.BOOL, InteractionDialogueBeginMessage::showOpening,
            InteractionDialogueBeginMessage::new
    );

    public InteractionDialogueBeginMessage(UUID villagerUUID) {
        this(villagerUUID, true);
    }

    public InteractionDialogueBeginMessage {
        Objects.requireNonNull(villagerUUID, "villagerUUID");
    }

    @Override
    public void handleServer(ServerPlayer player) {
        Entity entity = player.serverLevel().getEntity(villagerUUID);
        if (!(entity instanceof VillagerEntityMCA villager)
                || villager.getInteractions().getInteractingPlayer().filter(player::equals).isEmpty()) {
            return;
        }

        MCA.getDialogueEngine().ifPresent(engine -> Network.sendToPlayer(
                InteractionDialogueOptionsResponse.from(engine.begin(player, villager, showOpening)),
                player
        ));
    }

    @Override
    public Type<InteractionDialogueBeginMessage> type() {
        return TYPE;
    }
}
