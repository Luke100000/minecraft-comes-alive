package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

public record InteractionDialogueQuestionResponse(Component questionText, boolean silent) implements HandleablePayload {
    public static final CustomPacketPayload.Type<InteractionDialogueQuestionResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("interaction_dialogue_question_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InteractionDialogueQuestionResponse> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, InteractionDialogueQuestionResponse::questionText,
            ByteBufCodecs.BOOL, InteractionDialogueQuestionResponse::silent,
            InteractionDialogueQuestionResponse::new
    );

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleDialogueQuestionResponse(this);
    }

    @Override
    public Type<InteractionDialogueQuestionResponse> type() {
        return TYPE;
    }
}
