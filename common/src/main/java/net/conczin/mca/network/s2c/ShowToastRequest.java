package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

public record ShowToastRequest(String title, String message) implements HandleablePayload {
    public static final CustomPacketPayload.Type<ShowToastRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("show_toast_request"));
    public static final StreamCodec<FriendlyByteBuf, ShowToastRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ShowToastRequest::title,
            ByteBufCodecs.STRING_UTF8, ShowToastRequest::message,
            ShowToastRequest::new
    );

    public Component getTitle() {
        return Component.translatable(title);
    }

    public Component getMessage() {
        return Component.translatable(message);
    }

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleToastMessage(this);
    }

    @Override
    public CustomPacketPayload.Type<ShowToastRequest> type() {
        return TYPE;
    }
}
