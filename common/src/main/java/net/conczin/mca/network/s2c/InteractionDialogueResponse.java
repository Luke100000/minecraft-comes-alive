package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.resources.data.dialogue.Question;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public record InteractionDialogueResponse(String question, List<String> answers) implements HandleablePayload {
    public static final CustomPacketPayload.Type<InteractionDialogueResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("interaction_dialogue_response"));
    public static final StreamCodec<FriendlyByteBuf, InteractionDialogueResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, InteractionDialogueResponse::question,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), InteractionDialogueResponse::answers,
            InteractionDialogueResponse::new
    );

    public InteractionDialogueResponse(Question question, ServerPlayer player, VillagerEntityMCA villager) {
        this(question.getName(), question.getValidAnswers(player, villager));
    }

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleDialogueResponse(this);
    }

    @Override
    public Type<InteractionDialogueResponse> type() {
        return TYPE;
    }
}
