package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.ClientHandlerImpl;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

public record InteractionDialogueSelectionRejectedResponse(long offerToken) implements HandleablePayload {
    public static final CustomPacketPayload.Type<InteractionDialogueSelectionRejectedResponse> TYPE =
            new CustomPacketPayload.Type<>(MCA.locate("interaction_dialogue_selection_rejected"));
    public static final StreamCodec<FriendlyByteBuf, InteractionDialogueSelectionRejectedResponse> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, InteractionDialogueSelectionRejectedResponse::offerToken,
                    InteractionDialogueSelectionRejectedResponse::new
            );

    @Override
    public void handle(Player player) {
        if (ClientProxy.getNetworkHandler() instanceof ClientHandlerImpl handler) {
            handler.handleDialogueSelectionRejectedResponse(this);
        }
    }

    @Override
    public Type<InteractionDialogueSelectionRejectedResponse> type() {
        return TYPE;
    }
}
