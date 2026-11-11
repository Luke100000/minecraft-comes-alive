package net.conczin.mca.network.s2c;

import io.netty.buffer.ByteBuf;
import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;
import java.util.UUID;

/** Server-confirmed asynchronous lookup; a matching active node response ends this state. */
public record InteractionDialoguePendingResponse(UUID sessionId, long offerToken, Component pendingLine)
        implements HandleablePayload {
    public static final CustomPacketPayload.Type<InteractionDialoguePendingResponse> TYPE =
            new CustomPacketPayload.Type<>(MCA.locate("interaction_dialogue_pending"));
    private static final StreamCodec<ByteBuf, Component> LINE_CODEC =
            ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC;
    public static final StreamCodec<FriendlyByteBuf, InteractionDialoguePendingResponse> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, InteractionDialoguePendingResponse::sessionId,
            ByteBufCodecs.VAR_LONG, InteractionDialoguePendingResponse::offerToken,
            LINE_CODEC, InteractionDialoguePendingResponse::pendingLine,
            InteractionDialoguePendingResponse::new
    );

    public InteractionDialoguePendingResponse {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(pendingLine, "pendingLine");
        if (pendingLine.getString().length() > 2048) {
            throw new IllegalArgumentException("Dialogue pending line is too long");
        }
    }

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleDialoguePendingResponse(this);
    }

    @Override
    public Type<InteractionDialoguePendingResponse> type() {
        return TYPE;
    }
}
