package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

public record ChatAIContextResponse(boolean hasVillager, String villagerName, String villagerPrompt,
                                    String playerName, String playerPrompt, boolean hasVillage,
                                    String villageName, String villagePrompt,
                                    String worldPrompt) implements HandleablePayload {
    public static final CustomPacketPayload.Type<ChatAIContextResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("chat_ai_context_response"));
    public static final StreamCodec<FriendlyByteBuf, ChatAIContextResponse> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ChatAIContextResponse decode(FriendlyByteBuf buffer) {
            return new ChatAIContextResponse(
                    buffer.readBoolean(), buffer.readUtf(), buffer.readUtf(),
                    buffer.readUtf(), buffer.readUtf(), buffer.readBoolean(),
                    buffer.readUtf(), buffer.readUtf(), buffer.readUtf()
            );
        }

        @Override
        public void encode(FriendlyByteBuf buffer, ChatAIContextResponse context) {
            buffer.writeBoolean(context.hasVillager());
            buffer.writeUtf(context.villagerName());
            buffer.writeUtf(context.villagerPrompt());
            buffer.writeUtf(context.playerName());
            buffer.writeUtf(context.playerPrompt());
            buffer.writeBoolean(context.hasVillage());
            buffer.writeUtf(context.villageName());
            buffer.writeUtf(context.villagePrompt());
            buffer.writeUtf(context.worldPrompt());
        }
    };

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleChatAIContextResponse(this);
    }

    @Override
    public CustomPacketPayload.Type<ChatAIContextResponse> type() {
        return TYPE;
    }
}
