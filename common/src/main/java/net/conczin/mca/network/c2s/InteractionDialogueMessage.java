package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.resources.Dialogues;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public record InteractionDialogueMessage(
        UUID villagerUUID,
        String question,
        String answer
) implements HandleablePayload {
    public static final CustomPacketPayload.Type<InteractionDialogueMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("interaction_dialogue"));
    public static final StreamCodec<FriendlyByteBuf, InteractionDialogueMessage> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, InteractionDialogueMessage::villagerUUID,
            ByteBufCodecs.STRING_UTF8, InteractionDialogueMessage::question,
            ByteBufCodecs.STRING_UTF8, InteractionDialogueMessage::answer,
            InteractionDialogueMessage::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        Entity v = player.level().getEntity(villagerUUID);
        if (v instanceof VillagerEntityMCA villager) {
            Dialogues.getInstance().selectAnswer(villager, player, question, answer);
        }
    }

    @Override
    public Type<InteractionDialogueMessage> type() {
        return TYPE;
    }
}
