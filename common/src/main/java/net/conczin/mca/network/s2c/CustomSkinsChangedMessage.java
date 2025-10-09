package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

public record CustomSkinsChangedMessage() implements HandleablePayload {
    public static final CustomPacketPayload.Type<CustomSkinsChangedMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("custom_skins_changed"));
    public static final StreamCodec<FriendlyByteBuf, CustomSkinsChangedMessage> STREAM_CODEC = StreamCodec.unit(new CustomSkinsChangedMessage());

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleCustomSkinsChangedMessage(this);
    }

    @Override
    public CustomPacketPayload.Type<CustomSkinsChangedMessage> type() {
        return TYPE;
    }
}
